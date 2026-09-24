package hotshop.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A booked handover for one active sale. Either participant may propose moving it; the other
 * accepts or rejects, and the proposer may withdraw. Its outcome follows the sale: services
 * complete or cancel it together with the sale. Kept as history once finished.
 */
public final class Meetup {
    private final UUID id;
    private final UUID transactionId;
    private final UUID buyerId;
    private final UUID sellerId;
    private final Instant createdAt;
    private final List<RescheduleProposal> proposals = new ArrayList<>();
    private MeetupTime time;
    private MeetupStatus status;
    private Instant lastEventAt;

    /** Every persisted field of a meetup, used to restore it. */
    public record Snapshot(UUID id, UUID transactionId, UUID buyerId, UUID sellerId, MeetupTime time,
            MeetupStatus status, Instant createdAt, List<RescheduleProposal> proposals) {
    }

    private Meetup(UUID id, UUID transactionId, UUID buyerId, UUID sellerId, MeetupTime time, MeetupStatus status,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "Meetup ID");
        this.transactionId = Objects.requireNonNull(transactionId, "Transaction ID");
        this.buyerId = Objects.requireNonNull(buyerId, "Buyer ID");
        this.sellerId = Objects.requireNonNull(sellerId, "Seller ID");
        this.time = Objects.requireNonNull(time, "Meetup time");
        this.status = Objects.requireNonNull(status, "Meetup status");
        this.createdAt = Objects.requireNonNull(createdAt, "Booking time");
        if (buyerId.equals(sellerId)) {
            throw new IllegalArgumentException("A meetup needs two different participants");
        }
        lastEventAt = createdAt;
    }

    /** The buyer books an offered slot; the meetup takes the slot's time and place. */
    public static Meetup book(MeetupSlot slot, UUID buyerId, UUID sellerId, Instant bookedAt) {
        Objects.requireNonNull(slot, "Slot");
        return new Meetup(UUID.randomUUID(), slot.transactionId(), buyerId, sellerId, slot.time(),
                MeetupStatus.SCHEDULED, bookedAt);
    }

    /** Restores a persisted meetup, rejecting proposal histories the live model could never produce. */
    public static Meetup restore(Snapshot saved) {
        Objects.requireNonNull(saved, "Saved meetup");
        Meetup meetup = new Meetup(saved.id(), saved.transactionId(), saved.buyerId(), saved.sellerId(),
                saved.time(), saved.status(), saved.createdAt());
        List<RescheduleProposal> history = saved.proposals();
        for (int i = 0; i < history.size(); i++) {
            RescheduleProposal proposal = history.get(i);
            if (!proposal.getMeetupId().equals(meetup.id)) {
                throw new IllegalArgumentException("Proposal belongs to another meetup");
            }
            meetup.requireParticipant(proposal.getProposerId());
            boolean mayBePending = i == history.size() - 1 && meetup.status == MeetupStatus.SCHEDULED;
            if (proposal.getStatus() == ProposalStatus.PENDING && !mayBePending) {
                throw new IllegalArgumentException("Only a scheduled meetup's latest proposal can be pending");
            }
            if (proposal.getCreatedAt().isBefore(meetup.lastEventAt)) {
                throw new IllegalArgumentException("Proposals are out of order");
            }
            meetup.lastEventAt = proposal.getResolvedAt().orElse(proposal.getCreatedAt());
            meetup.proposals.add(proposal);
        }
        return meetup;
    }

    /** Either participant proposes one new time and place while no other proposal is pending. */
    public RescheduleProposal proposeMove(UUID proposerId, MeetupTime newTime, Instant proposedAt) {
        requireScheduled();
        requireParticipant(proposerId);
        Objects.requireNonNull(newTime, "Proposed time");
        requireTime(proposedAt);
        if (getPendingProposal().isPresent()) {
            throw new IllegalStateException("Resolve the pending proposal before proposing another");
        }
        RescheduleProposal proposal = RescheduleProposal.propose(id, proposerId, newTime, proposedAt);
        proposals.add(proposal);
        lastEventAt = proposedAt;
        return proposal;
    }

    /** The other participant accepts; the meetup moves to the proposed time and place. */
    public void acceptMove(UUID proposalId, UUID actorId, Instant resolvedAt) {
        time = resolve(proposalId, actorId, resolvedAt, ProposalStatus.ACCEPTED).getTime();
    }

    /** The other participant declines; the meetup keeps its time. */
    public void rejectMove(UUID proposalId, UUID actorId, Instant resolvedAt) {
        resolve(proposalId, actorId, resolvedAt, ProposalStatus.REJECTED);
    }

    /** The proposer takes back their own pending proposal. */
    public void withdrawMove(UUID proposalId, UUID actorId, Instant resolvedAt) {
        resolve(proposalId, actorId, resolvedAt, ProposalStatus.WITHDRAWN);
    }

    /** Cancels a scheduled meetup, withdrawing any pending proposal; the sale itself is unaffected. */
    public void cancel(Instant cancelledAt) {
        finish(MeetupStatus.CANCELLED, cancelledAt);
    }

    /** Marks a scheduled meetup completed when its sale completes. */
    public void complete(Instant completedAt) {
        finish(MeetupStatus.COMPLETED, completedAt);
    }

    public Optional<RescheduleProposal> getPendingProposal() {
        if (proposals.isEmpty() || proposals.getLast().getStatus() != ProposalStatus.PENDING) {
            return Optional.empty();
        }
        return Optional.of(proposals.getLast());
    }

    /** Immutable snapshots of every proposal, oldest first. */
    public List<RescheduleProposal> getProposals() {
        return List.copyOf(proposals);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getBuyerId() {
        return buyerId;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public MeetupTime getTime() {
        return time;
    }

    public MeetupStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastEventAt() {
        return lastEventAt;
    }

    private void finish(MeetupStatus outcome, Instant time) {
        requireScheduled();
        requireTime(time);
        Optional<RescheduleProposal> pending = getPendingProposal();
        if (pending.isPresent()) {
            proposals.set(proposals.size() - 1, pending.orElseThrow().resolve(ProposalStatus.WITHDRAWN, time));
        }
        status = outcome;
        lastEventAt = time;
    }

    private RescheduleProposal resolve(UUID proposalId, UUID actorId, Instant time, ProposalStatus outcome) {
        requireScheduled();
        requireParticipant(actorId);
        requireTime(time);
        Objects.requireNonNull(proposalId, "Proposal ID");
        RescheduleProposal pending = getPendingProposal()
                .filter(proposal -> proposal.getId().equals(proposalId))
                .orElseThrow(() -> new IllegalStateException("The specified proposal is not pending"));
        boolean isProposer = pending.getProposerId().equals(actorId);
        if ((outcome == ProposalStatus.WITHDRAWN) != isProposer) {
            throw new IllegalArgumentException(
                    "Only the proposer may withdraw; only the other participant may respond");
        }
        proposals.set(proposals.size() - 1, pending.resolve(outcome, time));
        lastEventAt = time;
        return pending;
    }

    private void requireScheduled() {
        if (status != MeetupStatus.SCHEDULED) {
            throw new IllegalStateException("Meetup is no longer scheduled");
        }
    }

    private void requireParticipant(UUID actorId) {
        Objects.requireNonNull(actorId, "Actor ID");
        if (!actorId.equals(buyerId) && !actorId.equals(sellerId)) {
            throw new IllegalArgumentException("Only the sale's participants may change its meetup");
        }
    }

    private void requireTime(Instant time) {
        Objects.requireNonNull(time, "Time");
        if (time.isBefore(lastEventAt)) {
            throw new IllegalArgumentException("Time cannot precede the meetup's previous event");
        }
    }
}
