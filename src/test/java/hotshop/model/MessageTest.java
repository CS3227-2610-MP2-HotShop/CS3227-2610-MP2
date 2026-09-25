package hotshop.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MessageTest {
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant SENT = ListingTest.CREATED.plusSeconds(60);

    @Test
    void send_participant_createsMessageWithTrimmedText() {
        Conversation conversation = conversation();
        Message message = Message.send(conversation, SELLER, 1, "  Still available?  ", SENT);
        assertEquals(conversation.getId(), message.conversationId());
        assertEquals(SELLER, message.senderId());
        assertEquals(1, message.sequence());
        assertEquals("Still available?", message.text());
        assertEquals(SENT, message.sentAt());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, Message.MAX_LENGTH})
    void send_boundaryLength_keepsText(int length) {
        assertEquals(length, Message.send(conversation(), BUYER, 1, "x".repeat(length), SENT).text().length());
    }

    @Test
    void send_maximumLengthOfEmoji_keepsText() {
        String emoji = "😀".repeat(Message.MAX_LENGTH);
        assertEquals(emoji, Message.send(conversation(), BUYER, 1, emoji, SENT).text());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void send_blankText_throwsException(String text) {
        assertThrows(IllegalArgumentException.class, () -> Message.send(conversation(), BUYER, 1, text, SENT));
    }

    @Test
    void send_overlongText_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> Message.send(conversation(), BUYER, 1, "x".repeat(Message.MAX_LENGTH + 1), SENT));
    }

    @Test
    void send_stranger_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> Message.send(conversation(), UUID.randomUUID(), 1, "Hello", SENT));
    }

    @Test
    void constructor_sequenceBelowOne_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Message(UUID.randomUUID(), UUID.randomUUID(), BUYER, 0, "Hello", SENT));
    }

    @Test
    void constructor_nullSentTime_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new Message(UUID.randomUUID(), UUID.randomUUID(), BUYER, 1, "Hello", null));
    }

    private static Conversation conversation() {
        Listing listing = new Listing(SELLER, ListingTest.details("Chair", 5000), List.of(), ListingTest.CREATED);
        return new Conversation(listing, BUYER, ListingTest.CREATED);
    }
}
