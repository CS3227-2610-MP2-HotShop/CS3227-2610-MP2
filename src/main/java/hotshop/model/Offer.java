package hotshop.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A buyer's fixed-price proposal. Services enforce pending-offer uniqueness, authorize
 * operations and recheck the listing before accepting; amounts never change in place.
 */
public final class Offer {
    private final UUID id = UUID.randomUUID();
    private final UUID listingId;
    private final UUID buyerId;
    private final long amountCents;
    private OfferStatus status = OfferStatus.PENDING;

    /** Creates an offer on an available listing; the amount is in SGD cents. */
    public Offer(Listing listing, UUID buyerId, long amountCents) {
        Objects.requireNonNull(listing, "Listing");
        this.buyerId = Objects.requireNonNull(buyerId, "Buyer ID");
        if (listing.getSellerId().equals(buyerId)) {
            throw new IllegalArgumentException("A user cannot offer on their own listing");
        }
        if (listing.getStatus() != ListingStatus.AVAILABLE) {
            throw new IllegalStateException("Offers require an available listing");
        }
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Offer amount must be positive");
        }
        this.listingId = listing.getId();
        this.amountCents = amountCents;
    }

    public void accept() {
        close(OfferStatus.ACCEPTED);
    }

    public void reject() {
        close(OfferStatus.REJECTED);
    }

    public void withdraw() {
        close(OfferStatus.WITHDRAWN);
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

    private void close(OfferStatus outcome) {
        if (status != OfferStatus.PENDING) {
            throw new IllegalStateException("Only a pending offer can be closed");
        }
        status = outcome;
    }
}
