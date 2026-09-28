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

    /**
     * Validates a new or restored message; surrounding whitespace is removed from the text.
     *
     * @param id the message ID
     * @param conversationId the ID of the conversation
     * @param senderId the ID of the participant sending the message
     * @param sequence the message sequence number
     * @param text the message text, trimmed and limited to 1,000 Unicode code points
     * @param sentAt the time the message was sent
     * @throws IllegalArgumentException if the supplied values violate the documented validation limits
     * @throws NullPointerException if a required value is null
     */
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

    /**
     * Creates the next message from one of the conversation's participants.
     *
     * @param conversation the conversation whose state is used
     * @param senderId the ID of the participant sending the message
     * @param sequence the message sequence number
     * @param text the message text, trimmed and limited to 1,000 Unicode code points
     * @param sentAt the time the message was sent
     * @return a new validated message with a generated identity
     * @throws IllegalArgumentException if the supplied values violate the documented validation limits or the sender
     *     is not a participant
     * @throws NullPointerException if a required value is null
     */
    public static Message send(Conversation conversation, UUID senderId, long sequence, String text, Instant sentAt) {
        Objects.requireNonNull(conversation, "Conversation");
        if (!conversation.isParticipant(senderId)) {
            throw new IllegalArgumentException("Only the buyer and seller can send messages in this conversation");
        }
        return new Message(UUID.randomUUID(), conversation.getId(), senderId, sequence, text, sentAt);
    }
}
