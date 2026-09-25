package hotshop.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Text a participant sent in a conversation, numbered from 1 in the order it was sent. Messages
 * cannot be edited or deleted, and never change an offer or sale.
 */
public record Message(UUID id, UUID conversationId, UUID senderId, long sequence, String text, Instant sentAt) {
    public static final int MAX_LENGTH = 1000;

    /** Validates a new or restored message; surrounding whitespace is removed from the text. */
    public Message {
        Objects.requireNonNull(id, "Message ID");
        Objects.requireNonNull(conversationId, "Conversation ID");
        Objects.requireNonNull(senderId, "Sender ID");
        if (sequence < 1) {
            throw new IllegalArgumentException("Message sequence numbers start at 1");
        }
        text = ModelValidation.text(text, MAX_LENGTH, "Message");
        Objects.requireNonNull(sentAt, "Sent time");
    }

    /** Creates the next message from one of the conversation's participants. */
    public static Message send(Conversation conversation, UUID senderId, long sequence, String text, Instant sentAt) {
        Objects.requireNonNull(conversation, "Conversation");
        if (!conversation.isParticipant(senderId)) {
            throw new IllegalArgumentException("Only the buyer and seller can send messages in this conversation");
        }
        return new Message(UUID.randomUUID(), conversation.getId(), senderId, sequence, text, sentAt);
    }
}
