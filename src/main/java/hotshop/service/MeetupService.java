package hotshop.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import hotshop.database.Database;
import hotshop.model.Meetup;
import hotshop.model.MeetupSlot;
import hotshop.model.MeetupStatus;
import hotshop.model.MeetupTime;
import hotshop.model.RescheduleProposal;
import hotshop.model.Transaction;
import hotshop.model.TransactionStatus;
import hotshop.repository.MeetupRepository;
import hotshop.repository.TransactionRepository;

/**
 * Planning the handover of an active sale: the seller offers up to three slots to the sale's buyer,
 * the buyer books one, and either participant can later propose a move or cancel the meetup.
 * Serialized with all other application services; every operation requires login and acts as the
 * session's current user. Only the sale's buyer and seller can see or change its meetup.
 */
public final class MeetupService {
    private static final int MAX_OFFERED_SLOTS = 3;
    private static final Duration MAX_DAYS_AHEAD = Duration.ofDays(60);
    private static final String STORAGE_FAILURE = "Meetups are unavailable right now. Please try again.";
    private final Database database;
    private final MeetupRepository meetups;
    private final TransactionRepository transactions;
    private final ServiceWorker worker;
    private final AuthenticatedSession session;
    private final Clock clock;

    /** Wires the shared database, worker, and session with the repositories meetups touch. */
    public MeetupService(Database database, MeetupRepository meetups, TransactionRepository transactions,
            ServiceWorker worker, AuthenticatedSession session, Clock clock) {
        this.database = database;
        this.meetups = meetups;
        this.transactions = transactions;
        this.worker = worker;
        this.session = session;
        this.clock = clock;
    }

    /**
     * The seller offers one time and place for the sale's handover. At most three future slots per
     * sale, none overlapping each other or the seller's own scheduled meetups.
     */
    public CompletableFuture<MeetupSummary> offerSlot(UUID saleId, Instant start, Instant end, String location) {
        return worker.submit(() -> {
            UUID userId = session.requireUserId();
            requireId(saleId, "Choose a sale first.");
            Instant now = ServiceSupport.now(clock);
            MeetupTime time = toFutureTime(start, end, location, now);
            return transaction(connection -> {
                Transaction sale = requireActiveSale(connection, saleId);
                if (!sale.getSellerId().equals(userId)) {
                    throw ServiceException.permission("Only the seller can offer meetup times.");
                }
                requireNoScheduledMeetup(connection, saleId);
                List<MeetupSlot> offered = SaleMeetups.futureSlots(connection, meetups, saleId, now);
                if (offered.size() >= MAX_OFFERED_SLOTS) {
                    throw ServiceException.validation("You can offer at most " + MAX_OFFERED_SLOTS
                            + " meetup times for a sale. Withdraw one before offering another.");
                }
                if (offered.stream().anyMatch(slot -> slot.time().overlaps(time))) {
                    throw ServiceException.invalidState("This time overlaps another time you offered for this sale.");
                }
                requireFree(meetups.findScheduledForParticipant(connection, userId), time, "You already have");
                meetups.insertSlot(connection, MeetupSlot.offer(saleId, time, now));
                return SaleMeetups.load(connection, meetups, saleId, now);
            });
        });
    }

    /** The seller takes back a slot nobody has booked. */
    public CompletableFuture<MeetupSummary> withdrawSlot(UUID slotId) {
        return worker.submit(() -> {
            UUID userId = session.requireUserId();
            requireId(slotId, "Choose a meetup time first.");
            return transaction(connection -> {
                MeetupSlot slot = requireSlot(connection, slotId);
                Transaction sale = requireSale(connection, slot.transactionId());
                if (!sale.getSellerId().equals(userId)) {
                    throw ServiceException.permission("Only the seller can withdraw meetup times they offered.");
                }
                meetups.deleteSlot(connection, slotId);
                return SaleMeetups.load(connection, meetups, sale.getId(), ServiceSupport.now(clock));
            });
        });
    }

    /**
     * The buyer books one offered slot; the sale's other slots are then deleted. Neither participant
     * may have another scheduled meetup at an overlapping time, in any role.
     */
    public CompletableFuture<MeetupSummary> bookSlot(UUID slotId) {
        return worker.submit(() -> {
            UUID userId = session.requireUserId();
            requireId(slotId, "Choose a meetup time first.");
            Instant now = ServiceSupport.now(clock);
            return transaction(connection -> {
                MeetupSlot slot = requireSlot(connection, slotId);
                Transaction sale = requireActiveSale(connection, slot.transactionId());
                if (!sale.getBuyerId().equals(userId)) {
                    throw ServiceException.permission("Only the buyer can choose a meetup time.");
                }
                requireNoScheduledMeetup(connection, sale.getId());
                if (!slot.time().startAt().isAfter(now)) {
                    throw ServiceException.invalidState(
                            "This time has already passed. Ask the seller to offer new times.");
                }
                requireFree(meetups.findScheduledForParticipant(connection, sale.getBuyerId()), slot.time(),
                        "You already have");
                requireFree(meetups.findScheduledForParticipant(connection, sale.getSellerId()), slot.time(),
                        "The seller already has");
                meetups.insertMeetup(connection, Meetup.book(slot, sale.getBuyerId(), sale.getSellerId(), now));
                meetups.deleteSlotsForSale(connection, sale.getId());
                return SaleMeetups.load(connection, meetups, sale.getId(), now);
            });
        });
    }

