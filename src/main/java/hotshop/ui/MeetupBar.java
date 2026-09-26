package hotshop.ui;

import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import hotshop.model.Meetup;
import hotshop.model.MeetupStatus;
import hotshop.model.MeetupTime;
import hotshop.model.RescheduleProposal;
import hotshop.model.TransactionStatus;
import hotshop.service.MeetupService;
import hotshop.service.MeetupSummary;
import hotshop.service.SaleRole;

/**
 * What the conversation's bar shows one participant once the two have a sale: the meetup's state
 * and the meetup actions that apply. MeetupService still decides whether each action is allowed.
 */
record MeetupBar(String text, List<MeetupBar.Action> actions) {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    /** Meetup actions the bar can show, plus the link to the sale. */
    enum Action {
        OFFER_TIME, VIEW_TIMES, CHOOSE_TIME, PROPOSE_MOVE, CANCEL_MEETUP, ACCEPT_MOVE, REJECT_MOVE, WITHDRAW_MOVE,
        VIEW_SALE
    }

    /** The conversation's one bar shows the latest stage: an active or completed sale replaces the offer bar. */
    static boolean replacesOfferBar(Optional<TransactionStatus> saleStatus) {
        return saleStatus.filter(status -> status != TransactionStatus.CANCELLED).isPresent();
    }

    static MeetupBar of(SaleRole viewer, UUID viewerId, String otherName, MeetupSummary summary,
            TransactionStatus saleStatus, Instant now, ZoneId zone) {
        if (saleStatus == TransactionStatus.COMPLETED) {
            String met = summary.meetup().map(meetup -> " · Met on " + format(meetup.getTime(), zone)).orElse("");
            return new MeetupBar("Sale completed" + met, List.of(Action.VIEW_SALE));
        }
        if (summary.meetup().isPresent()) {
            Meetup meetup = summary.meetup().orElseThrow();
            if (meetup.getPendingProposal().isPresent()) {
                RescheduleProposal proposal = meetup.getPendingProposal().orElseThrow();
                boolean isOwn = proposal.getProposerId().equals(viewerId);
                return new MeetupBar((isOwn ? "You" : otherName) + " proposed moving the meetup to "
                        + format(proposal.getTime(), zone), isOwn
                        ? List.of(Action.WITHDRAW_MOVE, Action.VIEW_SALE)
                        : List.of(Action.ACCEPT_MOVE, Action.REJECT_MOVE, Action.VIEW_SALE));
            }
            if (!meetup.getTime().endAt().isAfter(now)) {
                return new MeetupBar("The meetup time has passed. Confirm completion on the sale if the handover "
                        + "happened", List.of(Action.VIEW_SALE));
            }
            return new MeetupBar("Meetup: " + format(meetup.getTime(), zone),
                    List.of(Action.PROPOSE_MOVE, Action.CANCEL_MEETUP, Action.VIEW_SALE));
        }
        int offered = summary.offeredSlots().size();
        if (offered > 0) {
            List<Action> actions = new ArrayList<>();
            if (viewer == SaleRole.SELLER) {
                actions.add(Action.VIEW_TIMES);
                if (offered < MeetupService.MAX_OFFERED_SLOTS) {
                    actions.add(Action.OFFER_TIME);
                }
            } else {
                actions.add(Action.CHOOSE_TIME);
            }
            actions.add(Action.VIEW_SALE);
            return new MeetupBar(offeredText(offered), actions);
        }
        if (viewer == SaleRole.BUYER) {
            return new MeetupBar("Waiting for the seller to offer meetup times", List.of(Action.VIEW_SALE));
        }
        return new MeetupBar("No meetup times offered yet", List.of(Action.OFFER_TIME, Action.VIEW_SALE));
    }

    /** One line for sale and listing entries: the booked or completed meetup, or how many times are offered. */
    static String summary(MeetupSummary summary, ZoneId zone) {
        return summary.meetup().map(meetup -> (meetup.getStatus() == MeetupStatus.COMPLETED ? "Met on " : "Meetup: ")
                + format(meetup.getTime(), zone)).orElseGet(() -> summary.offeredSlots().isEmpty()
                        ? "No meetup times yet" : offeredText(summary.offeredSlots().size()));
    }

    /** "Fri 2 Oct 2026, 14:00 to 14:30 · Library lobby"; the end repeats the date only when it differs. */
    static String format(MeetupTime time, ZoneId zone) {
        ZonedDateTime start = time.startAt().atZone(zone);
        ZonedDateTime end = time.endAt().atZone(zone);
        String endText = start.toLocalDate().equals(end.toLocalDate()) ? CLOCK.format(end) : DAY.format(end);
        return DAY.format(start) + " to " + endText + " · " + time.location();
    }

    private static String offeredText(int count) {
        return count + (count == 1 ? " time offered" : " times offered");
    }
}
