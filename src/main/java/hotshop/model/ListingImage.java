package hotshop.model;

/** An image owned by its containing listing, with a storage-relative name and zero-based order. */
public record ListingImage(String filename, int displayOrder) {
    /**
     * Validates a storage-relative image filename and its display position.
     *
     * @param filename the storage-relative image filename
     * @param displayOrder the nonnegative, zero-based image position
     * @throws IllegalArgumentException if the supplied values violate the documented validation limits
     * @throws NullPointerException if a required value is null
     */
    public ListingImage {
        filename = ModelValidation.filename(filename);
        if (displayOrder < 0) {
            throw new IllegalArgumentException("Image display order cannot be negative");
        }
    }
}
