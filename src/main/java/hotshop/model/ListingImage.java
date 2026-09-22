package hotshop.model;

/** An image owned by its containing listing, with a storage-relative name and zero-based order. */
public record ListingImage(String filename, int displayOrder) {
    public ListingImage {
        filename = ModelValidation.filename(filename);
        if (displayOrder < 0) {
            throw new IllegalArgumentException("Image display order cannot be negative");
        }
    }
}
