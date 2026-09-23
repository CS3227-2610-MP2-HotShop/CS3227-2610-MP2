package hotshop.service;

import static hotshop.service.ListingServiceTest.assertFailure;
import static hotshop.service.ListingServiceTest.registerAndLogin;
import static hotshop.service.ListingServiceTest.setStatus;
import static hotshop.service.ListingServiceTest.titles;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import hotshop.ApplicationRuntime;
import hotshop.model.Category;
import hotshop.model.Condition;
import hotshop.model.ListingStatus;

class ListingSearchTest {
    @TempDir
    Path directory;

    private final TestClock clock = new TestClock(Instant.parse("2026-09-24T00:00:00Z"));

    @Test
    void searchListings_noFilters_returnsOtherSellersAvailableListingsNewestFirst() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            create(runtime, "Older", Category.BOOKS, 100, Condition.GOOD);
            create(runtime, "Newer", Category.BOOKS, 100, Condition.GOOD);
            UUID reserved = create(runtime, "Reserved", Category.BOOKS, 100, Condition.GOOD);
            UUID archived = create(runtime, "Archived", Category.BOOKS, 100, Condition.GOOD);
            setStatus(directory, reserved, ListingStatus.RESERVED);
            runtime.getListings().archiveListing(archived).join();
            registerAndLogin(runtime, "bobby");
            create(runtime, "Bob own", Category.BOOKS, 100, Condition.GOOD);
            assertEquals(List.of("Newer", "Older"), search(runtime, ListingSearch.all()));
        }
    }

    @Test
    void searchListings_titleText_matchesContainedTextIgnoringCase() throws Exception {
        try (ApplicationRuntime runtime = seeded()) {
            assertEquals(List.of("Wooden chairs"), search(runtime, byTitle("CHAIR")));
            assertEquals(List.of("Ärger textbook"), search(runtime, byTitle("ärger")));
            assertEquals(List.of(), search(runtime, byTitle("description")));
        }
    }

    @Test
    void searchListings_blankTitleText_appliesNoTextFilter() throws Exception {
        try (ApplicationRuntime runtime = seeded()) {
            assertEquals(3, search(runtime, byTitle("  ")).size());
        }
    }

    @Test
    void searchListings_category_returnsOnlyThatCategory() throws Exception {
        try (ApplicationRuntime runtime = seeded()) {
            var search = new ListingSearch(null, Category.FURNITURE, null, null, null, null);
            assertEquals(List.of("Wooden chairs"), search(runtime, search));
        }
    }

    @Test
    void searchListings_severalConditions_returnsAnyMatchingCondition() throws Exception {
        try (ApplicationRuntime runtime = seeded()) {
            var search = new ListingSearch(null, null, Set.of(Condition.NEW, Condition.POOR), null, null, null);
            assertEquals(List.of("Old phone", "Wooden chairs"), search(runtime, search));
        }
    }

    @ParameterizedTest
    @CsvSource({"200, 300, 2", "300, 300, 1", "0, 199, 1", "301, 100000000, 0"})
    void searchListings_priceRange_includesBothBounds(long min, long max, int expected) throws Exception {
        try (ApplicationRuntime runtime = seeded()) {
            var search = new ListingSearch(null, null, null, min, max, null);
            assertEquals(expected, search(runtime, search).size());
        }
    }

    @Test
    void searchListings_minimumAboveMaximum_reportsValidationWithBothPrices() throws Exception {
        try (ApplicationRuntime runtime = seeded()) {
            var search = new ListingSearch(null, null, null, 300L, 200L, null);
            var failure = assertFailure(ServiceException.Code.VALIDATION,
                    () -> runtime.getListings().searchListings(search).join());
            assertTrue(failure.getMessage().contains("S$3.00") && failure.getMessage().contains("S$2.00"),
                    failure.getMessage());
        }
    }

    @ParameterizedTest
    @CsvSource({"-1, ", ", -1"})
    void searchListings_negativePriceBound_reportsValidation(Long min, Long max) throws Exception {
        assertInvalidPrices(min, max);
    }

    @ParameterizedTest
    @CsvSource({"100000001, ", ", 100000001"})
    void searchListings_priceBoundAboveMaximum_reportsValidation(Long min, Long max) throws Exception {
        assertInvalidPrices(min, max);
    }

    @Test
    void searchListings_sellerProfile_isIncludedInResults() throws Exception {
        try (ApplicationRuntime runtime = seeded()) {
            var results = runtime.getListings().searchListings(ListingSearch.all()).join();
            assertEquals(Set.of("alice"), results.stream().map(result -> result.seller().displayName())
                    .collect(Collectors.toSet()));
        }
    }

    private void assertInvalidPrices(Long min, Long max) throws Exception {
        try (ApplicationRuntime runtime = seeded()) {
            var search = new ListingSearch(null, null, null, min, max, null);
            assertFailure(ServiceException.Code.VALIDATION, () -> runtime.getListings().searchListings(search).join());
        }
    }

    @Test
    void searchListings_priceSorts_orderByPriceThenNewest() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            create(runtime, "Cheap old", Category.OTHER, 100, Condition.GOOD);
            create(runtime, "Dear", Category.OTHER, 900, Condition.GOOD);
            create(runtime, "Cheap new", Category.OTHER, 100, Condition.GOOD);
            registerAndLogin(runtime, "bobby");
            assertEquals(List.of("Cheap new", "Cheap old", "Dear"),
                    search(runtime, new ListingSearch(null, null, null, null, null, ListingSort.PRICE_LOW_TO_HIGH)));
            assertEquals(List.of("Dear", "Cheap new", "Cheap old"),
                    search(runtime, new ListingSearch(null, null, null, null, null, ListingSort.PRICE_HIGH_TO_LOW)));
        }
    }

    @Test
    void searchListings_nullSearch_reportsValidation() throws Exception {
        try (ApplicationRuntime runtime = seeded()) {
            assertFailure(ServiceException.Code.VALIDATION, () -> runtime.getListings().searchListings(null).join());
        }
    }

    @Test
    void searchListings_loggedOut_rejectsAccess() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            assertFailure(ServiceException.Code.SESSION,
                    () -> runtime.getListings().searchListings(ListingSearch.all()).join());
        }
    }

    /** Alice lists three items at 100, 200 and 300 cents; Bob is logged in to search. */
    private ApplicationRuntime seeded() throws Exception {
        ApplicationRuntime runtime = open();
        registerAndLogin(runtime, "alice");
        create(runtime, "Wooden chairs", Category.FURNITURE, 100, Condition.NEW);
        create(runtime, "Ärger textbook", Category.BOOKS, 200, Condition.GOOD);
        create(runtime, "Old phone", Category.ELECTRONICS, 300, Condition.POOR);
        registerAndLogin(runtime, "bobby");
        return runtime;
    }

    private UUID create(ApplicationRuntime runtime, String title, Category category, long price, Condition condition) {
        clock.advanceSeconds(60);
        var draft = new ListingDraft(title, "Plain description", category, price, condition, "Campus");
        return runtime.getListings().createListing(draft, List.of()).join().listing().getId();
    }

    private static List<String> search(ApplicationRuntime runtime, ListingSearch search) {
        return titles(runtime.getListings().searchListings(search).join());
    }

    private static ListingSearch byTitle(String text) {
        return new ListingSearch(text, null, null, null, null, null);
    }

    private ApplicationRuntime open() throws Exception {
        return ApplicationRuntime.open(directory, clock);
    }
}
