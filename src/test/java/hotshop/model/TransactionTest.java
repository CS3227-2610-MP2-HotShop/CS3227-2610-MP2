
package hotshop.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TransactionTest {
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Instant START = Instant.parse("2026-09-22T00:00:00Z");
    private static final UUID SALE = UUID.fromString("00000000-0000-0000-0000-0000000000cc");
    private static final UUID LISTING = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID OFFER = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private Listing listing() {
        return new Listing(SELLER, ListingTest.details("Chair", 5000), List.of(), ListingTest.CREATED);
    }

    private Transaction transaction() {
        Listing listing = listing();
        Offer offer = new Offer(listing, BUYER, 4500, ListingTest.CREATED);
        offer.accept(ListingTest.CREATED);
        listing.reserve();
        return new Transaction(listing, offer, START);
    }

    @Test
    void constructor_acceptedOffer_preservesAgreedSnapshotAfterListingChanges() {
        Listing listing = listing();
        Offer offer = new Offer(listing, BUYER, 4500, ListingTest.CREATED);
        offer.accept(ListingTest.CREATED);
        listing.reserve();
        Transaction transaction = new Transaction(listing, offer, START);
        transaction.cancel(BUYER, START);
        listing.release();
        listing.update(new ListingDetails("Other item", "New description", Category.OTHER,
                9000, Condition.NEW, "Elsewhere"), List.of(), ListingTest.CREATED);
        assertEquals("Chair", transaction.getListingTitle());
        assertEquals("Three wooden chairs", transaction.getListingDescription());
        assertEquals(Condition.GOOD, transaction.getListingCondition());
        assertEquals(4500, transaction.getAgreedPriceCents());
        assertEquals(offer.getId(), transaction.getAcceptedOfferId());
        assertEquals(listing.getId(), transaction.getListingId());
    }

    @Test
    void constructor_pendingOffer_throwsException() {
        Listing listing = listing();
        Offer offer = new Offer(listing, BUYER, 4500, ListingTest.CREATED);
        listing.reserve();
        assertThrows(IllegalStateException.class, () -> new Transaction(listing, offer, START));
    }

    @Test
    void constructor_unreservedListing_throwsException() {
        Listing listing = listing();
        Offer offer = new Offer(listing, BUYER, 4500, ListingTest.CREATED);
        offer.accept(ListingTest.CREATED);
        assertThrows(IllegalStateException.class, () -> new Transaction(listing, offer, START));
    }

    @Test
    void confirmCompletion_sellerFirst_completesAfterBuyerConfirms() {
        Transaction transaction = transaction();
        transaction.confirmCompletion(SELLER, START);
        assertEquals(TransactionStatus.ACTIVE, transaction.getStatus());
        transaction.confirmCompletion(BUYER, START.plusSeconds(1));
        assertEquals(TransactionStatus.COMPLETED, transaction.getStatus());
    }

    @Test
    void confirmCompletion_nullTime_preservesState() {
        Transaction transaction = transaction();
        assertThrows(NullPointerException.class, () -> transaction.confirmCompletion(BUYER, null));
        assertTrue(transaction.getBuyerConfirmedAt().isEmpty());
        assertEquals(TransactionStatus.ACTIVE, transaction.getStatus());
    }

    @Test
    void confirmCompletion_completedTransaction_throwsException() {
        Transaction transaction = transaction();
        transaction.confirmCompletion(SELLER, START);
        transaction.confirmCompletion(BUYER, START.plusSeconds(1));
        assertThrows(IllegalStateException.class,
                () -> transaction.confirmCompletion(BUYER, START.plusSeconds(2)));
        assertEquals(START.plusSeconds(1), transaction.getBuyerConfirmedAt().orElseThrow());
    }

    @Test
    void constructor_differentListing_throwsException() {
        Listing listing = listing();
        Offer offer = new Offer(listing, BUYER, 4500, ListingTest.CREATED);
        offer.accept(ListingTest.CREATED);
        Listing other = listing();
        other.reserve();
        assertThrows(IllegalArgumentException.class, () -> new Transaction(other, offer, START));
    }

    @Test
    void confirmCompletion_bothParticipants_completesTransaction() {
        Transaction transaction = transaction();
        transaction.confirmCompletion(BUYER, START.plusSeconds(1));
        assertEquals(TransactionStatus.ACTIVE, transaction.getStatus());
        assertTrue(transaction.getSellerConfirmedAt().isEmpty());
        transaction.confirmCompletion(SELLER, START.plusSeconds(2));
        assertEquals(TransactionStatus.COMPLETED, transaction.getStatus());
        assertEquals(START.plusSeconds(1), transaction.getBuyerConfirmedAt().orElseThrow());
        assertEquals(START.plusSeconds(2), transaction.getSellerConfirmedAt().orElseThrow());
        assertThrows(IllegalStateException.class, () -> transaction.cancel(SELLER, START));
    }

    @Test
    void confirmCompletion_sameParticipantTwice_throwsException() {
        Transaction transaction = transaction();
        transaction.confirmCompletion(BUYER, START.plusSeconds(1));
        assertThrows(IllegalStateException.class,
                () -> transaction.confirmCompletion(BUYER, START.plusSeconds(2)));
        assertEquals(TransactionStatus.ACTIVE, transaction.getStatus());
    }

    @Test
    void confirmCompletion_nonparticipant_throwsException() {
        Transaction transaction = transaction();
        assertThrows(IllegalArgumentException.class, () -> transaction.confirmCompletion(STRANGER, START));
        assertTrue(transaction.getBuyerConfirmedAt().isEmpty());
        assertTrue(transaction.getSellerConfirmedAt().isEmpty());
    }

    @Test
    void confirmCompletion_beforeCreation_throwsException() {
        Transaction transaction = transaction();
        assertThrows(IllegalArgumentException.class,
                () -> transaction.confirmCompletion(BUYER, START.minusSeconds(1)));
        assertTrue(transaction.getBuyerConfirmedAt().isEmpty());
    }

    @Test
    void cancel_noConfirmations_cancelsTransaction() {
        Transaction transaction = transaction();
        transaction.cancel(SELLER, START);
        assertEquals(TransactionStatus.CANCELLED, transaction.getStatus());
        assertThrows(IllegalStateException.class, () -> transaction.confirmCompletion(BUYER, START));
    }

    @Test
    void cancel_afterFirstConfirmation_requiresAgreement() {
        Transaction transaction = transaction();
        transaction.confirmCompletion(SELLER, START);
        assertThrows(IllegalStateException.class, () -> transaction.cancel(BUYER, START));
        assertEquals(TransactionStatus.ACTIVE, transaction.getStatus());
    }

    @Test
    void cancel_nonparticipant_throwsException() {
        Transaction transaction = transaction();
        assertThrows(IllegalArgumentException.class, () -> transaction.cancel(STRANGER, START));
        assertEquals(TransactionStatus.ACTIVE, transaction.getStatus());
    }

    @Test
    void cancel_noConfirmations_recordsWhoCancelledAndWhen() {
        Transaction transaction = transaction();
        transaction.cancel(BUYER, START.plusSeconds(5));
        assertEquals(BUYER, transaction.getCancelledBy().orElseThrow());
        assertEquals(START.plusSeconds(5), transaction.getCancelledAt().orElseThrow());
    }

    @Test
    void cancel_timeBeforeCreation_throwsWithoutCancelling() {
        Transaction transaction = transaction();
        assertThrows(IllegalArgumentException.class, () -> transaction.cancel(BUYER, START.minusSeconds(1)));
        assertEquals(TransactionStatus.ACTIVE, transaction.getStatus());
        assertTrue(transaction.getCancelledAt().isEmpty());
    }

    @Test
    void restore_activeSaleWithHistory_restoresStateAndAllowsLaterConfirmation() {
        UUID requestId = UUID.randomUUID();
        Transaction restored = Transaction.restore(snapshot(TransactionStatus.ACTIVE, START.plusSeconds(1), null,
                null, null, List.of(rejectedRequest(requestId))));
        assertEquals(SALE, restored.getId());
        assertEquals(TransactionStatus.ACTIVE, restored.getStatus());
        assertEquals(START.plusSeconds(1), restored.getBuyerConfirmedAt().orElseThrow());
        assertEquals(List.of(requestId), restored.getCancellationRequests().stream()
                .map(CancellationRequest::getId).toList());
        restored.confirmCompletion(SELLER, START.plusSeconds(4));
        assertEquals(TransactionStatus.COMPLETED, restored.getStatus());
    }

    @Test
    void restore_savedHistory_rejectsActionBeforeLatestSavedEvent() {
        Transaction restored = Transaction.restore(snapshot(TransactionStatus.ACTIVE, START.plusSeconds(1), null,
                null, null, List.of(rejectedRequest(UUID.randomUUID()))));
        assertThrows(IllegalArgumentException.class,
                () -> restored.confirmCompletion(SELLER, START.plusSeconds(2)));
    }

    @Test
    void restore_acceptedRequestOnActiveSale_throwsException() {
        var accepted = CancellationRequest.restore(UUID.randomUUID(), SALE, SELLER, START.plusSeconds(2),
                CancellationStatus.ACCEPTED, START.plusSeconds(3));
        assertThrows(IllegalArgumentException.class, () -> Transaction.restore(snapshot(
                TransactionStatus.ACTIVE, START.plusSeconds(1), null, null, null, List.of(accepted))));
    }

    @Test
    void restore_cancelledByOtherThanAcceptedRequester_throwsException() {
        var accepted = CancellationRequest.restore(UUID.randomUUID(), SALE, SELLER, START.plusSeconds(2),
                CancellationStatus.ACCEPTED, START.plusSeconds(3));
        assertThrows(IllegalArgumentException.class, () -> Transaction.restore(snapshot(
                TransactionStatus.CANCELLED, START.plusSeconds(1), null, START.plusSeconds(3), BUYER,
                List.of(accepted))));
    }

    @Test
    void restore_requestWithoutAnyConfirmation_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> Transaction.restore(snapshot(
                TransactionStatus.ACTIVE, null, null, null, null, List.of(rejectedRequest(UUID.randomUUID())))));
    }

    @Test
    void restore_cancelledAfterConfirmationWithoutAcceptedRequest_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> Transaction.restore(snapshot(
                TransactionStatus.CANCELLED, START.plusSeconds(1), null, START.plusSeconds(2), SELLER, List.of())));
    }

    private static CancellationRequest rejectedRequest(UUID id) {
        return CancellationRequest.restore(id, SALE, SELLER, START.plusSeconds(2), CancellationStatus.REJECTED,
                START.plusSeconds(3));
    }

    @Test
    void restore_cancelledSale_restoresWhoCancelledAndWhen() {
        Transaction restored = Transaction.restore(snapshot(TransactionStatus.CANCELLED, null, null,
                START.plusSeconds(1), SELLER, List.of()));
        assertEquals(SELLER, restored.getCancelledBy().orElseThrow());
        assertEquals(START.plusSeconds(1), restored.getCancelledAt().orElseThrow());
    }

    @Test
    void restore_completedWithoutBothConfirmations_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> Transaction.restore(snapshot(
                TransactionStatus.COMPLETED, START.plusSeconds(1), null, null, null, List.of())));
    }

    @Test
    void restore_cancelledWithoutCancellationDetails_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> Transaction.restore(snapshot(
                TransactionStatus.CANCELLED, null, null, null, null, List.of())));
    }

    @Test
    void restore_activeWithCancellationDetails_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> Transaction.restore(snapshot(
                TransactionStatus.ACTIVE, null, null, START.plusSeconds(1), SELLER, List.of())));
    }

    @Test
    void restore_pendingRequestBeforeLastRequest_throwsException() {
        var pending = CancellationRequest.restore(UUID.randomUUID(), SALE, SELLER, START.plusSeconds(2),
                CancellationStatus.PENDING, null);
        var later = CancellationRequest.restore(UUID.randomUUID(), SALE, BUYER, START.plusSeconds(3),
                CancellationStatus.PENDING, null);
        assertThrows(IllegalArgumentException.class, () -> Transaction.restore(snapshot(
                TransactionStatus.ACTIVE, START.plusSeconds(1), null, null, null, List.of(pending, later))));
    }

    @Test
    void restore_requestFromAnotherSale_throwsException() {
        var other = CancellationRequest.restore(UUID.randomUUID(), UUID.randomUUID(), SELLER,
                START.plusSeconds(2), CancellationStatus.WITHDRAWN, START.plusSeconds(3));
        assertThrows(IllegalArgumentException.class, () -> Transaction.restore(snapshot(
                TransactionStatus.ACTIVE, START.plusSeconds(1), null, null, null, List.of(other))));
    }

    private static Transaction.Snapshot snapshot(TransactionStatus status, Instant buyerConfirmedAt,
            Instant sellerConfirmedAt, Instant cancelledAt, UUID cancelledBy, List<CancellationRequest> requests) {
        return new Transaction.Snapshot(SALE, LISTING, OFFER, BUYER, SELLER, 4500, "Chair", "Three wooden chairs",
                Condition.GOOD, START, status, buyerConfirmedAt, sellerConfirmedAt, cancelledAt, cancelledBy,
                requests);
    }
}
