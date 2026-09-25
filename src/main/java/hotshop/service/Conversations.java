package hotshop.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import hotshop.model.Conversation;
import hotshop.model.Listing;
import hotshop.model.Message;
import hotshop.repository.ChatRepository;

/**
 * Starting conversations and adding messages inside the caller's database transaction, shared by
 * ChatService and by OfferService, whose offers start the buyer's conversation.
 */
final class Conversations {
    private Conversations() {
    }

    /** The trimmed message text, or a validation failure that says what to fix. */
    static String requireText(String text) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty()) {
            throw ServiceException.validation("Write a message first.");
        }
        int length = trimmed.codePointCount(0, trimmed.length());
        if (length > Message.MAX_LENGTH) {
            throw ServiceException.validation(String.format(Locale.ROOT,
                    "Messages can be at most %,d characters, but this one has %,d.", Message.MAX_LENGTH, length));
        }
        return trimmed;
    }

    /** The buyer's conversation about the listing, started now if they do not have one yet. */
    static Conversation findOrStart(Connection connection, ChatRepository chats, Listing listing, UUID buyerId,
            Instant time) throws SQLException {
        var existing = chats.findConversation(connection, listing.getId(), buyerId);
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }
        Conversation conversation = new Conversation(listing, buyerId, time);
        chats.insertConversation(connection, conversation);
        return conversation;
    }

    /**
     * Adds the next message and marks everything up to it as read for the sender. The time never
     * precedes the previous message or the conversation's start, even if the clock moved backwards.
     */
    static void append(Connection connection, ChatRepository chats, Conversation conversation, UUID senderId,
            String text, Instant now) throws SQLException {
        var latest = chats.findLatestMessage(connection, conversation.getId());
        long sequence = latest.map(Message::sequence).orElse(0L) + 1;
        Instant time = ServiceSupport.latest(now, latest.map(Message::sentAt).orElse(conversation.getCreatedAt()));
        chats.insertMessage(connection, Message.send(conversation, senderId, sequence, text, time));
        conversation.markRead(senderId, sequence, time);
        chats.updateReadState(connection, conversation);
    }
}
