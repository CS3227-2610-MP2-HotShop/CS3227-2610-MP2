package hotshop.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable snapshot of a proposal to move a meetup. Only its owning Meetup creates or resolves proposals. */
public final class RescheduleProposal {
    private final UUID id;
    private final UUID meetupId;
    private final UUID proposerId;
    private final MeetupTime time;
    private final Instant createdAt;
    private final ProposalStatus status;
    private final Instant resolvedAt;

    private RescheduleProposal(UUID id, UUID meetupId, UUID proposerId, MeetupTime time, Instant createdAt,
            ProposalStatus status, Instant resolvedAt) {
        this.id = id;
        this.meetupId = meetupId;
        this.proposerId = proposerId;
        this.time = time;
        this.createdAt = createdAt;
        this.status = status;
        this.resolvedAt = resolvedAt;
    }

    static RescheduleProposal propose(UUID meetupId, UUID proposerId, MeetupTime time, Instant createdAt) {
        return new RescheduleProposal(UUID.randomUUID(), meetupId, proposerId, time, createdAt,
                ProposalStatus.PENDING, null);
    }

    /**
     * Restores a persisted proposal; its owning Meetup's restoration checks it fits the meetup's history.
     *
     * @param id the reschedule proposal ID
     * @param meetupId the ID of the meetup
     * @param proposerId the ID of the participant proposing the move
     * @param time the meetup time and location
     * @param createdAt the creation time
     * @param status the persisted lifecycle status
     * @param resolvedAt the resolution time; null only when restoring a pending request or proposal
     * @return the restored reschedule proposal with its persisted identity
     * @throws IllegalArgumentException if the resolution time is inconsistent with the status or creation time
     * @throws NullPointerException if a required value is null
     */
    public static RescheduleProposal restore(UUID id, UUID meetupId, UUID proposerId, MeetupTime time,
            Instant createdAt, ProposalStatus status, Instant resolvedAt) {
        Objects.requireNonNull(id, "Proposal ID");
        Objects.requireNonNull(meetupId, "Meetup ID");
        Objects.requireNonNull(proposerId, "Proposer ID");
        Objects.requireNonNull(time, "Proposed time");
        Objects.requireNonNull(createdAt, "Proposal time");
        Objects.requireNonNull(status, "Proposal status");
        if ((status == ProposalStatus.PENDING) != (resolvedAt == null)) {
            throw new IllegalArgumentException("Only resolved proposals have a resolution time");
        }
        if (resolvedAt != null && resolvedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Resolution cannot precede the proposal");
        }
        return new RescheduleProposal(id, meetupId, proposerId, time, createdAt, status, resolvedAt);
    }

    RescheduleProposal resolve(ProposalStatus outcome, Instant time) {
        return new RescheduleProposal(id, meetupId, proposerId, this.time, createdAt, outcome, time);
    }

    /**
     * Returns the stable identity of this record.
     *
     * @return the stable identity of this record
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns the ID of the associated meetup.
     *
     * @return the ID of the associated meetup
     */
    public UUID getMeetupId() {
        return meetupId;
    }

    /**
     * Returns the user ID of the move proposer.
     *
     * @return the user ID of the move proposer
     */
    public UUID getProposerId() {
        return proposerId;
    }

    /**
     * Returns the meetup time and location.
     *
     * @return the meetup time and location
     */
    public MeetupTime getTime() {
        return time;
    }

    /**
     * Returns the creation time.
     *
     * @return the creation time
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the current lifecycle status.
     *
     * @return the current lifecycle status
     */
    public ProposalStatus getStatus() {
        return status;
    }

    /**
     * Returns the resolution time, or empty while pending.
     *
     * @return the resolution time, or empty while pending
     */
    public Optional<Instant> getResolvedAt() {
        return Optional.ofNullable(resolvedAt);
    }
}
