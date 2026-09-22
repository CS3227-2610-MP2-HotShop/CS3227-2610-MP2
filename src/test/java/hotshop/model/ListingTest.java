package hotshop.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class ListingTest {
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    static ListingDetails details(String title, long price) {
        return new ListingDetails(title, "Three wooden chairs", Category.FURNITURE,
                price, Condition.GOOD, "Campus");
    }

    private Listing listingInState(ListingStatus status) {
        Listing listing = new Listing(SELLER, details("Chairs", 5000), List.of());
        if (status == ListingStatus.RESERVED || status == ListingStatus.SOLD) {
            listing.reserve();
        }
        if (status == ListingStatus.SOLD) {
            listing.markSold();
        }
        if (status == ListingStatus.ARCHIVED) {
            listing.archive();
        }
        return listing;
    }

    static Stream<ListingDetails> changedDetails() {
        return Stream.of(
                new ListingDetails("Other", "Three wooden chairs", Category.FURNITURE, 5000, Condition.GOOD, "Campus"),
                new ListingDetails("Chairs", "Other", Category.FURNITURE, 5000, Condition.GOOD, "Campus"),
                new ListingDetails("Chairs", "Three wooden chairs", Category.OTHER, 5000, Condition.GOOD, "Campus"),
                new ListingDetails("Chairs", "Three wooden chairs", Category.FURNITURE, 1, Condition.GOOD, "Campus"),
                new ListingDetails("Chairs", "Three wooden chairs", Category.FURNITURE, 5000, Condition.NEW, "Campus"),
                new ListingDetails("Chairs", "Three wooden chairs", Category.FURNITURE, 5000, Condition.GOOD, "Other"));
    }

    @ParameterizedTest
    @MethodSource("changedDetails")
    void update_eachSaleTermChange_reportsOfferInvalidation(ListingDetails changed) {
        Listing listing = listingInState(ListingStatus.AVAILABLE);
        assertTrue(listing.update(changed, List.of()));
        assertEquals(changed, listing.getDetails());
    }

    @Test
    void update_imageChanges_reportsOfferInvalidation() {
        Listing listing = listingInState(ListingStatus.AVAILABLE);
        List<ListingImage> images = List.of(new ListingImage("chair.jpg", 0));
        assertTrue(listing.update(listing.getDetails(), images));
        assertFalse(listing.update(listing.getDetails(), images));
        assertTrue(listing.update(listing.getDetails(), List.of()));
    }

    @Test
    void update_invalidImages_preservesAllPreviousDetails() {
        Listing listing = listingInState(ListingStatus.AVAILABLE);
        assertThrows(IllegalArgumentException.class, () -> listing.update(details("New", 1),
                List.of(new ListingImage("a.jpg", 1))));
        assertEquals("Chairs", listing.getDetails().title());
        assertTrue(listing.getImages().isEmpty());
    }

    @Test
    void constructor_tenImages_acceptsLimit() {
        List<ListingImage> images = IntStream.range(0, 10)
                .mapToObj(i -> new ListingImage(i + ".jpg", i)).toList();
        Listing listing = new Listing(SELLER, details("Chairs", 5000), images);
        assertEquals(10, listing.getImages().size());
    }

    @Test
    void constructor_elevenImages_throwsException() {
        List<ListingImage> images = IntStream.range(0, 11)
                .mapToObj(i -> new ListingImage(i + ".jpg", i)).toList();
        assertThrows(IllegalArgumentException.class, () -> new Listing(SELLER, details("Chairs", 5000), images));
    }

    @Test
    void constructor_duplicateImageOrder_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new Listing(SELLER, details("Chairs", 5000),
                List.of(new ListingImage("a.jpg", 0), new ListingImage("b.jpg", 0))));
    }

    @Test
    void constructor_nullSeller_throwsException() {
        assertThrows(NullPointerException.class, () -> new Listing(null, details("Chairs", 5000), List.of()));
    }

    @Test
    void constructor_nullDetails_throwsException() {
        assertThrows(NullPointerException.class, () -> new Listing(SELLER, null, List.of()));
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"RESERVED", "SOLD", "ARCHIVED"})
    void reserve_unavailableListing_throwsException(ListingStatus status) {
        Listing listing = listingInState(status);
        assertThrows(IllegalStateException.class, listing::reserve);
        assertEquals(status, listing.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"AVAILABLE", "SOLD", "ARCHIVED"})
    void release_unreservedListing_throwsException(ListingStatus status) {
        Listing listing = listingInState(status);
        assertThrows(IllegalStateException.class, listing::release);
        assertEquals(status, listing.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"AVAILABLE", "SOLD", "ARCHIVED"})
    void markSold_unreservedListing_throwsException(ListingStatus status) {
        Listing listing = listingInState(status);
        assertThrows(IllegalStateException.class, listing::markSold);
        assertEquals(status, listing.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"RESERVED", "SOLD", "ARCHIVED"})
    void update_unavailableListing_throwsException(ListingStatus status) {
        Listing listing = listingInState(status);
        assertThrows(IllegalStateException.class, () -> listing.update(details("Other", 1), List.of()));
        assertEquals("Chairs", listing.getDetails().title());
    }

    @Test
    void release_reservedListing_allowsEditingAgain() {
        Listing listing = new Listing(SELLER, details("Chairs", 5000), List.of());
        listing.reserve();
        listing.release();
        assertEquals(ListingStatus.AVAILABLE, listing.getStatus());
        assertTrue(listing.update(details("Chair", 2500), List.of()));
    }

    @Test
    void archive_reservedListing_throwsException() {
        Listing listing = new Listing(SELLER, details("Chairs", 5000), List.of());
        listing.reserve();
        assertThrows(IllegalStateException.class, listing::archive);
        assertEquals(ListingStatus.RESERVED, listing.getStatus());
    }

    @Test
    void markSold_reservedListing_freezesDetailsAndAllowsArchival() {
        Listing listing = new Listing(SELLER, details("Chairs", 5000), List.of());
        listing.reserve();
        listing.markSold();
        assertEquals(ListingStatus.SOLD, listing.getStatus());
        assertThrows(IllegalStateException.class, listing::release);
        assertThrows(IllegalStateException.class, () -> listing.update(details("Chair", 2500), List.of()));
        listing.archive();
        assertEquals(ListingStatus.ARCHIVED, listing.getStatus());
        assertThrows(IllegalStateException.class, listing::reserve);
    }

    @Test
    void update_changedAndUnchangedDetails_reportsOfferInvalidation() {
        Listing listing = new Listing(SELLER, details("Chairs", 5000), List.of());
        assertFalse(listing.update(details(" Chairs ", 5000), List.of()));
        assertTrue(listing.update(details("Chair", 2500), List.of()));
        assertEquals("Chair", listing.getDetails().title());
        assertEquals(2500, listing.getDetails().priceCents());
    }

    @Test
    void constructor_mutableImages_defensivelyCopiesCollection() {
        List<ListingImage> images = new ArrayList<>();
        images.add(new ListingImage("chairs.jpg", 0));
        Listing listing = new Listing(SELLER, details("Chairs", 5000), images);
        images.clear();
        assertEquals(1, listing.getImages().size());
        assertThrows(UnsupportedOperationException.class, () -> listing.getImages().clear());
    }

    @Test
    void update_reservedListing_rejectsEditWithoutChangingDetails() {
        Listing listing = new Listing(SELLER, details("Chairs", 5000), List.of());
        listing.reserve();
        assertThrows(IllegalStateException.class, () -> listing.update(details("Chair", 2500), List.of()));
        assertEquals("Chairs", listing.getDetails().title());
        assertEquals(ListingStatus.RESERVED, listing.getStatus());
    }
}
