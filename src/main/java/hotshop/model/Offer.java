package hotshop.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A buyer's fixed-price proposal. Services enforce pending-offer uniqueness, authorize
 * operations and recheck the listing before accepting; amounts never change in place.
 */
public final class Offer {
    private final UUID id;
    private final UUID listingId;
    private final UUID buyerId;
    private final long amountCents;
    private final Instant createdAt;
    private OfferStatus status;
    private Instant closedAt;

    /** Creates a pending offer on an available listing; the amount is in SGD cents. */
    public Offer(Listing listing, UUID buyerId, long amountCents, Instant createdAt) {
        Objects.requireNonNull(listing, "Listing");
        Objects.requireNonNull(buyerId, "Buyer ID");
        if (listing.getSellerId().equals(buyerId)) {
            throw new IllegalArgumentException("A user cannot offer on their own listing");
        }
        if (listing.getStatus() != ListingStatus.AVAILABLE) {
            throw new IllegalStateException("Offers require an available listing");
        }
        this.id = UUID.randomUUID();
        this.listingId = listing.getId();
        this.buyerId = buyerId;
        this.amountCents = validateAmount(amountCents);
        this.createdAt = Objects.requireNonNull(createdAt, "Creation time");
        this.status = OfferStatus.PENDING;
    }

    private Offer(UUID id, UUID listingId, UUID buyerId, long amountCents, OfferStatus status,
            Instant createdAt, Instant closedAt) {
        this.id = Objects.requireNonNull(id, "Offer ID");
        this.listingId = Objects.requireNonNull(listingId, "Listing ID");
        this.buyerId = Objects.requireNonNull(buyerId, "Buyer ID");
        this.amountCents = validateAmount(amountCents);
        this.status = Objects.requireNonNull(status, "Offer status");
        this.createdAt = Objects.requireNonNull(createdAt, "Creation time");
        if ((status == OfferStatus.PENDING) != (closedAt == null)) {
            throw new IllegalArgumentException("Only closed offers have a close time");
        }
        if (closedAt != null && closedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Close time cannot precede creation time");
        }
        this.closedAt = closedAt;
    }

    /** Restores a persisted offer while enforcing the same invariants as creation. */
    public static Offer restore(UUID id, UUID listingId, UUID buyerId, long amountCents, OfferStatus status,
            Instant createdAt, Instant closedAt) {
        return new Offer(id, listingId, buyerId, amountCents, status, createdAt, closedAt);
    }

    public void accept(Instant time) {
        close(OfferStatus.ACCEPTED, time);
    }

    public void reject(Instant time) {
        close(OfferStatus.REJECTED, time);
    }

    public void withdraw(Instant time) {
        close(OfferStatus.WITHDRAWN, time);
    }

    public UUID getId() {
        return id;
    }

    public UUID getListingId() {
        return listingId;
    }

    public UUID getBuyerId() {
        return buyerId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public OfferStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** When the offer was accepted, rejected, or withdrawn; empty while pending. */
    public Optional<Instant> getClosedAt() {
        return Optional.ofNullable(closedAt);
    }

    private void close(OfferStatus outcome, Instant time) {
        Objects.requireNonNull(time, "Close time");
        if (status != OfferStatus.PENDING) {
            throw new IllegalStateException("Only a pending offer can be closed");
        }
        if (time.isBefore(createdAt)) {
            throw new IllegalArgumentException("Close time cannot precede creation time");
        }
        status = outcome;
        closedAt = time;
    }

    private static long validateAmount(long amountCents) {
        if (amountCents <= 0 || amountCents > ListingDetails.MAX_PRICE_CENTS) {
            throw new IllegalArgumentException("Offer amount must be between S$0.01 and S$1,000,000.00");
        }
        return amountCents;
    }
}
