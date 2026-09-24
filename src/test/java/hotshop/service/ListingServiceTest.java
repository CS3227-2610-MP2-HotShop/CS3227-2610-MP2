package hotshop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import hotshop.ApplicationRuntime;
import hotshop.model.Category;
import hotshop.model.Condition;
import hotshop.model.ListingStatus;

class ListingServiceTest {
    private static final String PASSWORD = "Sample1!";
    private static final Instant START = Instant.parse("2026-09-24T00:00:00Z");

    @TempDir
    Path directory;

    private final TestClock clock = new TestClock(START);

    @Test
    void createListing_validDraft_savesAvailableListingWithSellerProfile() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID alice = registerAndLogin(runtime, "alice");
            var created = runtime.getListings().createListing(draft(" Chairs ", 5000), List.of()).join();
            assertEquals(alice, created.listing().getSellerId());
            assertEquals("Chairs", created.listing().getDetails().title());
            assertEquals(ListingStatus.AVAILABLE, created.listing().getStatus());
            assertEquals(START, created.listing().getCreatedAt());
            assertEquals(START, created.listing().getUpdatedAt());
            assertEquals(new PublicProfile(alice, "alice", java.util.Optional.empty()), created.seller());
        }
    }

    static Stream<ListingDraft> invalidDrafts() {
        return Stream.of(
                new ListingDraft(" ", "Description", Category.OTHER, 100, Condition.NEW, "Campus"),
                new ListingDraft("Title", "Description", Category.OTHER, 0, Condition.NEW, "Campus"),
                new ListingDraft("Title", "Description", Category.OTHER, 100_000_001, Condition.NEW, "Campus"),
                new ListingDraft("Title", "Description", null, 100, Condition.NEW, "Campus"),
                new ListingDraft("Title", "Description", Category.OTHER, 100, null, "Campus"),
                new ListingDraft("Title", "Description", Category.OTHER, 100, Condition.NEW, null));
    }

    @ParameterizedTest
    @MethodSource("invalidDrafts")
    void createListing_invalidDraft_reportsValidationWithoutSaving(ListingDraft invalid) throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            assertFailure(ServiceException.Code.VALIDATION,
                    () -> runtime.getListings().createListing(invalid, List.of()).join());
            assertEquals(List.of(), runtime.getListings().getMyListings().join());
        }
    }

    @Test
    void createListing_nullDraft_reportsValidation() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            assertFailure(ServiceException.Code.VALIDATION,
                    () -> runtime.getListings().createListing(null, List.of()).join());
        }
    }

    @Test
    void createListing_reopenedDatabase_preservesListing() throws Exception {
        UUID id;
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            id = runtime.getListings().createListing(draft("Chairs", 5000), List.of()).join().listing().getId();
        }
        try (ApplicationRuntime runtime = open()) {
            runtime.getAccounts().login("alice", PASSWORD).join();
            var restored = runtime.getListings().getListing(id).join().listing();
            assertEquals("Chairs", restored.getDetails().title());
            assertEquals(5000, restored.getDetails().priceCents());
            assertEquals(Category.FURNITURE, restored.getDetails().category());
            assertEquals(Condition.GOOD, restored.getDetails().condition());
            assertEquals("Campus", restored.getDetails().pickupLocation());
            assertEquals(START, restored.getCreatedAt());
        }
    }

    @Test
    void getMyListings_twoSellers_returnsOnlyOwnListingsNewestFirst() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            var listings = runtime.getListings();
            registerAndLogin(runtime, "alice");
            listings.createListing(draft("Older", 100), List.of()).join();
            clock.advanceSeconds(60);
            listings.createListing(draft("Newer", 100), List.of()).join();
            registerAndLogin(runtime, "bobby");
            listings.createListing(draft("Bob item", 100), List.of()).join();
            runtime.getAccounts().logout().join();
            runtime.getAccounts().login("alice", PASSWORD).join();
            var mine = listings.getMyListings().join();
            assertEquals(List.of("Newer", "Older"), titles(mine));
            assertEquals("alice", mine.get(0).seller().displayName());
        }
    }

    @Test
    void getListing_otherUsersListing_returnsListingWithSeller() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID alice = registerAndLogin(runtime, "alice");
            UUID id = runtime.getListings().createListing(draft("Chairs", 5000), List.of()).join().listing().getId();
            registerAndLogin(runtime, "bobby");
            var found = runtime.getListings().getListing(id).join();
            assertEquals(id, found.listing().getId());
            assertEquals(alice, found.seller().id());
        }
    }

    @Test
    void getListing_unknownId_reportsNotFound() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            assertFailure(ServiceException.Code.NOT_FOUND,
                    () -> runtime.getListings().getListing(UUID.randomUUID()).join());
        }
    }

    @Test
    void listingOperations_loggedOut_rejectAccess() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            var listings = runtime.getListings();
            UUID id = UUID.randomUUID();
            assertFailure(ServiceException.Code.SESSION, () -> listings.createListing(draft("A", 1), List.of()).join());
            assertFailure(ServiceException.Code.SESSION, () -> listings.getMyListings().join());
            assertFailure(ServiceException.Code.SESSION, () -> listings.getListing(id).join());
            assertFailure(ServiceException.Code.SESSION,
                    () -> listings.updateListing(id, draft("A", 1), List.of()).join());
            assertFailure(ServiceException.Code.SESSION, () -> listings.archiveListing(id).join());
            assertFailure(ServiceException.Code.SESSION, () -> listings.deleteListing(id).join());
        }
    }

    @Test
    void updateListing_ownAvailableListing_savesChangesAndAdvancesUpdatedAt() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            UUID id = create(runtime, "Chairs");
            clock.advanceSeconds(60);
            var updated = runtime.getListings().updateListing(id, draft("Chair", 2500), List.of()).join();
            assertEquals("Chair", updated.listing().getDetails().title());
            assertEquals(START, updated.listing().getCreatedAt());
            assertEquals(START.plusSeconds(60), updated.listing().getUpdatedAt());
            assertEquals(2500, runtime.getListings().getListing(id).join().listing().getDetails().priceCents());
        }
    }

    @Test
    void updateListing_unchangedValues_keepsUpdatedAt() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            UUID id = create(runtime, "Chairs");
            clock.advanceSeconds(60);
            runtime.getListings().updateListing(id, draft("Chairs", 5000), List.of()).join();
            assertEquals(START, runtime.getListings().getListing(id).join().listing().getUpdatedAt());
        }
    }

    @Test
    void updateListing_otherUsersListing_reportsPermissionWithoutChanges() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            UUID id = create(runtime, "Chairs");
            registerAndLogin(runtime, "bobby");
            assertFailure(ServiceException.Code.PERMISSION,
                    () -> runtime.getListings().updateListing(id, draft("Mine now", 1), List.of()).join());
            assertEquals("Chairs", runtime.getListings().getListing(id).join().listing().getDetails().title());
        }
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"RESERVED", "SOLD", "ARCHIVED"})
    void updateListing_unavailableListing_reportsInvalidState(ListingStatus status) throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            UUID id = create(runtime, "Chairs");
            setStatus(directory, id, status);
            assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> runtime.getListings().updateListing(id, draft("Chair", 1), List.of()).join());
            assertEquals("Chairs", runtime.getListings().getListing(id).join().listing().getDetails().title());
        }
    }

    @Test
    void updateListing_invalidDraft_reportsValidationWithoutChanges() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            UUID id = create(runtime, "Chairs");
            assertFailure(ServiceException.Code.VALIDATION,
                    () -> runtime.getListings().updateListing(id, draft(" ", 1), List.of()).join());
            assertEquals("Chairs", runtime.getListings().getListing(id).join().listing().getDetails().title());
        }
    }

    @Test
    void mutations_unknownListing_reportNotFound() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            var listings = runtime.getListings();
            UUID id = UUID.randomUUID();
            assertFailure(ServiceException.Code.NOT_FOUND,
                    () -> listings.updateListing(id, draft("A", 1), List.of()).join());
            assertFailure(ServiceException.Code.NOT_FOUND, () -> listings.archiveListing(id).join());
            assertFailure(ServiceException.Code.NOT_FOUND, () -> listings.deleteListing(id).join());
        }
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"AVAILABLE", "SOLD"})
    void archiveListing_availableOrSold_archivesAndKeepsListingVisible(ListingStatus status) throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            UUID id = create(runtime, "Chairs");
            setStatus(directory, id, status);
            assertEquals(ListingStatus.ARCHIVED, runtime.getListings().archiveListing(id).join().listing().getStatus());
            assertEquals(List.of("Chairs"), titles(runtime.getListings().getMyListings().join()));
            registerAndLogin(runtime, "bobby");
            assertEquals(ListingStatus.ARCHIVED, runtime.getListings().getListing(id).join().listing().getStatus());
        }
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"RESERVED", "ARCHIVED"})
    void archiveListing_reservedOrArchived_reportsInvalidState(ListingStatus status) throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            UUID id = create(runtime, "Chairs");
            setStatus(directory, id, status);
            assertFailure(ServiceException.Code.INVALID_STATE, () -> runtime.getListings().archiveListing(id).join());
            assertEquals(status, runtime.getListings().getListing(id).join().listing().getStatus());
        }
    }

    @Test
    void archiveListing_otherUsersListing_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            UUID id = create(runtime, "Chairs");
            registerAndLogin(runtime, "bobby");
            assertFailure(ServiceException.Code.PERMISSION, () -> runtime.getListings().archiveListing(id).join());
            assertEquals(ListingStatus.AVAILABLE, runtime.getListings().getListing(id).join().listing().getStatus());
        }
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"AVAILABLE", "ARCHIVED"})
    void deleteListing_availableOrArchived_removesListing(ListingStatus status) throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            UUID id = create(runtime, "Chairs");
            setStatus(directory, id, status);
            runtime.getListings().deleteListing(id).join();
            assertFailure(ServiceException.Code.NOT_FOUND, () -> runtime.getListings().getListing(id).join());
            assertEquals(List.of(), runtime.getListings().getMyListings().join());
        }
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"RESERVED", "SOLD"})
    void deleteListing_reservedOrSold_reportsInvalidState(ListingStatus status) throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            UUID id = create(runtime, "Chairs");
            setStatus(directory, id, status);
            assertFailure(ServiceException.Code.INVALID_STATE, () -> runtime.getListings().deleteListing(id).join());
            assertEquals(status, runtime.getListings().getListing(id).join().listing().getStatus());
        }
    }

    @Test
    void deleteListing_otherUsersListing_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            UUID id = create(runtime, "Chairs");
            registerAndLogin(runtime, "bobby");
            assertFailure(ServiceException.Code.PERMISSION, () -> runtime.getListings().deleteListing(id).join());
            assertEquals(id, runtime.getListings().getListing(id).join().listing().getId());
        }
    }

    private UUID create(ApplicationRuntime runtime, String title) {
        return runtime.getListings().createListing(draft(title, 5000), List.of()).join().listing().getId();
    }

    /** Stands in for OfferService and TransactionService, which will own these transitions. */
    static void setStatus(Path data, UUID id, ListingStatus status) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + data.resolve("marketplace.db"));
                var statement = connection.prepareStatement("UPDATE listings SET status = ? WHERE id = ?")) {
            statement.setString(1, status.name());
            statement.setString(2, id.toString());
            assertEquals(1, statement.executeUpdate());
        }
    }

    private ApplicationRuntime open() throws Exception {
        return ApplicationRuntime.open(directory, clock);
    }

    static ListingDraft draft(String title, long price) {
        return new ListingDraft(title, "Three wooden chairs", Category.FURNITURE, price, Condition.GOOD, "Campus");
    }

    static UUID registerAndLogin(ApplicationRuntime runtime, String username) {
        var accounts = runtime.getAccounts();
        accounts.logout().join();
        UUID id = accounts.register(username, PASSWORD, username).join().getId();
        accounts.login(username, PASSWORD).join();
        return id;
    }

    static List<String> titles(List<ListingWithSeller> results) {
        return results.stream().map(result -> result.listing().getDetails().title()).toList();
    }

    static void assertFailure(ServiceException.Code code, Runnable action) {
        CompletionException failure = assertThrows(CompletionException.class, action::run);
        assertEquals(code, ((ServiceException) failure.getCause()).getCode());
    }
}
