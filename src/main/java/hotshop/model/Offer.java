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

    /**
     * Creates a pending offer on an available listing; the amount is in SGD cents.
     *
     * @param listing the listing whose state is used
     * @param buyerId the buyer's user ID
     * @param amountCents the offer amount in SGD cents, from 1 to 100,000,000 inclusive
     * @param createdAt the creation time
     * @throws IllegalArgumentException if the supplied values violate the model invariants
     * @throws NullPointerException if a required value is null
     * @throws IllegalStateException if the supplied listing or offer is not in the required state
     */
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

    /**
     * Restores a persisted offer while enforcing the same invariants as creation.
     *
     * @param id the offer ID
     * @param listingId the ID of the listing
     * @param buyerId the buyer's user ID
     * @param amountCents the offer amount in SGD cents, from 1 to 100,000,000 inclusive
     * @param status the persisted lifecycle status
     * @param createdAt the creation time
     * @param closedAt the close time, or null while the offer is pending
     * @return the restored offer with its persisted identity
     * @throws IllegalArgumentException if the supplied values violate the model invariants
     * @throws NullPointerException if a required value is null
     */
    public static Offer restore(UUID id, UUID listingId, UUID buyerId, long amountCents, OfferStatus status,
            Instant createdAt, Instant closedAt) {
        return new Offer(id, listingId, buyerId, amountCents, status, createdAt, closedAt);
    }

    /**
     * Accepts this pending offer and records its close time.
     *
     * @param time the event time, not before the preceding event or creation
     * @throws IllegalStateException if the offer is no longer pending
     * @throws IllegalArgumentException if the close time precedes creation
     * @throws NullPointerException if the time is null
     */
    public void accept(Instant time) {
        close(OfferStatus.ACCEPTED, time);
    }

    /**
     * Rejects this pending offer and records its close time.
     *
     * @param time the event time, not before the preceding event or creation
     * @throws IllegalStateException if the offer is no longer pending
     * @throws IllegalArgumentException if the close time precedes creation
     * @throws NullPointerException if the time is null
     */
    public void reject(Instant time) {
        close(OfferStatus.REJECTED, time);
    }

    /**
     * Withdraws this pending offer and records its close time.
     *
     * @param time the event time, not before the preceding event or creation
     * @throws IllegalStateException if the offer is no longer pending
     * @throws IllegalArgumentException if the close time precedes creation
     * @throws NullPointerException if the time is null
     */
    public void withdraw(Instant time) {
        close(OfferStatus.WITHDRAWN, time);
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
     * Returns the ID of the associated listing.
     *
     * @return the ID of the associated listing
     */
    public UUID getListingId() {
        return listingId;
    }

    /**
     * Returns the buyer's user ID.
     *
     * @return the buyer's user ID
     */
    public UUID getBuyerId() {
        return buyerId;
    }

    /**
     * Returns the offered amount in SGD cents.
     *
     * @return the offered amount in SGD cents
     */
    public long getAmountCents() {
        return amountCents;
    }

    /**
     * Returns the current lifecycle status.
     *
     * @return the current lifecycle status
     */
    public OfferStatus getStatus() {
        return status;
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
     * When the offer was accepted, rejected, or withdrawn; empty while pending.
     *
     * @return the close time, or empty while the offer is pending
     */
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
