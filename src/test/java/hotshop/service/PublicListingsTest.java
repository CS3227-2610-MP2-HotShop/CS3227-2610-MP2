package hotshop.service;

import static hotshop.service.ListingServiceTest.assertFailure;
import static hotshop.service.ListingServiceTest.draft;
import static hotshop.service.ListingServiceTest.registerAndLogin;
import static hotshop.service.ListingServiceTest.titles;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hotshop.ApplicationRuntime;

class PublicListingsTest {
    @TempDir
    Path directory;

    @Test
    void getPublicListings_loggedOut_rejectsAccess() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            var seller = registerAndLogin(runtime, "seller");
            runtime.getAccounts().logout().join();
            assertFailure(ServiceException.Code.SESSION,
                    () -> runtime.getListings().getPublicListings(seller).join());
        }
    }

    @Test
    void getPublicListings_unknownSeller_reportsNotFound() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            registerAndLogin(runtime, "viewer");
            assertFailure(ServiceException.Code.NOT_FOUND,
                    () -> runtime.getListings().getPublicListings(UUID.randomUUID()).join());
        }
    }

    @Test
    void getPublicListings_nullSeller_reportsValidation() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            registerAndLogin(runtime, "viewer");
            assertFailure(ServiceException.Code.VALIDATION,
                    () -> runtime.getListings().getPublicListings(null).join());
        }
    }

    @Test
    void getPublicListings_sellerWithNoListings_returnsEmpty() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            UUID seller = registerAndLogin(runtime, "seller");
            assertEquals(List.of(), runtime.getListings().getPublicListings(seller).join());
        }
    }

    @Test
    void getPublicListings_reservedAndSold_excludesBothStates() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            UUID seller = registerAndLogin(runtime, "seller");
            var listing = runtime.getListings().createListing(draft("Desk", 100), List.of()).join().listing();
            registerAndLogin(runtime, "buyer");
            var offer = runtime.getOffers().submitOffer(listing.getId(), 100).join().offer();
            ListingServiceTest.loginAs(runtime, "seller");
            var sale = runtime.getOffers().acceptOffer(offer.getId()).join();
            assertEquals(List.of(), runtime.getListings().getPublicListings(seller).join());
            runtime.getTransactions().confirmCompletion(sale.transactionId()).join();
            ListingServiceTest.loginAs(runtime, "buyer");
            runtime.getTransactions().confirmCompletion(sale.transactionId()).join();
            assertEquals(List.of(), runtime.getListings().getPublicListings(seller).join());
        }
    }

    @Test
    void getPublicListings_selectedSeller_returnsOnlyAvailableListingsNewestFirst() throws Exception {
        TestClock clock = new TestClock(Instant.parse("2026-09-24T00:00:00Z"));
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory, clock)) {
            var seller = registerAndLogin(runtime, "seller");
            var listings = runtime.getListings();
            listings.createListing(draft("Older", 100), List.of()).join();
            clock.advanceSeconds(60);
            listings.createListing(draft("Newer", 100), List.of()).join();
            var archived = listings.createListing(draft("Archived", 100), List.of()).join().listing();
            listings.archiveListing(archived.getId()).join();
            registerAndLogin(runtime, "buyer");
            listings.createListing(draft("Other seller", 100), List.of()).join();
            assertEquals(List.of("Newer", "Older"), titles(listings.getPublicListings(seller).join()));
        }
    }
}
