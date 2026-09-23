package hotshop.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One indivisible sale. Services authorize the actor and coordinate offer invalidation
 * and transaction changes; this model governs the listing's own state.
 */
public final class Listing {
    public static final int MAX_IMAGES = 10;

    private final UUID id;
    private final UUID sellerId;
    private final Instant createdAt;
    private ListingDetails details;
    private List<ListingImage> images;
    private ListingStatus status;
    private Instant updatedAt;

    /** Creates an available listing; both timestamps start at the creation time. */
    public Listing(UUID sellerId, ListingDetails details, List<ListingImage> images, Instant createdAt) {
        this(UUID.randomUUID(), sellerId, details, images, ListingStatus.AVAILABLE, createdAt, createdAt);
    }

    private Listing(UUID id, UUID sellerId, ListingDetails details, List<ListingImage> images,
            ListingStatus status, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "Listing ID");
        this.sellerId = Objects.requireNonNull(sellerId, "Seller ID");
        this.details = Objects.requireNonNull(details, "Listing details");
        this.images = validateImages(images);
        this.status = Objects.requireNonNull(status, "Listing status");
        this.createdAt = Objects.requireNonNull(createdAt, "Creation time");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Update time");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Update time cannot precede creation time");
        }
    }

    /** Restores a persisted listing while enforcing the same invariants as creation. */
    public static Listing restore(UUID id, UUID sellerId, ListingDetails details, List<ListingImage> images,
            ListingStatus status, Instant createdAt, Instant updatedAt) {
        return new Listing(id, sellerId, details, images, status, createdAt, updatedAt);
    }

    /**
     * Replaces all sale details while available. Only an actual change advances the update time.
     *
     * @return true when sale terms changed and the service must reject pending offers
     */
    public boolean update(ListingDetails newDetails, List<ListingImage> newImages, Instant time) {
        requireStatus(ListingStatus.AVAILABLE);
        Objects.requireNonNull(newDetails, "Listing details");
        Objects.requireNonNull(time, "Update time");
        if (time.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Update time cannot precede the previous update");
        }
        List<ListingImage> checkedImages = validateImages(newImages);
        boolean hasChanges = !details.equals(newDetails) || !images.equals(checkedImages);
        if (hasChanges) {
            details = newDetails;
            images = checkedImages;
            updatedAt = time;
        }
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

    /**
     * True for available or archived listings; reserved and sold listings always have a transaction.
     * Services must also refuse listings with offer or conversation history.
     */
    public boolean isDeletable() {
        return status == ListingStatus.AVAILABLE || status == ListingStatus.ARCHIVED;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Time of the last actual change to sale details or images; status changes do not count. */
    public Instant getUpdatedAt() {
        return updatedAt;
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
