
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

    private Listing listing() {
        return new Listing(SELLER, ListingTest.details("Chair", 5000), List.of());
    }

    private Transaction transaction() {
        Listing listing = listing();
        Offer offer = new Offer(listing, BUYER, 4500);
        offer.accept();
        listing.reserve();
        return new Transaction(listing, offer, START);
    }

    @Test
    void constructor_acceptedOffer_preservesAgreedSnapshotAfterListingChanges() {
        Listing listing = listing();
        Offer offer = new Offer(listing, BUYER, 4500);
        offer.accept();
        listing.reserve();
        Transaction transaction = new Transaction(listing, offer, START);
        transaction.cancel(BUYER);
        listing.release();
        listing.update(new ListingDetails("Other item", "New description", Category.OTHER,
                9000, Condition.NEW, "Elsewhere"), List.of());
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
        Offer offer = new Offer(listing, BUYER, 4500);
        listing.reserve();
        assertThrows(IllegalStateException.class, () -> new Transaction(listing, offer, START));
    }

    @Test
    void constructor_unreservedListing_throwsException() {
        Listing listing = listing();
        Offer offer = new Offer(listing, BUYER, 4500);
        offer.accept();
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
        Offer offer = new Offer(listing, BUYER, 4500);
        offer.accept();
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
        assertThrows(IllegalStateException.class, () -> transaction.cancel(SELLER));
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
        transaction.cancel(SELLER);
        assertEquals(TransactionStatus.CANCELLED, transaction.getStatus());
        assertThrows(IllegalStateException.class, () -> transaction.confirmCompletion(BUYER, START));
    }

    @Test
    void cancel_afterFirstConfirmation_requiresAgreement() {
        Transaction transaction = transaction();
        transaction.confirmCompletion(SELLER, START);
        assertThrows(IllegalStateException.class, () -> transaction.cancel(BUYER));
        assertEquals(TransactionStatus.ACTIVE, transaction.getStatus());
    }

    @Test
    void cancel_nonparticipant_throwsException() {
        Transaction transaction = transaction();
        assertThrows(IllegalArgumentException.class, () -> transaction.cancel(STRANGER));
        assertEquals(TransactionStatus.ACTIVE, transaction.getStatus());
    }
}
