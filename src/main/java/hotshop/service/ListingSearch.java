package hotshop.service;

import java.util.Comparator;
import java.util.Locale;
import java.util.Set;

import hotshop.model.Category;
import hotshop.model.Condition;
import hotshop.model.Listing;
import hotshop.model.ListingDetails;

/**
 * Buyer search criteria. A null or blank title, null category, null or empty conditions, or
 * null price bound applies no filter; a null sort means newest first. Prices are SGD cents.
 */
public record ListingSearch(String titleText, Category category, Set<Condition> conditions,
        Long minPriceCents, Long maxPriceCents, ListingSort sort) {
    private static final Comparator<Listing> NEWEST_FIRST =
            Comparator.comparing(Listing::getCreatedAt).reversed();

    /** Every available listing of other sellers, newest first. */
    public static ListingSearch all() {
        return new ListingSearch(null, null, null, null, null, null);
    }

    /** True when the listing satisfies every supplied filter; status and seller are checked elsewhere. */
    boolean matches(Listing listing) {
        ListingDetails details = listing.getDetails();
        return matchesTitle(details.title())
                && (category == null || category == details.category())
                && (conditions == null || conditions.isEmpty() || conditions.contains(details.condition()))
                && (minPriceCents == null || details.priceCents() >= minPriceCents)
                && (maxPriceCents == null || details.priceCents() <= maxPriceCents);
    }

    /** The requested order, with ties broken newest first. */
    public Comparator<Listing> order() {
        Comparator<Listing> byPrice = Comparator.comparingLong(listing -> listing.getDetails().priceCents());
        return switch (sort == null ? ListingSort.NEWEST : sort) {
            case NEWEST -> NEWEST_FIRST;
            case PRICE_LOW_TO_HIGH -> byPrice.thenComparing(NEWEST_FIRST);
            case PRICE_HIGH_TO_LOW -> byPrice.reversed().thenComparing(NEWEST_FIRST);
        };
    }

    /** Case-insensitive for all scripts, which SQLite's own matching is not. */
    private boolean matchesTitle(String title) {
        if (titleText == null || titleText.isBlank()) {
            return true;
        }
        return title.toLowerCase(Locale.ROOT).contains(titleText.strip().toLowerCase(Locale.ROOT));
    }
}
