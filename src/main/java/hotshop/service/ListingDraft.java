package hotshop.service;

import hotshop.model.Category;
import hotshop.model.Condition;

/** Unvalidated listing form values; ListingService reports invalid values as validation failures. */
public record ListingDraft(String title, String description, Category category, long priceCents,
        Condition condition, String pickupLocation) {
}