    /** Either participant proposes moving the meetup to one new time and place. */
    public CompletableFuture<MeetupSummary> proposeMove(UUID meetupId, Instant start, Instant end, String location) {
        return changeMeetup(meetupId, (connection, meetup, userId, now) -> {
            MeetupTime time = toFutureTime(start, end, location, now);
            if (meetup.getPendingProposal().isPresent()) {
                throw ServiceException.invalidState("There is already a pending proposal to move this meetup.");
            }
            requireBothFree(connection, meetup, time);
            meetup.proposeMove(userId, time, eventTime(meetup, now));
        });
    }

    /** The other participant accepts the pending move, after both participants are rechecked for clashes. */
    public CompletableFuture<MeetupSummary> acceptMove(UUID meetupId) {
        return changeMeetup(meetupId, (connection, meetup, userId, now) -> {
            var proposal = requireResponder(meetup, userId);
            requireBothFree(connection, meetup, proposal.getTime());
            meetup.acceptMove(proposal.getId(), userId, eventTime(meetup, now));
        });
    }

    /** The other participant declines the pending move; the meetup keeps its time. */
    public CompletableFuture<MeetupSummary> rejectMove(UUID meetupId) {
        return changeMeetup(meetupId, (connection, meetup, userId, now) ->
                meetup.rejectMove(requireResponder(meetup, userId).getId(), userId, eventTime(meetup, now)));
    }

    /** The proposer takes back their pending move. */
    public CompletableFuture<MeetupSummary> withdrawMove(UUID meetupId) {
        return changeMeetup(meetupId, (connection, meetup, userId, now) -> {
            var proposal = requirePendingProposal(meetup);
            if (!proposal.getProposerId().equals(userId)) {
                throw ServiceException.permission("Only the participant who proposed the move can withdraw it.");
            }
            meetup.withdrawMove(proposal.getId(), userId, eventTime(meetup, now));
        });
    }

    /** Either participant cancels the meetup; the sale stays active and the seller can offer new times. */
    public CompletableFuture<MeetupSummary> cancelMeetup(UUID meetupId) {
        return changeMeetup(meetupId, (connection, meetup, userId, now) -> meetup.cancel(eventTime(meetup, now)));
    }

    /** The sale's offered slots and meetup, for either participant. */
    public CompletableFuture<MeetupSummary> getMeetupSummary(UUID saleId) {
        return worker.submit(() -> {
            UUID userId = session.requireUserId();
            requireId(saleId, "Choose a sale first.");
            return transaction(connection -> {
                requireParticipant(requireSale(connection, saleId), userId);
                return SaleMeetups.load(connection, meetups, saleId, ServiceSupport.now(clock));
            });
        });
    }

    /** A change to one scheduled meetup, applied inside the operation's database transaction. */
    @FunctionalInterface
    private interface MeetupChange {
        void apply(Connection connection, Meetup meetup, UUID userId, Instant now) throws SQLException;
    }

    /** Loads the meetup, checks the current user takes part and that it is scheduled, applies and saves the change. */
    private CompletableFuture<MeetupSummary> changeMeetup(UUID meetupId, MeetupChange change) {
        return worker.submit(() -> {
            UUID userId = session.requireUserId();
            requireId(meetupId, "Choose a meetup first.");
            Instant now = ServiceSupport.now(clock);
            return transaction(connection -> {
                Meetup meetup = meetups.findMeetup(connection, meetupId)
                        .orElseThrow(() -> ServiceException.notFound("This meetup no longer exists."));
                if (!userId.equals(meetup.getBuyerId()) && !userId.equals(meetup.getSellerId())) {
                    throw ServiceException.permission("Only the buyer and seller of this sale can change its meetup.");
                }
                if (meetup.getStatus() != MeetupStatus.SCHEDULED) {
                    throw ServiceException.invalidState("This meetup is already "
                            + ServiceSupport.describe(meetup.getStatus()) + ", so it can't be changed.");
                }
                change.apply(connection, meetup, userId, now);
                meetups.updateMeetup(connection, meetup);
                return SaleMeetups.load(connection, meetups, meetup.getTransactionId(), now);
            });
        });
    }

