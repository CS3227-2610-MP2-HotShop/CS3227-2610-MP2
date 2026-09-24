package hotshop.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ListingDetailsTest {
    @Test
    void constructor_lowerBoundaries_acceptsValues() {
        ListingDetails details = new ListingDetails(" A ", " B ", Category.OTHER, 1, Condition.NEW, " C ");
        assertEquals("A", details.title());
        assertEquals("B", details.description());
        assertEquals("C", details.pickupLocation());
        assertEquals(1, details.priceCents());
    }

    @Test
    void constructor_upperBoundaries_acceptsValues() {
        ListingDetails details = new ListingDetails("a".repeat(120), "b".repeat(5000),
                Category.OTHER, 100_000_000, Condition.POOR, "c".repeat(200));
        assertEquals(120, details.title().length());
        assertEquals(5000, details.description().length());
        assertEquals(200, details.pickupLocation().length());
        assertEquals(100_000_000, details.priceCents());
    }

    @ParameterizedTest
    @ValueSource(longs = {100_000_001, Long.MAX_VALUE})
    void constructor_priceAboveMaximum_throwsException(long price) {
        assertThrows(IllegalArgumentException.class,
                () -> new ListingDetails("Title", "Description", Category.OTHER, price, Condition.NEW, "Campus"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void constructor_blankTitle_throwsException(String title) {
        assertThrows(IllegalArgumentException.class,
                () -> new ListingDetails(title, "Description", Category.OTHER, 1, Condition.NEW, "Campus"));
    }

    @Test
    void constructor_overlongTitle_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ListingDetails("a".repeat(121), "Description", Category.OTHER, 1, Condition.NEW, "Campus"));
    }

    @Test
    void constructor_blankDescription_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ListingDetails("Title", " ", Category.OTHER, 1, Condition.NEW, "Campus"));
    }

    @Test
    void constructor_overlongDescription_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ListingDetails("Title", "a".repeat(5001), Category.OTHER, 1, Condition.NEW, "Campus"));
    }

    @Test
    void constructor_blankLocation_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ListingDetails("Title", "Description", Category.OTHER, 1, Condition.NEW, " "));
    }

    @Test
    void constructor_overlongLocation_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ListingDetails("Title", "Description", Category.OTHER, 1, Condition.NEW, "a".repeat(201)));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void constructor_nonpositivePrice_throwsException(long price) {
        assertThrows(IllegalArgumentException.class,
                () -> new ListingDetails("Title", "Description", Category.OTHER, price, Condition.NEW, "Campus"));
    }

    @Test
    void constructor_nullCategory_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new ListingDetails("Title", "Description", null, 1, Condition.NEW, "Campus"));
    }

    @Test
    void constructor_nullCondition_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new ListingDetails("Title", "Description", Category.OTHER, 1, null, "Campus"));
    }

    @Test
    void constructor_supplementaryCharacters_countsCharactersRatherThanCodeUnits() {
        String title = "\uD83D\uDE00".repeat(120);
        ListingDetails details = new ListingDetails(title, "Description", Category.OTHER, 1, Condition.NEW, "Campus");
        assertEquals(title, details.title());
    }
}
