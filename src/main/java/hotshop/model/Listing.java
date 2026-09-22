package hotshop.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One indivisible sale. Services authorize the actor and coordinate offer invalidation
 * and transaction changes; this model governs the listing's own state.
 */
public final class Listing {
    private static final int MAX_IMAGES = 10;

    private final UUID id = UUID.randomUUID();
    private final UUID sellerId;
    private ListingDetails details;
    private List<ListingImage> images;
    private ListingStatus status = ListingStatus.AVAILABLE;

    public Listing(UUID sellerId, ListingDetails details, List<ListingImage> images) {
        this.sellerId = Objects.requireNonNull(sellerId, "Seller ID");
        this.details = Objects.requireNonNull(details, "Listing details");
        this.images = validateImages(images);
    }

    /**
     * Replaces all sale details while available.
     *
     * @return true when sale terms changed and the service must reject pending offers
     */
    public boolean update(ListingDetails newDetails, List<ListingImage> newImages) {
        requireStatus(ListingStatus.AVAILABLE);
        Objects.requireNonNull(newDetails, "Listing details");
        List<ListingImage> checkedImages = validateImages(newImages);
        boolean hasChanges = !details.equals(newDetails) || !images.equals(checkedImages);
        details = newDetails;
        images = checkedImages;
        return hasChanges;
    }

    /** Reserves an available listing as part of coordinated offer acceptance. */
    public void reserve() {
        requireStatus(ListingStatus.AVAILABLE);
        status = ListingStatus.RESERVED;
    }

    /** Releases a reservation after the transaction has been cancelled. */
    public void release() {
        requireStatus(ListingStatus.RESERVED);
        status = ListingStatus.AVAILABLE;
    }

    /** Marks a reserved listing sold after both transaction participants confirm. */
    public void markSold() {
        requireStatus(ListingStatus.RESERVED);
        status = ListingStatus.SOLD;
    }

    /** Archives an available or sold listing; services must also reject pending offers. */
    public void archive() {
        if (status != ListingStatus.AVAILABLE && status != ListingStatus.SOLD) {
            throw new IllegalStateException("Only available or sold listings can be archived");
        }
        status = ListingStatus.ARCHIVED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public ListingDetails getDetails() {
        return details;
    }

    public List<ListingImage> getImages() {
        return images;
    }

    public ListingStatus getStatus() {
        return status;
    }

    private void requireStatus(ListingStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Listing must be " + expected);
        }
    }

    private static List<ListingImage> validateImages(List<ListingImage> images) {
        List<ListingImage> copy = List.copyOf(images);
        if (copy.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("A listing may have at most " + MAX_IMAGES + " images");
        }
        for (int i = 0; i < copy.size(); i++) {
            if (copy.get(i).displayOrder() != i) {
                throw new IllegalArgumentException("Image order must match its zero-based list position");
            }
        }
        return copy;
    }
}
