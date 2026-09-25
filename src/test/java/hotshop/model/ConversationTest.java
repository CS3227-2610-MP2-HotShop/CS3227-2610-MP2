package hotshop.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ConversationTest {
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Instant STARTED = ListingTest.CREATED.plusSeconds(60);
    private static final Instant LATER = STARTED.plusSeconds(60);

    @Test
    void constructor_availableListing_startsReadByBuyerOnly() {
        Listing listing = listing();
        Conversation conversation = new Conversation(listing, BUYER, STARTED);
        assertEquals(listing.getId(), conversation.getListingId());
        assertEquals(SELLER, conversation.getSellerId());
        assertEquals(BUYER, conversation.getBuyerId());
        assertEquals(Optional.of(STARTED), conversation.getLastOpenedAt(BUYER));
        assertEquals(Optional.empty(), conversation.getLastOpenedAt(SELLER));
        assertEquals(0, conversation.getReadSequence(SELLER));
    }

    @Test
    void constructor_reservedListing_startsConversation() {
        Listing listing = listing();
        listing.reserve();
        assertEquals(BUYER, new Conversation(listing, BUYER, STARTED).getBuyerId());
    }

    @Test
    void constructor_soldListing_throwsException() {
        Listing listing = listing();
        listing.reserve();
        listing.markSold();
        assertThrows(IllegalStateException.class, () -> new Conversation(listing, BUYER, STARTED));
    }

    @Test
    void constructor_archivedListing_throwsException() {
        Listing listing = listing();
        listing.archive();
        assertThrows(IllegalStateException.class, () -> new Conversation(listing, BUYER, STARTED));
    }

    @Test
    void constructor_sellerAsBuyer_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new Conversation(listing(), SELLER, STARTED));
    }

    @Test
    void isParticipant_buyerAndSeller_returnsTrue() {
        Conversation conversation = new Conversation(listing(), BUYER, STARTED);
        assertTrue(conversation.isParticipant(BUYER));
        assertTrue(conversation.isParticipant(SELLER));
    }

    @Test
    void isParticipant_stranger_returnsFalse() {
        assertFalse(new Conversation(listing(), BUYER, STARTED).isParticipant(STRANGER));
    }

    @Test
    void getOtherParticipant_eitherParticipant_returnsTheOther() {
        Conversation conversation = new Conversation(listing(), BUYER, STARTED);
        assertEquals(SELLER, conversation.getOtherParticipant(BUYER));
        assertEquals(BUYER, conversation.getOtherParticipant(SELLER));
    }

    @Test
    void getOtherParticipant_stranger_throwsException() {
        Conversation conversation = new Conversation(listing(), BUYER, STARTED);
        assertThrows(IllegalArgumentException.class, () -> conversation.getOtherParticipant(STRANGER));
    }

    @Test
    void getReadSequence_stranger_throwsException() {
        Conversation conversation = new Conversation(listing(), BUYER, STARTED);
        assertThrows(IllegalArgumentException.class, () -> conversation.getReadSequence(STRANGER));
    }

    @Test
    void markRead_sequenceZero_keepsStartingPosition() {
        Conversation conversation = new Conversation(listing(), BUYER, STARTED);
        conversation.markRead(SELLER, 0, STARTED);
        assertEquals(0, conversation.getReadSequence(SELLER));
        assertEquals(Optional.of(STARTED), conversation.getLastOpenedAt(SELLER));
    }

    @Test
    void markRead_timeBeforeStart_throwsException() {
        Conversation conversation = new Conversation(listing(), BUYER, STARTED);
        assertThrows(IllegalArgumentException.class,
                () -> conversation.markRead(SELLER, 1, STARTED.minusMillis(1)));
    }

    @Test
    void markRead_seller_advancesOnlySellerPosition() {
        Conversation conversation = new Conversation(listing(), BUYER, STARTED);
        conversation.markRead(SELLER, 3, LATER);
        assertEquals(3, conversation.getReadSequence(SELLER));
        assertEquals(Optional.of(LATER), conversation.getLastOpenedAt(SELLER));
        assertEquals(0, conversation.getReadSequence(BUYER));
        assertEquals(Optional.of(STARTED), conversation.getLastOpenedAt(BUYER));
    }

    @Test
    void markRead_olderPosition_keepsLatestPosition() {
        Conversation conversation = new Conversation(listing(), BUYER, STARTED);
        conversation.markRead(SELLER, 3, LATER);
        conversation.markRead(SELLER, 1, STARTED);
        assertEquals(3, conversation.getReadSequence(SELLER));
        assertEquals(Optional.of(LATER), conversation.getLastOpenedAt(SELLER));
    }

    @Test
    void markRead_stranger_throwsException() {
        Conversation conversation = new Conversation(listing(), BUYER, STARTED);
        assertThrows(IllegalArgumentException.class, () -> conversation.markRead(STRANGER, 1, LATER));
    }

    @Test
    void markRead_negativeSequence_throwsException() {
        Conversation conversation = new Conversation(listing(), BUYER, STARTED);
        assertThrows(IllegalArgumentException.class, () -> conversation.markRead(SELLER, -1, LATER));
    }

    @Test
    void restore_savedState_keepsEveryField() {
        UUID id = UUID.randomUUID();
        UUID listing = UUID.randomUUID();
        Conversation restored = Conversation.restore(new Conversation.Snapshot(id, listing, BUYER, SELLER, STARTED,
                2, LATER, 1, null));
        assertEquals(id, restored.getId());
        assertEquals(listing, restored.getListingId());
        assertEquals(STARTED, restored.getCreatedAt());
        assertEquals(2, restored.getReadSequence(BUYER));
        assertEquals(Optional.of(LATER), restored.getLastOpenedAt(BUYER));
        assertEquals(1, restored.getReadSequence(SELLER));
        assertEquals(Optional.empty(), restored.getLastOpenedAt(SELLER));
    }

    @Test
    void restore_openedBeforeStart_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> Conversation.restore(new Conversation.Snapshot(
                UUID.randomUUID(), UUID.randomUUID(), BUYER, SELLER, LATER, 0, STARTED, 0, null)));
    }

    @Test
    void restore_sellerOpenedBeforeStart_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> Conversation.restore(new Conversation.Snapshot(
                UUID.randomUUID(), UUID.randomUUID(), BUYER, SELLER, LATER, 0, LATER, 0, STARTED)));
    }

    @Test
    void restore_negativeReadSequence_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> Conversation.restore(new Conversation.Snapshot(
                UUID.randomUUID(), UUID.randomUUID(), BUYER, SELLER, STARTED, -1, STARTED, 0, null)));
    }

    @Test
    void restore_sameBuyerAndSeller_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> Conversation.restore(new Conversation.Snapshot(
                UUID.randomUUID(), UUID.randomUUID(), BUYER, BUYER, STARTED, 0, STARTED, 0, null)));
    }

    private static Listing listing() {
        return new Listing(SELLER, ListingTest.details("Chair", 5000), List.of(), ListingTest.CREATED);
    }
}
