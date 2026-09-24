package hotshop.service;

import static hotshop.service.ListingServiceTest.assertFailure;
import static hotshop.service.ListingServiceTest.draft;
import static hotshop.service.ListingServiceTest.loginAs;
import static hotshop.service.ListingServiceTest.registerAndLogin;
import static hotshop.service.ListingServiceTest.setStatus;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import hotshop.ApplicationRuntime;
import hotshop.model.ListingDetails;
import hotshop.model.ListingStatus;
import hotshop.model.OfferStatus;
import hotshop.model.TransactionStatus;

class OfferServiceTest {
    private static final String PASSWORD = "Sample1!";
    private static final Instant START = Instant.parse("2026-09-24T00:00:00Z");

    @TempDir
    Path directory;

    private final TestClock clock = new TestClock(START);

    @Test
    void submitOffer_availableListing_savesPendingOfferWithListing() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = sellerListing(runtime);
            UUID bob = registerAndLogin(runtime, "bobby");
            var result = runtime.getOffers().submitOffer(listing, 4500).join();
            assertEquals(bob, result.offer().getBuyerId());
            assertEquals(4500, result.offer().getAmountCents());
            assertEquals(OfferStatus.PENDING, result.offer().getStatus());
            assertEquals(START, result.offer().getCreatedAt());
            assertEquals(listing, result.listing().listing().getId());
            assertEquals("alice", result.listing().seller().displayName());
            assertTrue(result.saleStatus().isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {1, ListingDetails.MAX_PRICE_CENTS})
    void submitOffer_boundaryAmount_savesOffer(long amount) throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = sellerListing(runtime);
            registerAndLogin(runtime, "bobby");
            assertEquals(amount, runtime.getOffers().submitOffer(listing, amount).join().offer().getAmountCents());
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, ListingDetails.MAX_PRICE_CENTS + 1})
    void submitOffer_amountOutsideRange_reportsValidationWithLimits(long amount) throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = sellerListing(runtime);
            registerAndLogin(runtime, "bobby");
            var failure = assertFailure(ServiceException.Code.VALIDATION,
                    () -> runtime.getOffers().submitOffer(listing, amount).join());
            assertTrue(failure.getMessage().contains("S$1,000,000.00"), failure.getMessage());
            assertEquals(List.of(), runtime.getOffers().getMyOffers().join());
        }
    }

    @Test
    void submitOffer_ownListing_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = sellerListing(runtime);
            assertFailure(ServiceException.Code.PERMISSION, () -> runtime.getOffers().submitOffer(listing, 100).join());
        }
    }

    @Test
    void submitOffer_unknownListing_reportsNotFound() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "bobby");
            assertFailure(ServiceException.Code.NOT_FOUND,
                    () -> runtime.getOffers().submitOffer(UUID.randomUUID(), 100).join());
        }
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = {"RESERVED", "SOLD", "ARCHIVED"})
    void submitOffer_unavailableListing_reportsInvalidStateNamingStatus(ListingStatus status) throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = sellerListing(runtime);
            setStatus(directory, listing, status);
            registerAndLogin(runtime, "bobby");
            var failure = assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> runtime.getOffers().submitOffer(listing, 100).join());
            assertTrue(failure.getMessage().contains(status.name().toLowerCase()), failure.getMessage());
        }
    }

    @Test
    void submitOffer_existingPendingOffer_reportsInvalidStateWithAmount() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = sellerListing(runtime);
            registerAndLogin(runtime, "bobby");
            runtime.getOffers().submitOffer(listing, 4000).join();
            var failure = assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> runtime.getOffers().submitOffer(listing, 4500).join());
            assertTrue(failure.getMessage().contains("S$40.00"), failure.getMessage());
        }
    }

    @Test
    void submitOffer_afterWithdrawal_savesNewOfferAndKeepsHistory() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = sellerListing(runtime);
            registerAndLogin(runtime, "bobby");
            var offers = runtime.getOffers();
            UUID first = offers.submitOffer(listing, 4000).join().offer().getId();
            offers.withdrawOffer(first).join();
            clock.advanceSeconds(60);
            offers.submitOffer(listing, 4500).join();
            assertEquals(List.of(4500L, 4000L), amounts(offers.getMyOffers().join()));
        }
    }

    @Test
    void withdrawOffer_ownPendingOffer_withdrawsAndRecordsTime() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = sellerListing(runtime);
            registerAndLogin(runtime, "bobby");
            UUID offer = runtime.getOffers().submitOffer(listing, 4000).join().offer().getId();
            clock.advanceSeconds(60);
            var withdrawn = runtime.getOffers().withdrawOffer(offer).join().offer();
            assertEquals(OfferStatus.WITHDRAWN, withdrawn.getStatus());
            assertEquals(START.plusSeconds(60), withdrawn.getClosedAt().orElseThrow());
        }
    }

    @Test
    void withdrawOffer_otherUsersOffer_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = sellerListing(runtime);
            registerAndLogin(runtime, "bobby");
            UUID offer = runtime.getOffers().submitOffer(listing, 4000).join().offer().getId();
            registerAndLogin(runtime, "carol");
            assertFailure(ServiceException.Code.PERMISSION, () -> runtime.getOffers().withdrawOffer(offer).join());
        }
    }

    @Test
    void withdrawOffer_withdrawnOffer_reportsInvalidState() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = sellerListing(runtime);
            registerAndLogin(runtime, "bobby");
            UUID offer = runtime.getOffers().submitOffer(listing, 4000).join().offer().getId();
            runtime.getOffers().withdrawOffer(offer).join();
            var failure = assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> runtime.getOffers().withdrawOffer(offer).join());
            assertTrue(failure.getMessage().contains("withdrawn"), failure.getMessage());
        }
    }

    @Test
    void withdrawOffer_unknownOffer_reportsNotFound() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "bobby");
            assertFailure(ServiceException.Code.NOT_FOUND,
                    () -> runtime.getOffers().withdrawOffer(UUID.randomUUID()).join());
        }
    }

    @Test
    void getMyOffers_twoBuyers_returnsOnlyOwnOffersNewestFirst() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID first = sellerListing(runtime);
            UUID second = runtime.getListings().createListing(draft("Desk", 9000), List.of()).join()
                    .listing().getId();
            registerAndLogin(runtime, "carol");
            runtime.getOffers().submitOffer(first, 1).join();
            registerAndLogin(runtime, "bobby");
            runtime.getOffers().submitOffer(first, 4000).join();
            clock.advanceSeconds(60);
            runtime.getOffers().submitOffer(second, 8000).join();
            var mine = runtime.getOffers().getMyOffers().join();
            assertEquals(List.of(8000L, 4000L), amounts(mine));
            assertEquals("Desk", mine.get(0).listing().listing().getDetails().title());
        }
    }

    @Test
    void submitOffer_reopenedDatabase_preservesOffer() throws Exception {
        UUID listing;
        try (ApplicationRuntime runtime = open()) {
            listing = sellerListing(runtime);
            registerAndLogin(runtime, "bobby");
            runtime.getOffers().submitOffer(listing, 4000).join();
        }
        try (ApplicationRuntime runtime = open()) {
            runtime.getAccounts().login("bobby", PASSWORD).join();
            var restored = runtime.getOffers().getMyOffers().join().get(0).offer();
            assertEquals(4000, restored.getAmountCents());
            assertEquals(listing, restored.getListingId());
            assertEquals(START, restored.getCreatedAt());
        }
    }

    @Test
    void offerOperations_loggedOut_rejectAccess() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            var offers = runtime.getOffers();
            UUID id = UUID.randomUUID();
            assertFailure(ServiceException.Code.SESSION, () -> offers.submitOffer(id, 100).join());
            assertFailure(ServiceException.Code.SESSION, () -> offers.withdrawOffer(id).join());
            assertFailure(ServiceException.Code.SESSION, () -> offers.getMyOffers().join());
        }
    }

    @Test
    void acceptOffer_pendingOffer_reservesListingRejectsOthersAndSavesSale() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = sellerListing(runtime);
            UUID bobOffer = offerAs(runtime, "bobby", listing, 4000);
            offerAs(runtime, "carol", listing, 4500);
            loginAs(runtime, "alice");
            clock.advanceSeconds(60);
            Instant acceptedAt = clock.instant();
            var accepted = runtime.getOffers().acceptOffer(bobOffer).join();
            assertEquals(OfferStatus.ACCEPTED, accepted.offer().getStatus());
            assertEquals(acceptedAt, accepted.offer().getClosedAt().orElseThrow());
            assertEquals(ListingStatus.RESERVED, accepted.listing().getStatus());
            loginAs(runtime, "carol");
            assertEquals(OfferStatus.REJECTED, runtime.getOffers().getMyOffers().join().get(0).offer().getStatus());
            loginAs(runtime, "bobby");
            var mine = runtime.getOffers().getMyOffers().join().get(0);
            assertEquals(TransactionStatus.ACTIVE, mine.saleStatus().orElseThrow());
            assertEquals(ListingStatus.RESERVED, mine.listing().listing().getStatus());
        }
    }

    @Test
    void acceptOffer_buyer_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = sellerListing(runtime);
            UUID offer = offerAs(runtime, "bobby", listing, 4000);
            var failure = assertFailure(ServiceException.Code.PERMISSION,
                    () -> runtime.getOffers().acceptOffer(offer).join());
            assertTrue(failure.getMessage().contains("seller"), failure.getMessage());
        }
    }

    @Test
    void acceptOffer_otherUser_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = sellerListing(runtime);
            UUID offer = offerAs(runtime, "bobby", listing, 4000);
            registerAndLogin(runtime, "carol");
            assertFailure(ServiceException.Code.PERMISSION, () -> runtime.getOffers().acceptOffer(offer).join());
        }
    }

    @Test
    void acceptOffer_listingNoLongerAvailable_reportsInvalidStateNamingStatus() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = sellerListing(runtime);
            UUID offer = offerAs(runtime, "bobby", listing, 4000);
            loginAs(runtime, "alice");
            setStatus(directory, listing, ListingStatus.SOLD);
            var failure = assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> runtime.getOffers().acceptOffer(offer).join());
            assertTrue(failure.getMessage().contains("sold"), failure.getMessage());
        }
    }

    @Test
    void sellerOperations_unknownIds_reportNotFound() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            sellerListing(runtime);
            var offers = runtime.getOffers();
            UUID unknown = UUID.randomUUID();
            assertFailure(ServiceException.Code.NOT_FOUND, () -> offers.acceptOffer(unknown).join());
            assertFailure(ServiceException.Code.NOT_FOUND, () -> offers.rejectOffer(unknown).join());
            assertFailure(ServiceException.Code.NOT_FOUND, () -> offers.getOffersForListing(unknown).join());
        }
    }

    @Test
    void acceptOffer_withdrawnOffer_reportsInvalidState() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = sellerListing(runtime);
            UUID offer = offerAs(runtime, "bobby", listing, 4000);
            runtime.getOffers().withdrawOffer(offer).join();
            loginAs(runtime, "alice");
            var failure = assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> runtime.getOffers().acceptOffer(offer).join());
            assertTrue(failure.getMessage().contains("withdrawn"), failure.getMessage());
        }
    }

    @Test
    void acceptOffer_saleSaveFailure_rollsBackListingAndOffers() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = sellerListing(runtime);
            UUID bobOffer = offerAs(runtime, "bobby", listing, 4000);
            offerAs(runtime, "carol", listing, 4500);
            loginAs(runtime, "alice");
            sql("CREATE TRIGGER fail_sale BEFORE INSERT ON transactions "
                    + "BEGIN SELECT RAISE(ABORT, 'simulated failure'); END");
            assertFailure(ServiceException.Code.STORAGE, () -> runtime.getOffers().acceptOffer(bobOffer).join());
            assertEquals(ListingStatus.AVAILABLE, listingStatus(runtime, listing));
            assertEquals(List.of(OfferStatus.PENDING, OfferStatus.PENDING),
                    statuses(runtime.getOffers().getOffersForListing(listing).join()));
        }
    }

    @Test
    void rejectOffer_pendingOffer_rejectsOnlyThatOffer() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = sellerListing(runtime);
            UUID bobOffer = offerAs(runtime, "bobby", listing, 4000);
            offerAs(runtime, "carol", listing, 4500);
            loginAs(runtime, "alice");
            var rejected = runtime.getOffers().rejectOffer(bobOffer).join();
            assertEquals(OfferStatus.REJECTED, rejected.offer().getStatus());
            assertEquals("bobby", rejected.buyer().displayName());
            assertEquals(ListingStatus.AVAILABLE, listingStatus(runtime, listing));
            assertEquals(List.of(OfferStatus.PENDING, OfferStatus.REJECTED),
                    statuses(runtime.getOffers().getOffersForListing(listing).join()));
        }
    }

    @Test
    void rejectOffer_buyer_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = sellerListing(runtime);
            UUID offer = offerAs(runtime, "bobby", listing, 4000);
            assertFailure(ServiceException.Code.PERMISSION, () -> runtime.getOffers().rejectOffer(offer).join());
        }
    }

    @Test
    void rejectOffer_rejectedOffer_reportsInvalidState() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = sellerListing(runtime);
            UUID offer = offerAs(runtime, "bobby", listing, 4000);
            loginAs(runtime, "alice");
            runtime.getOffers().rejectOffer(offer).join();
            assertFailure(ServiceException.Code.INVALID_STATE, () -> runtime.getOffers().rejectOffer(offer).join());
        }
    }

    @Test
    void getOffersForListing_acceptedOffer_listsItFirstThenNewest() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = sellerListing(runtime);
            UUID bobOffer = offerAs(runtime, "bobby", listing, 4000);
            offerAs(runtime, "carol", listing, 4500);
            offerAs(runtime, "david", listing, 4200);
            loginAs(runtime, "alice");
            runtime.getOffers().acceptOffer(bobOffer).join();
            var results = runtime.getOffers().getOffersForListing(listing).join();
            assertEquals(List.of("bobby", "david", "carol"), buyers(results));
            assertEquals(TransactionStatus.ACTIVE, results.get(0).saleStatus().orElseThrow());
            assertTrue(results.get(1).saleStatus().isEmpty());
        }
    }

    @Test
    void getOffersForListing_cancelledEarlierSale_listsLiveSaleThenCancelledSale() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = sellerListing(runtime);
            UUID bobOffer = offerAs(runtime, "bobby", listing, 4000);
            loginAs(runtime, "alice");
            runtime.getOffers().acceptOffer(bobOffer).join();
            sql("UPDATE transactions SET status = 'CANCELLED'");
            setStatus(directory, listing, ListingStatus.AVAILABLE);
            UUID carolOffer = offerAs(runtime, "carol", listing, 3000);
            offerAs(runtime, "david", listing, 2000);
            loginAs(runtime, "alice");
            runtime.getOffers().acceptOffer(carolOffer).join();
            var results = runtime.getOffers().getOffersForListing(listing).join();
            assertEquals(List.of("carol", "bobby", "david"), buyers(results));
            assertEquals(TransactionStatus.ACTIVE, results.get(0).saleStatus().orElseThrow());
            assertEquals(TransactionStatus.CANCELLED, results.get(1).saleStatus().orElseThrow());
        }
    }

    @Test
    void getOffersForListing_buyer_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = sellerListing(runtime);
            offerAs(runtime, "bobby", listing, 4000);
            assertFailure(ServiceException.Code.PERMISSION,
                    () -> runtime.getOffers().getOffersForListing(listing).join());
        }
    }

    @Test
    void sellerOperations_loggedOut_rejectAccess() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            var offers = runtime.getOffers();
            UUID id = UUID.randomUUID();
            assertFailure(ServiceException.Code.SESSION, () -> offers.getOffersForListing(id).join());
            assertFailure(ServiceException.Code.SESSION, () -> offers.acceptOffer(id).join());
            assertFailure(ServiceException.Code.SESSION, () -> offers.rejectOffer(id).join());
        }
    }

    /** Registers a buyer who makes one offer one minute after the previous event, and stays logged in. */
    private UUID offerAs(ApplicationRuntime runtime, String buyer, UUID listing, long amount) {
        clock.advanceSeconds(60);
        registerAndLogin(runtime, buyer);
        return runtime.getOffers().submitOffer(listing, amount).join().offer().getId();
    }

    private void sql(String statement) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("marketplace.db"));
                var sql = connection.createStatement()) {
            sql.execute(statement);
        }
    }

    private static ListingStatus listingStatus(ApplicationRuntime runtime, UUID listing) {
        return runtime.getListings().getListing(listing).join().listing().getStatus();
    }

    private static List<OfferStatus> statuses(List<OfferWithBuyer> results) {
        return results.stream().map(result -> result.offer().getStatus()).toList();
    }

    private static List<String> buyers(List<OfferWithBuyer> results) {
        return results.stream().map(result -> result.buyer().displayName()).toList();
    }

    /** Registers alice, logs her in, and returns the ID of her new listing. */
    private UUID sellerListing(ApplicationRuntime runtime) {
        registerAndLogin(runtime, "alice");
        return runtime.getListings().createListing(draft("Chairs", 5000), List.of()).join().listing().getId();
    }

    private static List<Long> amounts(List<OfferWithListing> results) {
        return results.stream().map(result -> result.offer().getAmountCents()).toList();
    }

    private ApplicationRuntime open() throws Exception {
        return ApplicationRuntime.open(directory, clock);
    }
}
