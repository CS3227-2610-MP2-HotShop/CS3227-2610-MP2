package hotshop.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable request snapshot. Only its owning Transaction creates or resolves requests. */
public final class CancellationRequest {
    private final UUID id;
    private final UUID transactionId;
    private final UUID requesterId;
    private final Instant createdAt;
    private final CancellationStatus status;
    private final Instant resolvedAt;

    CancellationRequest(UUID transactionId, UUID requesterId, Instant createdAt) {
        this(UUID.randomUUID(), transactionId, requesterId, createdAt, CancellationStatus.PENDING, null);
    }

    private CancellationRequest(UUID id, UUID transactionId, UUID requesterId, Instant createdAt,
            CancellationStatus status, Instant resolvedAt) {
        this.id = id;
        this.transactionId = transactionId;
        this.requesterId = requesterId;
        this.createdAt = createdAt;
        this.status = status;
        this.resolvedAt = resolvedAt;
    }

    /**
     * Restores a persisted request. Its owning Transaction's restoration checks that it belongs to
     * that sale and fits the sale's history.
     *
     * @param id the cancellation request ID
     * @param transactionId the ID of the agreed sale
     * @param requesterId the ID of the participant requesting cancellation
     * @param createdAt the creation time
     * @param status the persisted lifecycle status
     * @param resolvedAt the resolution time; null only when restoring a pending request or proposal
     * @return the restored cancellation request with its persisted identity
     * @throws IllegalArgumentException if the resolution time is inconsistent with the status or creation time
     * @throws NullPointerException if a required value is null
     */
    public static CancellationRequest restore(UUID id, UUID transactionId, UUID requesterId, Instant createdAt,
            CancellationStatus status, Instant resolvedAt) {
        Objects.requireNonNull(id, "Request ID");
        Objects.requireNonNull(transactionId, "Transaction ID");
        Objects.requireNonNull(requesterId, "Requester ID");
        Objects.requireNonNull(createdAt, "Request time");
        Objects.requireNonNull(status, "Request status");
        if ((status == CancellationStatus.PENDING) != (resolvedAt == null)) {
            throw new IllegalArgumentException("Only resolved requests have a resolution time");
        }
        if (resolvedAt != null && resolvedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Resolution cannot precede the request");
        }
        return new CancellationRequest(id, transactionId, requesterId, createdAt, status, resolvedAt);
    }

    CancellationRequest resolve(CancellationStatus outcome, Instant time) {
        return new CancellationRequest(id, transactionId, requesterId, createdAt, outcome, time);
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
     * Returns the ID of the associated agreed sale.
     *
     * @return the ID of the associated agreed sale
     */
    public UUID getTransactionId() {
        return transactionId;
    }

    /**
     * Returns the user ID of the cancellation requester.
     *
     * @return the user ID of the cancellation requester
     */
    public UUID getRequesterId() {
        return requesterId;
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
    public CancellationStatus getStatus() {
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
