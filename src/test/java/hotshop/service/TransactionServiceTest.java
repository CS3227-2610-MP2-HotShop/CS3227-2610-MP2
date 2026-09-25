package hotshop.service;

import static hotshop.service.ListingServiceTest.assertFailure;
import static hotshop.service.ListingServiceTest.draft;
import static hotshop.service.ListingServiceTest.loginAs;
import static hotshop.service.ListingServiceTest.registerAndLogin;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hotshop.ApplicationRuntime;
import hotshop.model.ListingStatus;
import hotshop.model.OfferStatus;
import hotshop.model.TransactionStatus;

class TransactionServiceTest {
    private static final String PASSWORD = "Sample1!";
    private static final Instant START = Instant.parse("2026-09-24T00:00:00Z");

    @TempDir
    Path directory;

    private final TestClock clock = new TestClock(START);
    private UUID listing;

    @Test
    void getMySales_newSale_showsHandoverStepAndBothEarlyActions() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "bobby", 4000);
            var mine = runtime.getTransactions().getMySales().join();
            assertEquals(1, mine.size());
            var entry = mine.get(0);
            assertEquals(sale, entry.sale().getId());
            assertEquals(SaleRole.SELLER, entry.role());
            assertEquals("bobby", entry.otherParticipant().displayName());
            assertEquals(4000, entry.sale().getAgreedPriceCents());
            assertEquals("Chairs", entry.sale().getListingTitle());
            assertEquals(NextStep.OFFER_MEETUP_TIMES, entry.nextStep());
            assertEquals(Set.of(SaleAction.CONFIRM_COMPLETION, SaleAction.CANCEL_SALE), entry.availableActions());
        }
    }

    @Test
    void confirmCompletion_bothParticipants_completesSaleAndMarksListingSold() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "bobby", 4000);
            runtime.getTransactions().confirmCompletion(sale).join();
            loginAs(runtime, "bobby");
            clock.advanceSeconds(60);
            var completed = runtime.getTransactions().confirmCompletion(sale).join();
            assertEquals(TransactionStatus.COMPLETED, completed.sale().getStatus());
            assertEquals(NextStep.NONE, completed.nextStep());
            assertEquals(Set.of(), completed.availableActions());
            assertEquals(ListingStatus.SOLD, listingStatus(runtime));
        }
    }

    @Test
    void confirmCompletion_firstParticipant_keepsSaleActiveAndShowsWaitingSteps() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "bobby", 4000);
            var seller = runtime.getTransactions().confirmCompletion(sale).join();
            assertEquals(TransactionStatus.ACTIVE, seller.sale().getStatus());
            assertEquals(NextStep.WAIT_FOR_CONFIRMATION, seller.nextStep());
            assertEquals(Set.of(SaleAction.REQUEST_CANCELLATION), seller.availableActions());
            loginAs(runtime, "bobby");
            var buyer = runtime.getTransactions().getMyPurchases().join().get(0);
            assertEquals(SaleRole.BUYER, buyer.role());
            assertEquals(NextStep.WAIT_FOR_MEETUP_TIMES, buyer.nextStep());
            assertEquals(Set.of(SaleAction.CONFIRM_COMPLETION, SaleAction.REQUEST_CANCELLATION),
                    buyer.availableActions());
            assertEquals(ListingStatus.RESERVED, listingStatus(runtime));
        }
    }

    @Test
    void confirmCompletion_sameParticipantTwice_reportsInvalidState() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "bobby", 4000);
            runtime.getTransactions().confirmCompletion(sale).join();
            var failure = assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> runtime.getTransactions().confirmCompletion(sale).join());
            assertTrue(failure.getMessage().contains("already confirmed"), failure.getMessage());
        }
    }

    @Test
    void confirmCompletion_nonParticipant_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "bobby", 4000);
            registerAndLogin(runtime, "carol");
            assertFailure(ServiceException.Code.PERMISSION,
                    () -> runtime.getTransactions().confirmCompletion(sale).join());
        }
    }

    @Test
    void confirmCompletion_listingSaveFailure_rollsBackCompletion() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "bobby", 4000);
            runtime.getTransactions().confirmCompletion(sale).join();
            loginAs(runtime, "bobby");
            sql("CREATE TRIGGER fail_listing BEFORE UPDATE ON listings "
                    + "BEGIN SELECT RAISE(ABORT, 'simulated failure'); END");
            assertFailure(ServiceException.Code.STORAGE,
                    () -> runtime.getTransactions().confirmCompletion(sale).join());
            var purchase = runtime.getTransactions().getMyPurchases().join().get(0);
            assertEquals(TransactionStatus.ACTIVE, purchase.sale().getStatus());
            assertTrue(purchase.sale().getBuyerConfirmedAt().isEmpty());
            assertEquals(ListingStatus.RESERVED, listingStatus(runtime));
        }
    }

    @Test
    void cancelSale_noConfirmations_cancelsReleasesListingAndRecordsCanceller() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            listing = runtime.getListings().createListing(draft("Chairs", 5000), List.of()).join()
                    .listing().getId();
            UUID bobOffer = offerAs(runtime, "bobby", 4000);
            UUID rejectedOffer = offerAs(runtime, "carol", 3000);
            UUID sale = acceptAs(runtime, bobOffer);
            loginAs(runtime, "bobby");
            clock.advanceSeconds(60);
            UUID bob = runtime.getAccounts().getCurrentUserId().join().orElseThrow();
            var cancelled = runtime.getTransactions().cancelSale(sale).join();
            assertEquals(TransactionStatus.CANCELLED, cancelled.sale().getStatus());
            assertEquals(bob, cancelled.sale().getCancelledBy().orElseThrow());
            assertEquals(clock.instant(), cancelled.sale().getCancelledAt().orElseThrow());
            assertEquals(ListingStatus.AVAILABLE, listingStatus(runtime));
            loginAs(runtime, "carol");
            assertEquals(OfferStatus.REJECTED, runtime.getOffers().getMyOffers().join().stream()
                    .filter(offer -> offer.offer().getId().equals(rejectedOffer)).findFirst().orElseThrow()
                    .offer().getStatus());
            runtime.getOffers().submitOffer(listing, 3500).join();
        }
    }

    @Test
    void cancelSale_listingSaveFailure_rollsBackCancellation() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "bobby", 4000);
            sql("CREATE TRIGGER fail_listing BEFORE UPDATE ON listings "
                    + "BEGIN SELECT RAISE(ABORT, 'simulated failure'); END");
            assertFailure(ServiceException.Code.STORAGE, () -> runtime.getTransactions().cancelSale(sale).join());
            var entry = runtime.getTransactions().getMySales().join().get(0);
            assertEquals(TransactionStatus.ACTIVE, entry.sale().getStatus());
            assertTrue(entry.sale().getCancelledAt().isEmpty());
            assertEquals(ListingStatus.RESERVED, listingStatus(runtime));
        }
    }

    @Test
    void getMySales_twoActiveSales_listsNewerFirst() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID older = agreedSale(runtime, "bobby", 4000);
            listing = runtime.getListings().createListing(draft("Desk", 9000), List.of()).join().listing().getId();
            UUID newer = acceptAs(runtime, offerAs(runtime, "carol", 8000));
            assertEquals(List.of(newer, older), runtime.getTransactions().getMySales().join().stream()
                    .map(entry -> entry.sale().getId()).toList());
        }
    }

    @Test
    void cancelSale_afterConfirmation_reportsInvalidStateSuggestingRequest() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "bobby", 4000);
            runtime.getTransactions().confirmCompletion(sale).join();
            var failure = assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> runtime.getTransactions().cancelSale(sale).join());
            assertTrue(failure.getMessage().contains("request"), failure.getMessage());
            assertEquals(ListingStatus.RESERVED, listingStatus(runtime));
        }
    }

    @Test
    void cancelSale_nonParticipant_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "bobby", 4000);
            registerAndLogin(runtime, "carol");
            assertFailure(ServiceException.Code.PERMISSION, () -> runtime.getTransactions().cancelSale(sale).join());
        }
    }

    @Test
    void cancelSale_completedSale_reportsInvalidState() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "bobby", 4000);
            complete(runtime, sale);
            var failure = assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> runtime.getTransactions().cancelSale(sale).join());
            assertTrue(failure.getMessage().contains("completed"), failure.getMessage());
        }
    }

    @Test
    void getMySales_listingSoldAfterCancelledSale_listsLiveSaleBeforeCancelledOne() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID first = agreedSale(runtime, "bobby", 4000);
            runtime.getTransactions().cancelSale(first).join();
            UUID carolOffer = offerAs(runtime, "carol", 3500);
            loginAs(runtime, "alice");
            runtime.getOffers().acceptOffer(carolOffer).join();
            var sales = runtime.getTransactions().getMySales().join();
            assertEquals(List.of("carol", "bobby"), sales.stream()
                    .map(entry -> entry.otherParticipant().displayName()).toList());
            assertEquals(List.of(TransactionStatus.ACTIVE, TransactionStatus.CANCELLED), sales.stream()
                    .map(entry -> entry.sale().getStatus()).toList());
            assertEquals(NextStep.NONE, sales.get(1).nextStep());
        }
    }

    @Test
    void getMySales_mixedStatuses_ordersActiveThenCompletedThenCancelled() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID cancelled = agreedSale(runtime, "bobby", 4000);
            runtime.getTransactions().cancelSale(cancelled).join();
            UUID completed = acceptAs(runtime, offerAs(runtime, "carol", 3000));
            complete(runtime, completed);
            UUID desk = runtime.getListings().createListing(draft("Desk", 9000), List.of()).join()
                    .listing().getId();
            listing = desk;
            UUID active = acceptAs(runtime, offerAs(runtime, "david", 8000));
            loginAs(runtime, "alice");
            assertEquals(List.of(active, completed, cancelled), runtime.getTransactions().getMySales().join()
                    .stream().map(entry -> entry.sale().getId()).toList());
        }
    }

    @Test
    void getMyPurchases_buyer_listsOnlyOwnPurchasesWithSeller() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            agreedSale(runtime, "bobby", 4000);
            loginAs(runtime, "bobby");
            var purchases = runtime.getTransactions().getMyPurchases().join();
            assertEquals(1, purchases.size());
            assertEquals("alice", purchases.get(0).otherParticipant().displayName());
            assertEquals(List.of(), runtime.getTransactions().getMySales().join());
        }
    }

    @Test
    void confirmCompletion_reopenedDatabase_preservesConfirmation() throws Exception {
        UUID sale;
        try (ApplicationRuntime runtime = open()) {
            sale = agreedSale(runtime, "bobby", 4000);
            runtime.getTransactions().confirmCompletion(sale).join();
        }
        try (ApplicationRuntime runtime = open()) {
            runtime.getAccounts().login("alice", PASSWORD).join();
            var restored = runtime.getTransactions().getMySales().join().get(0).sale();
            assertEquals(sale, restored.getId());
            assertEquals(START.plusSeconds(120), restored.getSellerConfirmedAt().orElseThrow());
        }
    }

    @Test
    void saleOperations_unknownSale_reportNotFound() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            UUID unknown = UUID.randomUUID();
            assertFailure(ServiceException.Code.NOT_FOUND,
                    () -> runtime.getTransactions().confirmCompletion(unknown).join());
            assertFailure(ServiceException.Code.NOT_FOUND, () -> runtime.getTransactions().cancelSale(unknown).join());
        }
    }

    @Test
    void saleOperations_loggedOut_rejectAccess() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            var transactions = runtime.getTransactions();
            UUID id = UUID.randomUUID();
            assertFailure(ServiceException.Code.SESSION, () -> transactions.confirmCompletion(id).join());
            assertFailure(ServiceException.Code.SESSION, () -> transactions.cancelSale(id).join());
            assertFailure(ServiceException.Code.SESSION, () -> transactions.getMySales().join());
            assertFailure(ServiceException.Code.SESSION, () -> transactions.getMyPurchases().join());
        }
    }

    @Test
    void requestCancellation_afterConfirmation_blocksConfirmationAndShowsBothSides() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "bobby", 4000);
            runtime.getTransactions().confirmCompletion(sale).join();
            var requester = runtime.getTransactions().requestCancellation(sale).join();
            assertEquals(NextStep.WAIT_FOR_CANCELLATION_RESPONSE, requester.nextStep());
            assertEquals(Set.of(SaleAction.WITHDRAW_CANCELLATION), requester.availableActions());
            assertEquals(TransactionStatus.ACTIVE, requester.sale().getStatus());
            loginAs(runtime, "bobby");
            var responder = runtime.getTransactions().getMyPurchases().join().get(0);
            assertEquals(NextStep.RESPOND_TO_CANCELLATION_REQUEST, responder.nextStep());
            assertEquals(Set.of(SaleAction.ACCEPT_CANCELLATION, SaleAction.REJECT_CANCELLATION),
                    responder.availableActions());
            var failure = assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> runtime.getTransactions().confirmCompletion(sale).join());
            assertTrue(failure.getMessage().contains("cancellation request"), failure.getMessage());
        }
    }

    @Test
    void requestCancellation_beforeAnyConfirmation_reportsInvalidStateSuggestingCancel() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "bobby", 4000);
            var failure = assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> runtime.getTransactions().requestCancellation(sale).join());
            assertTrue(failure.getMessage().contains("cancel"), failure.getMessage());
        }
    }

    @Test
    void requestCancellation_existingPendingRequest_reportsInvalidState() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "bobby", 4000);
            runtime.getTransactions().confirmCompletion(sale).join();
            runtime.getTransactions().requestCancellation(sale).join();
            loginAs(runtime, "bobby");
            assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> runtime.getTransactions().requestCancellation(sale).join());
        }
    }

    @Test
    void acceptCancellation_otherParticipant_cancelsReleasesListingAndRecordsRequester() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "bobby", 4000);
            UUID alice = runtime.getAccounts().getCurrentUserId().join().orElseThrow();
            runtime.getTransactions().confirmCompletion(sale).join();
            runtime.getTransactions().requestCancellation(sale).join();
            loginAs(runtime, "bobby");
            var cancelled = runtime.getTransactions().acceptCancellation(sale).join();
            assertEquals(TransactionStatus.CANCELLED, cancelled.sale().getStatus());
            assertEquals(alice, cancelled.sale().getCancelledBy().orElseThrow());
            assertEquals(ListingStatus.AVAILABLE, listingStatus(runtime));
        }
    }

    @Test
    void acceptCancellation_requester_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "bobby", 4000);
            runtime.getTransactions().confirmCompletion(sale).join();
            runtime.getTransactions().requestCancellation(sale).join();
            var failure = assertFailure(ServiceException.Code.PERMISSION,
                    () -> runtime.getTransactions().acceptCancellation(sale).join());
            assertTrue(failure.getMessage().contains("other participant"), failure.getMessage());
        }
    }

    @Test
    void rejectCancellation_otherParticipant_keepsConfirmationAndAllowsCompletion() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "bobby", 4000);
            runtime.getTransactions().confirmCompletion(sale).join();
            runtime.getTransactions().requestCancellation(sale).join();
            loginAs(runtime, "bobby");
            var rejected = runtime.getTransactions().rejectCancellation(sale).join();
            assertTrue(rejected.sale().getSellerConfirmedAt().isPresent());
            assertEquals(NextStep.WAIT_FOR_MEETUP_TIMES, rejected.nextStep());
            runtime.getTransactions().confirmCompletion(sale).join();
            assertEquals(ListingStatus.SOLD, listingStatus(runtime));
        }
    }

    @Test
    void withdrawCancellation_requester_keepsConfirmationAndAllowsNewRequest() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "bobby", 4000);
            runtime.getTransactions().confirmCompletion(sale).join();
            runtime.getTransactions().requestCancellation(sale).join();
            var withdrawn = runtime.getTransactions().withdrawCancellation(sale).join();
            assertEquals(NextStep.WAIT_FOR_CONFIRMATION, withdrawn.nextStep());
            assertTrue(withdrawn.sale().getSellerConfirmedAt().isPresent());
            runtime.getTransactions().requestCancellation(sale).join();
            assertEquals(2, runtime.getTransactions().getMySales().join().get(0).sale()
                    .getCancellationRequests().size());
        }
    }

    @Test
    void withdrawCancellation_otherParticipant_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "bobby", 4000);
            runtime.getTransactions().confirmCompletion(sale).join();
            runtime.getTransactions().requestCancellation(sale).join();
            loginAs(runtime, "bobby");
            assertFailure(ServiceException.Code.PERMISSION,
                    () -> runtime.getTransactions().withdrawCancellation(sale).join());
        }
    }

    @Test
    void acceptCancellation_noPendingRequest_reportsInvalidState() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "bobby", 4000);
            var failure = assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> runtime.getTransactions().acceptCancellation(sale).join());
            assertTrue(failure.getMessage().contains("no pending"), failure.getMessage());
        }
    }

    @Test
    void getMySales_pendingRequest_listsThatSaleFirst() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID older = agreedSale(runtime, "bobby", 4000);
            UUID desk = runtime.getListings().createListing(draft("Desk", 9000), List.of()).join()
                    .listing().getId();
            listing = desk;
            UUID newer = acceptAs(runtime, offerAs(runtime, "carol", 8000));
            runtime.getTransactions().confirmCompletion(older).join();
            runtime.getTransactions().requestCancellation(older).join();
            assertEquals(List.of(older, newer), runtime.getTransactions().getMySales().join().stream()
                    .map(entry -> entry.sale().getId()).toList());
        }
    }

    @Test
    void requestCancellation_reopenedDatabase_preservesRequestHistory() throws Exception {
        UUID sale;
        try (ApplicationRuntime runtime = open()) {
            sale = agreedSale(runtime, "bobby", 4000);
            runtime.getTransactions().confirmCompletion(sale).join();
            runtime.getTransactions().requestCancellation(sale).join();
            runtime.getTransactions().withdrawCancellation(sale).join();
            runtime.getTransactions().requestCancellation(sale).join();
        }
        try (ApplicationRuntime runtime = open()) {
            runtime.getAccounts().login("bobby", PASSWORD).join();
            var purchase = runtime.getTransactions().getMyPurchases().join().get(0);
            assertEquals(2, purchase.sale().getCancellationRequests().size());
            assertEquals(NextStep.RESPOND_TO_CANCELLATION_REQUEST, purchase.nextStep());
        }
    }

    @Test
    void getSalesDashboard_newSeller_returnsZeros() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            registerAndLogin(runtime, "alice");
            assertEquals(new SalesDashboard(0, 0, 0, 0, 0), runtime.getTransactions().getSalesDashboard().join());
        }
    }

    @Test
    void getSalesDashboard_mixedSales_countsSalesAndTotalsCompletedOnly() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID cancelled = agreedSale(runtime, "bobby", 5000);
            runtime.getTransactions().cancelSale(cancelled).join();
            complete(runtime, acceptAs(runtime, offerAs(runtime, "carol", 3000)));
            listing = runtime.getListings().createListing(draft("Desk", 9000), List.of()).join().listing().getId();
            acceptAs(runtime, offerAs(runtime, "david", 8000));
            listing = runtime.getListings().createListing(draft("Lamp", 900), List.of()).join().listing().getId();
            offerAs(runtime, "erin", 100);
            offerAs(runtime, "frank", 200);
            loginAs(runtime, "alice");
            assertEquals(new SalesDashboard(2, 1, 1, 3000, 0), runtime.getTransactions().getSalesDashboard().join());
        }
    }

    @Test
    void getSalesDashboard_buyer_countsOnlyTheirOwnListings() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "bobby", 4000);
            complete(runtime, sale);
            loginAs(runtime, "bobby");
            assertEquals(new SalesDashboard(0, 0, 0, 0, 0), runtime.getTransactions().getSalesDashboard().join());
        }
    }

    @Test
    void getSalesDashboard_loggedOut_rejectsAccess() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            assertFailure(ServiceException.Code.SESSION, () -> runtime.getTransactions().getSalesDashboard().join());
        }
    }

    /**
     * Alice lists chairs, the buyer offers, and Alice accepts one minute later; Alice is left logged
     * in and the sale ID returned. Each step advances the clock by one minute.
     */
    private UUID agreedSale(ApplicationRuntime runtime, String buyer, long amount) {
        registerAndLogin(runtime, "alice");
        listing = runtime.getListings().createListing(draft("Chairs", 5000), List.of()).join().listing().getId();
        return acceptAs(runtime, offerAs(runtime, buyer, amount));
    }

    /** Registers a new buyer who makes an offer on the current listing one minute later. */
    private UUID offerAs(ApplicationRuntime runtime, String buyer, long amount) {
        clock.advanceSeconds(60);
        registerAndLogin(runtime, buyer);
        return runtime.getOffers().submitOffer(listing, amount).join().offer().getId();
    }

    private UUID acceptAs(ApplicationRuntime runtime, UUID offer) {
        clock.advanceSeconds(60);
        loginAs(runtime, "alice");
        return runtime.getOffers().acceptOffer(offer).join().transactionId();
    }

    /** Both participants confirm; leaves Alice logged in. */
    private void complete(ApplicationRuntime runtime, UUID sale) {
        loginAs(runtime, "alice");
        runtime.getTransactions().confirmCompletion(sale).join();
        String buyer = runtime.getTransactions().getMySales().join().stream()
                .filter(entry -> entry.sale().getId().equals(sale)).findFirst().orElseThrow()
                .otherParticipant().displayName();
        loginAs(runtime, buyer);
        runtime.getTransactions().confirmCompletion(sale).join();
        loginAs(runtime, "alice");
    }

    private ListingStatus listingStatus(ApplicationRuntime runtime) {
        return runtime.getListings().getListing(listing).join().listing().getStatus();
    }

    private void sql(String statement) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("marketplace.db"));
                var sql = connection.createStatement()) {
            sql.execute(statement);
        }
    }

    private ApplicationRuntime open() throws Exception {
        return ApplicationRuntime.open(directory, clock);
    }
}
