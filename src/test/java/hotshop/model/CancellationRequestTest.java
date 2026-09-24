package hotshop.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CancellationRequestTest {
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Instant START = Instant.parse("2026-09-22T00:00:00Z");

    private Transaction transaction() {
        Listing listing = new Listing(SELLER, ListingTest.details("Chair", 5000), List.of(), ListingTest.CREATED);
        Offer offer = new Offer(listing, BUYER, 4500, ListingTest.CREATED);
        offer.accept(ListingTest.CREATED);
        listing.reserve();
        return new Transaction(listing, offer, START);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void acceptCancellation_otherParticipant_cancelsWithMutualAgreement(boolean isBuyerRequester) {
        Transaction transaction = transaction();
        transaction.confirmCompletion(BUYER, START);
        UUID requester = isBuyerRequester ? BUYER : SELLER;
        UUID responder = isBuyerRequester ? SELLER : BUYER;
        CancellationRequest request = transaction.requestCancellation(requester, START.plusSeconds(1));
        assertEquals(TransactionStatus.ACTIVE, transaction.getStatus());
        assertEquals(CancellationStatus.PENDING, request.getStatus());
        assertEquals(transaction.getId(), request.getTransactionId());
        assertEquals(requester, request.getRequesterId());
        transaction.acceptCancellation(request.getId(), responder, START.plusSeconds(2));
        assertEquals(TransactionStatus.CANCELLED, transaction.getStatus());
        CancellationRequest resolved = transaction.getCancellationRequests().getFirst();
        assertEquals(CancellationStatus.ACCEPTED, resolved.getStatus());
        assertEquals(START.plusSeconds(2), resolved.getResolvedAt().orElseThrow());
        assertEquals(START, transaction.getBuyerConfirmedAt().orElseThrow());
        assertThrows(IllegalStateException.class,
                () -> transaction.confirmCompletion(SELLER, START.plusSeconds(3)));
    }

    @Test
    void confirmCompletion_pendingCancellation_throwsException() {
        Transaction transaction = transaction();
        transaction.confirmCompletion(BUYER, START);
        transaction.requestCancellation(SELLER, START.plusSeconds(1));
        assertThrows(IllegalStateException.class,
                () -> transaction.confirmCompletion(SELLER, START.plusSeconds(2)));
        assertTrue(transaction.getSellerConfirmedAt().isEmpty());
        assertEquals(TransactionStatus.ACTIVE, transaction.getStatus());
    }

    @Test
    void requestCancellation_beforeConfirmation_throwsException() {
        Transaction transaction = transaction();
        assertThrows(IllegalStateException.class, () -> transaction.requestCancellation(BUYER, START));
        assertTrue(transaction.getCancellationRequests().isEmpty());
    }

    @Test
    void requestCancellation_existingPendingRequest_throwsException() {
        Transaction transaction = transaction();
        transaction.confirmCompletion(BUYER, START);
        transaction.requestCancellation(BUYER, START.plusSeconds(1));
        assertThrows(IllegalStateException.class,
                () -> transaction.requestCancellation(SELLER, START.plusSeconds(2)));
        assertEquals(1, transaction.getCancellationRequests().size());
    }

    @Test
    void requestCancellation_nonparticipant_throwsException() {
        Transaction transaction = transaction();
        transaction.confirmCompletion(BUYER, START);
        assertThrows(IllegalArgumentException.class,
                () -> transaction.requestCancellation(STRANGER, START.plusSeconds(1)));
        assertTrue(transaction.getCancellationRequests().isEmpty());
    }

    @Test
    void requestCancellation_completedTransaction_throwsException() {
        Transaction transaction = transaction();
        transaction.confirmCompletion(BUYER, START);
        transaction.confirmCompletion(SELLER, START.plusSeconds(1));
        assertThrows(IllegalStateException.class,
                () -> transaction.requestCancellation(BUYER, START.plusSeconds(2)));
    }

    @Test
    void requestCancellation_beforeFirstConfirmationTime_preservesHistory() {
        Transaction transaction = transaction();
        transaction.confirmCompletion(BUYER, START.plusSeconds(2));
        assertThrows(IllegalArgumentException.class,
                () -> transaction.requestCancellation(BUYER, START.plusSeconds(1)));
        assertTrue(transaction.getCancellationRequests().isEmpty());
    }

    @Test
    void acceptCancellation_nonparticipant_throwsException() {
        Transaction transaction = transaction();
        transaction.confirmCompletion(BUYER, START);
        CancellationRequest request = transaction.requestCancellation(SELLER, START.plusSeconds(1));
        assertThrows(IllegalArgumentException.class,
                () -> transaction.acceptCancellation(request.getId(), STRANGER, START.plusSeconds(2)));
        assertEquals(TransactionStatus.ACTIVE, transaction.getStatus());
    }

    @Test
    void rejectCancellation_nonparticipant_throwsException() {
        Transaction transaction = transaction();
        transaction.confirmCompletion(BUYER, START);
        CancellationRequest request = transaction.requestCancellation(SELLER, START.plusSeconds(1));
        assertThrows(IllegalArgumentException.class,
                () -> transaction.rejectCancellation(request.getId(), STRANGER, START.plusSeconds(2)));
    }

    @Test
    void withdrawCancellation_nonparticipant_throwsException() {
        Transaction transaction = transaction();
        transaction.confirmCompletion(BUYER, START);
        CancellationRequest request = transaction.requestCancellation(SELLER, START.plusSeconds(1));
        assertThrows(IllegalArgumentException.class,
                () -> transaction.withdrawCancellation(request.getId(), STRANGER, START.plusSeconds(2)));
    }

    @Test
    void requestCancellation_afterWithdrawal_preservesHistory() {
        Transaction transaction = transaction();
        transaction.confirmCompletion(SELLER, START);
        CancellationRequest first = transaction.requestCancellation(SELLER, START.plusSeconds(1));
        transaction.withdrawCancellation(first.getId(), SELLER, START.plusSeconds(2));
        CancellationRequest second = transaction.requestCancellation(SELLER, START.plusSeconds(3));
        assertEquals(2, transaction.getCancellationRequests().size());
        assertEquals(CancellationStatus.WITHDRAWN, transaction.getCancellationRequests().getFirst().getStatus());
        assertEquals(CancellationStatus.PENDING, second.getStatus());
    }

    @Test
    void rejectCancellation_otherParticipant_permitsCompletion() {
        Transaction transaction = transaction();
        transaction.confirmCompletion(SELLER, START);
        CancellationRequest request = transaction.requestCancellation(BUYER, START.plusSeconds(1));
        transaction.rejectCancellation(request.getId(), SELLER, START.plusSeconds(2));
        transaction.confirmCompletion(BUYER, START.plusSeconds(3));
        assertEquals(TransactionStatus.COMPLETED, transaction.getStatus());
    }

    @Test
    void acceptCancellation_requester_throwsException() {
        Transaction transaction = transaction();
        transaction.confirmCompletion(BUYER, START);
        CancellationRequest request = transaction.requestCancellation(SELLER, START.plusSeconds(1));
        assertThrows(IllegalArgumentException.class,
                () -> transaction.acceptCancellation(request.getId(), SELLER, START.plusSeconds(2)));
        assertEquals(CancellationStatus.PENDING, transaction.getCancellationRequests().getFirst().getStatus());
    }

    @Test
    void rejectCancellation_requester_throwsException() {
        Transaction transaction = transaction();
        transaction.confirmCompletion(BUYER, START);
        CancellationRequest request = transaction.requestCancellation(SELLER, START.plusSeconds(1));
        assertThrows(IllegalArgumentException.class,
                () -> transaction.rejectCancellation(request.getId(), SELLER, START.plusSeconds(2)));
    }

    @Test
    void withdrawCancellation_otherParticipant_throwsException() {
        Transaction transaction = transaction();
        transaction.confirmCompletion(BUYER, START);
        CancellationRequest request = transaction.requestCancellation(SELLER, START.plusSeconds(1));
        assertThrows(IllegalArgumentException.class,
                () -> transaction.withdrawCancellation(request.getId(), BUYER, START.plusSeconds(2)));
    }

    @Test
    void withdrawCancellation_requester_preservesConfirmationAndPermitsCompletion() {
        Transaction transaction = transaction();
        transaction.confirmCompletion(BUYER, START);
        CancellationRequest request = transaction.requestCancellation(SELLER, START.plusSeconds(1));
        transaction.withdrawCancellation(request.getId(), SELLER, START.plusSeconds(2));
        assertEquals(CancellationStatus.WITHDRAWN, transaction.getCancellationRequests().getFirst().getStatus());
        assertEquals(START, transaction.getBuyerConfirmedAt().orElseThrow());
        transaction.confirmCompletion(SELLER, START.plusSeconds(3));
        assertEquals(TransactionStatus.COMPLETED, transaction.getStatus());
    }

    @Test
    void rejectCancellation_otherParticipant_retainsHistoryAndAllowsNewRequest() {
        Transaction transaction = transaction();
        transaction.confirmCompletion(BUYER, START);
        CancellationRequest first = transaction.requestCancellation(SELLER, START.plusSeconds(1));
        List<CancellationRequest> oldHistory = transaction.getCancellationRequests();
        transaction.rejectCancellation(first.getId(), BUYER, START.plusSeconds(2));
        CancellationRequest second = transaction.requestCancellation(BUYER, START.plusSeconds(3));
        assertNotEquals(first.getId(), second.getId());
        assertEquals(2, transaction.getCancellationRequests().size());
        assertEquals(CancellationStatus.REJECTED, transaction.getCancellationRequests().getFirst().getStatus());
        assertEquals(CancellationStatus.PENDING, oldHistory.getFirst().getStatus());
        assertEquals(1, oldHistory.size());
        assertThrows(UnsupportedOperationException.class, () -> oldHistory.clear());
        assertEquals(START, transaction.getBuyerConfirmedAt().orElseThrow());
        assertThrows(IllegalStateException.class,
                () -> transaction.acceptCancellation(first.getId(), BUYER, START.plusSeconds(4)));
        assertEquals(CancellationStatus.PENDING, transaction.getCancellationRequests().getLast().getStatus());
    }

    @Test
    void acceptCancellation_beforeRequestTime_throwsExceptionWithoutChangingState() {
        Transaction transaction = transaction();
        transaction.confirmCompletion(BUYER, START);
        CancellationRequest request = transaction.requestCancellation(SELLER, START.plusSeconds(2));
        assertThrows(IllegalArgumentException.class,
                () -> transaction.acceptCancellation(request.getId(), BUYER, START.plusSeconds(1)));
        assertEquals(TransactionStatus.ACTIVE, transaction.getStatus());
        assertEquals(CancellationStatus.PENDING, transaction.getCancellationRequests().getFirst().getStatus());
    }
}
