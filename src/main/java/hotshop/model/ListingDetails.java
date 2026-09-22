package hotshop.model;

import java.util.Objects;

/** Validated sale terms, replaced together to prevent partially applied edits. Prices are SGD cents. */
public record ListingDetails(String title, String description, Category category,
        long priceCents, Condition condition, String pickupLocation) {
    public ListingDetails {
        title = ModelValidation.text(title, 120, "Title");
        description = ModelValidation.text(description, 5000, "Description");
        Objects.requireNonNull(category, "Category");
        if (priceCents <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        Objects.requireNonNull(condition, "Condition");
        pickupLocation = ModelValidation.text(pickupLocation, 200, "Pickup location");
    }
}
