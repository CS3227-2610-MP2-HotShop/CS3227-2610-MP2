package hotshop.model;

import java.time.Instant;
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

    CancellationRequest resolve(CancellationStatus outcome, Instant time) {
        return new CancellationRequest(id, transactionId, requesterId, createdAt, outcome, time);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public CancellationStatus getStatus() {
        return status;
    }

    public Optional<Instant> getResolvedAt() {
        return Optional.ofNullable(resolvedAt);
    }
}