    /** Neither participant may have another scheduled meetup at the new time; the meetup itself is ignored. */
    private void requireBothFree(Connection connection, Meetup meetup, MeetupTime time) throws SQLException {
        requireFree(othersThan(meetup, meetups.findScheduledForParticipant(connection, meetup.getBuyerId())), time,
                "The buyer already has");
        requireFree(othersThan(meetup, meetups.findScheduledForParticipant(connection, meetup.getSellerId())), time,
                "The seller already has");
    }

    private static List<Meetup> othersThan(Meetup meetup, List<Meetup> scheduled) {
        return scheduled.stream().filter(other -> !other.getId().equals(meetup.getId())).toList();
    }

    private static RescheduleProposal requirePendingProposal(Meetup meetup) {
        return meetup.getPendingProposal().orElseThrow(() ->
                ServiceException.invalidState("There is no pending proposal to move this meetup."));
    }

    /** Returns the pending proposal after checking the current user is the one who must respond. */
    private static RescheduleProposal requireResponder(Meetup meetup, UUID userId) {
        RescheduleProposal proposal = requirePendingProposal(meetup);
        if (proposal.getProposerId().equals(userId)) {
            throw ServiceException.permission("Only the other participant can respond to your proposal. "
                    + "You can withdraw it instead.");
        }
        return proposal;
    }

    /** Event times never precede the meetup's last event, even if the system clock moved backwards. */
    private static Instant eventTime(Meetup meetup, Instant now) {
        return ServiceSupport.latest(now, meetup.getLastEventAt());
    }

    /** Validates length and place, then that the time starts in the future and within sixty days. */
    private static MeetupTime toFutureTime(Instant start, Instant end, String location, Instant now) {
        if (start == null || end == null || location == null) {
            throw ServiceException.validation("Choose a start time, end time, and location.");
        }
        MeetupTime time;
        try {
            time = new MeetupTime(start, end, location);
        } catch (IllegalArgumentException exception) {
            throw ServiceException.validation(exception.getMessage() + ".");
        }
        if (!time.startAt().isAfter(now)) {
            throw ServiceException.validation("Meetup times must start in the future.");
        }
        if (time.startAt().isAfter(now.plus(MAX_DAYS_AHEAD))) {
            throw ServiceException.validation(
                    "Meetup times can be at most " + MAX_DAYS_AHEAD.toDays() + " days ahead.");
        }
        return time;
    }

    /** Refuses a time that overlaps one participant's scheduled meetups; alreadyBusy begins the message. */
    private static void requireFree(List<Meetup> scheduled, MeetupTime time, String alreadyBusy) {
        for (Meetup other : scheduled) {
            if (other.getTime().overlaps(time)) {
                throw ServiceException.invalidState(alreadyBusy + " a meetup from "
                        + ServiceSupport.formatTime(other.getTime().startAt()) + " to "
                        + ServiceSupport.formatTime(other.getTime().endAt()) + ". Choose a different time.");
            }
        }
    }

    private void requireNoScheduledMeetup(Connection connection, UUID saleId) throws SQLException {
        if (meetups.findCurrentForSale(connection, saleId)
                .filter(meetup -> meetup.getStatus() == MeetupStatus.SCHEDULED).isPresent()) {
            throw ServiceException.invalidState("This sale already has a meetup booked. Propose a move instead.");
        }
    }

    private MeetupSlot requireSlot(Connection connection, UUID slotId) throws SQLException {
        return meetups.findSlot(connection, slotId)
                .orElseThrow(() -> ServiceException.notFound("This meetup time is no longer offered."));
    }

    private Transaction requireSale(Connection connection, UUID saleId) throws SQLException {
        return transactions.findById(connection, saleId)
                .orElseThrow(() -> ServiceException.notFound("This sale no longer exists."));
    }

    private Transaction requireActiveSale(Connection connection, UUID saleId) throws SQLException {
        Transaction sale = requireSale(connection, saleId);
        if (sale.getStatus() != TransactionStatus.ACTIVE) {
            throw ServiceException.invalidState("This sale is already " + ServiceSupport.describe(sale.getStatus())
                    + ", so its meetup can't be changed.");
        }
        return sale;
    }

    private static void requireParticipant(Transaction sale, UUID userId) {
        if (!userId.equals(sale.getBuyerId()) && !userId.equals(sale.getSellerId())) {
            throw ServiceException.permission("Only the buyer and seller of this sale can see its meetup.");
        }
    }

    private static void requireId(UUID id, String message) {
        if (id == null) {
            throw ServiceException.validation(message);
        }
    }

    private <T> T transaction(Database.Work<T> work) {
        return ServiceSupport.transaction(database, STORAGE_FAILURE, work);
    }
}
