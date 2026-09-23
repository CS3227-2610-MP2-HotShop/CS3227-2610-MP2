package hotshop.model;

import java.util.Objects;

/** Validated sale terms, replaced together to prevent partially applied edits. Prices are SGD cents. */
public record ListingDetails(String title, String description, Category category,
        long priceCents, Condition condition, String pickupLocation) {
    /** S$1,000,000; catches mistyped prices. */
    public static final long MAX_PRICE_CENTS = 100_000_000;

    public ListingDetails {
        title = ModelValidation.text(title, 120, "Title");
        description = ModelValidation.text(description, 5000, "Description");
        Objects.requireNonNull(category, "Category");
        if (priceCents <= 0 || priceCents > MAX_PRICE_CENTS) {
            throw new IllegalArgumentException("Price must be between S$0.01 and S$1,000,000.00");
        }
        Objects.requireNonNull(condition, "Condition");
        pickupLocation = ModelValidation.text(pickupLocation, 200, "Pickup location");
    }
}
