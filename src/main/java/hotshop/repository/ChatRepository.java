package hotshop.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import hotshop.model.Conversation;
import hotshop.model.Message;

/**
 * SQL mappings for conversations, their messages, and read positions. Callers supply the
 * transaction and enforce permissions. Times are epoch milliseconds.
 */
public final class ChatRepository {
    /**
     * Inserts a conversation and its initial read positions in the caller's transaction.
     *
     * @param connection the caller-owned connection for the current database transaction
     * @param conversation the conversation whose state is used
     * @throws SQLException if database access fails
     */
    public void insertConversation(Connection connection, Conversation conversation) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO conversations (id, listing_id, buyer_id, "
                + "seller_id, created_at, buyer_read_sequence, buyer_opened_at, seller_read_sequence, "
                + "seller_opened_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, conversation.getId().toString());
            statement.setString(2, conversation.getListingId().toString());
            statement.setString(3, conversation.getBuyerId().toString());
            statement.setString(4, conversation.getSellerId().toString());
            statement.setLong(5, conversation.getCreatedAt().toEpochMilli());
            setReadState(statement, 6, conversation, conversation.getBuyerId());
            setReadState(statement, 8, conversation, conversation.getSellerId());
            statement.executeUpdate();
        }
    }

    /**
     * Saves both participants' read positions; the participants and listing never change.
     *
     * @param connection the caller-owned connection for the current database transaction
     * @param conversation the conversation whose state is used
     * @throws SQLException if database access fails
     */
    public void updateReadState(Connection connection, Conversation conversation) throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE conversations SET buyer_read_sequence = ?, "
                + "buyer_opened_at = ?, seller_read_sequence = ?, seller_opened_at = ? WHERE id = ?")) {
            setReadState(statement, 1, conversation, conversation.getBuyerId());
            setReadState(statement, 3, conversation, conversation.getSellerId());
            statement.setString(5, conversation.getId().toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Missing conversation during update");
            }
        }
    }

    /**
     * Returns the matching conversation, or empty if none exists.
     *
     * @param connection the caller-owned connection for the current database transaction
     * @param id the conversation ID
     * @return the matching conversation, or empty if none exists
     * @throws SQLException if database access fails
     */
    public Optional<Conversation> findConversation(Connection connection, UUID id) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT * FROM conversations WHERE id = ?")) {
            statement.setString(1, id.toString());
            return readConversations(statement).stream().findFirst();
        }
    }

    /**
     * The buyer's conversation about a listing, if they have one; there is at most one.
     *
     * @param connection the caller-owned connection for the current database transaction
     * @param listingId the ID of the listing
     * @param buyerId the buyer's user ID
     * @return the matching conversation, or empty if none exists
     * @throws SQLException if database access fails
     */
    public Optional<Conversation> findConversation(Connection connection, UUID listingId, UUID buyerId)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM conversations WHERE listing_id = ? AND buyer_id = ?")) {
            statement.setString(1, listingId.toString());
            statement.setString(2, buyerId.toString());
            return readConversations(statement).stream().findFirst();
        }
    }

    /**
     * Every conversation the user takes part in, as the buyer or the seller, in no particular order.
     *
     * @param connection the caller-owned connection for the current database transaction
     * @param userId the user ID to look up
     * @return all conversations involving the user, in unspecified order
     * @throws SQLException if database access fails
     */
    public List<Conversation> findConversationsFor(Connection connection, UUID userId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM conversations WHERE buyer_id = ? OR seller_id = ?")) {
            statement.setString(1, userId.toString());
            statement.setString(2, userId.toString());
            return readConversations(statement);
        }
    }

    /**
     * Deletes every conversation about the listing and all their messages, messages first for the foreign key.
     *
     * @param connection the caller-owned connection for the current database transaction
     * @param listingId the ID of the listing
     * @throws SQLException if database access fails
     */
    public void deleteForListing(Connection connection, UUID listingId) throws SQLException {
        try (var statement = connection.prepareStatement("DELETE FROM messages WHERE conversation_id IN "
                + "(SELECT id FROM conversations WHERE listing_id = ?)")) {
            statement.setString(1, listingId.toString());
            statement.executeUpdate();
        }
        try (var statement = connection.prepareStatement("DELETE FROM conversations WHERE listing_id = ?")) {
            statement.setString(1, listingId.toString());
            statement.executeUpdate();
        }
    }

    /**
     * Inserts a message with its assigned sequence in the caller's transaction.
     *
     * @param connection the caller-owned connection for the current database transaction
     * @param message the message to persist
     * @throws SQLException if database access fails
     */
    public void insertMessage(Connection connection, Message message) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO messages (id, conversation_id, sender_id, "
                + "sequence, text, sent_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, message.id().toString());
            statement.setString(2, message.conversationId().toString());
            statement.setString(3, message.senderId().toString());
            statement.setLong(4, message.sequence());
            statement.setString(5, message.text());
            statement.setLong(6, message.sentAt().toEpochMilli());
            statement.executeUpdate();
        }
    }

    /**
     * Every message in the conversation, in the order it was sent.
     *
     * @param connection the caller-owned connection for the current database transaction
     * @param conversationId the ID of the conversation
     * @return the messages in ascending sequence order
     * @throws SQLException if database access fails
     */
    public List<Message> findMessages(Connection connection, UUID conversationId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM messages WHERE conversation_id = ? ORDER BY sequence")) {
            statement.setString(1, conversationId.toString());
            return readMessages(statement);
        }
    }

    /**
     * The most recent message in the conversation, if it has any.
     *
     * @param connection the caller-owned connection for the current database transaction
     * @param conversationId the ID of the conversation
     * @return the latest message, or empty if the conversation has none
     * @throws SQLException if database access fails
     */
    public Optional<Message> findLatestMessage(Connection connection, UUID conversationId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM messages WHERE conversation_id = ? ORDER BY sequence DESC LIMIT 1")) {
            statement.setString(1, conversationId.toString());
            return readMessages(statement).stream().findFirst();
        }
    }

    /**
     * Messages from the other participant after the reader's read position.
     *
     * @param connection the caller-owned connection for the current database transaction
     * @param conversationId the ID of the conversation
     * @param readerId the ID of the participant whose unread messages are counted
     * @param readSequence the last read sequence number; messages after it are counted
     * @return the number of messages from other senders after the read position
     * @throws SQLException if database access fails
     */
    public int countUnreadMessages(Connection connection, UUID conversationId, UUID readerId, long readSequence)
            throws SQLException {
        try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM messages "
                + "WHERE conversation_id = ? AND sender_id <> ? AND sequence > ?")) {
            statement.setString(1, conversationId.toString());
            statement.setString(2, readerId.toString());
            statement.setLong(3, readSequence);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private static void setReadState(PreparedStatement statement, int index, Conversation conversation,
            UUID participant) throws SQLException {
        statement.setLong(index, conversation.getReadSequence(participant));
        Optional<Instant> openedAt = conversation.getLastOpenedAt(participant);
        if (openedAt.isPresent()) {
            statement.setLong(index + 1, openedAt.orElseThrow().toEpochMilli());
        } else {
            statement.setNull(index + 1, Types.INTEGER);
        }
    }

    private static List<Conversation> readConversations(PreparedStatement statement) throws SQLException {
        List<Conversation> result = new ArrayList<>();
        try (var rows = statement.executeQuery()) {
            while (rows.next()) {
                result.add(Conversation.restore(new Conversation.Snapshot(UUID.fromString(rows.getString("id")),
                        UUID.fromString(rows.getString("listing_id")), UUID.fromString(rows.getString("buyer_id")),
                        UUID.fromString(rows.getString("seller_id")),
                        Instant.ofEpochMilli(rows.getLong("created_at")), rows.getLong("buyer_read_sequence"),
                        getTime(rows, "buyer_opened_at"), rows.getLong("seller_read_sequence"),
                        getTime(rows, "seller_opened_at"))));
            }
        }
        return result;
    }

    private static List<Message> readMessages(PreparedStatement statement) throws SQLException {
        List<Message> result = new ArrayList<>();
        try (var rows = statement.executeQuery()) {
            while (rows.next()) {
                result.add(new Message(UUID.fromString(rows.getString("id")),
                        UUID.fromString(rows.getString("conversation_id")),
                        UUID.fromString(rows.getString("sender_id")), rows.getLong("sequence"),
                        rows.getString("text"), Instant.ofEpochMilli(rows.getLong("sent_at"))));
            }
        }
        return result;
    }

    private static Instant getTime(ResultSet row, String column) throws SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : Instant.ofEpochMilli(value);
    }
}
