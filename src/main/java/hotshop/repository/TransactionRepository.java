package hotshop.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import hotshop.model.CancellationRequest;
import hotshop.model.CancellationStatus;
import hotshop.model.Condition;
import hotshop.model.Transaction;
import hotshop.model.TransactionStatus;

/** SQL mappings for agreed sales and their cancellation requests. Times are epoch milliseconds. */
public final class TransactionRepository {
    /** Saves a newly created sale in the caller's transaction. */
    public void insert(Connection connection, Transaction transaction) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO transactions (id, listing_id, "
                + "accepted_offer_id, buyer_id, seller_id, agreed_price_cents, listing_title, listing_description, "
                + "listing_condition, created_at, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, transaction.getId().toString());
            statement.setString(2, transaction.getListingId().toString());
            statement.setString(3, transaction.getAcceptedOfferId().toString());
            statement.setString(4, transaction.getBuyerId().toString());
            statement.setString(5, transaction.getSellerId().toString());
            statement.setLong(6, transaction.getAgreedPriceCents());
            statement.setString(7, transaction.getListingTitle());
            statement.setString(8, transaction.getListingDescription());
            statement.setString(9, transaction.getListingCondition().name());
            statement.setLong(10, transaction.getCreatedAt().toEpochMilli());
            statement.setString(11, transaction.getStatus().name());
            statement.executeUpdate();
        }
    }

    /** Saves status, confirmations, cancellation details, and every cancellation request. */
    public void update(Connection connection, Transaction transaction) throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE transactions SET status = ?, "
                + "buyer_confirmed_at = ?, seller_confirmed_at = ?, cancelled_at = ?, cancelled_by = ? WHERE id = ?")) {
            statement.setString(1, transaction.getStatus().name());
            setTime(statement, 2, transaction.getBuyerConfirmedAt().orElse(null));
            setTime(statement, 3, transaction.getSellerConfirmedAt().orElse(null));
            setTime(statement, 4, transaction.getCancelledAt().orElse(null));
            statement.setString(5, transaction.getCancelledBy().map(UUID::toString).orElse(null));
            statement.setString(6, transaction.getId().toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Missing transaction during update");
            }
        }
        try (var statement = connection.prepareStatement("INSERT INTO cancellation_requests (id, transaction_id, "
                + "requester_id, created_at, status, resolved_at) VALUES (?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(id) DO UPDATE SET status = excluded.status, resolved_at = excluded.resolved_at")) {
            for (CancellationRequest request : transaction.getCancellationRequests()) {
                statement.setString(1, request.getId().toString());
                statement.setString(2, transaction.getId().toString());
                statement.setString(3, request.getRequesterId().toString());
                statement.setLong(4, request.getCreatedAt().toEpochMilli());
                statement.setString(5, request.getStatus().name());
                setTime(statement, 6, request.getResolvedAt().orElse(null));
                statement.executeUpdate();
            }
        }
    }

    /** Restores one sale with its cancellation requests, or empty when no sale has this ID. */
    public Optional<Transaction> findById(Connection connection, UUID id) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT * FROM transactions WHERE id = ?")) {
            statement.setString(1, id.toString());
            return readAll(connection, statement).stream().findFirst();
        }
    }

    /** Every sale where the user is the seller, newest first. */
    public List<Transaction> findBySeller(Connection connection, UUID sellerId) throws SQLException {
        return findByParticipant(connection, "seller_id", sellerId);
    }

    /** Every sale where the user is the buyer, newest first. */
    public List<Transaction> findByBuyer(Connection connection, UUID buyerId) throws SQLException {
        return findByParticipant(connection, "buyer_id", buyerId);
    }

    /** The status of the sale created from an accepted offer, if there is one. */
    public Optional<TransactionStatus> findStatusByOffer(Connection connection, UUID offerId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT status FROM transactions WHERE accepted_offer_id = ?")) {
            statement.setString(1, offerId.toString());
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(TransactionStatus.valueOf(rows.getString(1))) : Optional.empty();
            }
        }
    }

    /** Sale statuses for every accepted offer on a listing, keyed by offer ID. */
    public Map<UUID, TransactionStatus> findStatusesByListing(Connection connection, UUID listingId)
            throws SQLException {
        Map<UUID, TransactionStatus> result = new HashMap<>();
        try (var statement = connection.prepareStatement(
                "SELECT accepted_offer_id, status FROM transactions WHERE listing_id = ?")) {
            statement.setString(1, listingId.toString());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.put(UUID.fromString(rows.getString(1)), TransactionStatus.valueOf(rows.getString(2)));
                }
            }
        }
        return result;
    }

    /** The column name is one of two fixed names chosen in this class, never user input. */
    private List<Transaction> findByParticipant(Connection connection, String column, UUID userId)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM transactions WHERE " + column + " = ? ORDER BY created_at DESC, id")) {
            statement.setString(1, userId.toString());
            return readAll(connection, statement);
        }
    }

    private List<Transaction> readAll(Connection connection, PreparedStatement statement) throws SQLException {
        List<Transaction> result = new ArrayList<>();
        try (var rows = statement.executeQuery()) {
            while (rows.next()) {
                result.add(read(connection, rows));
            }
        }
        return result;
    }

    private Transaction read(Connection connection, ResultSet row) throws SQLException {
        UUID id = UUID.fromString(row.getString("id"));
        String cancelledBy = row.getString("cancelled_by");
        return Transaction.restore(new Transaction.Snapshot(id, UUID.fromString(row.getString("listing_id")),
                UUID.fromString(row.getString("accepted_offer_id")), UUID.fromString(row.getString("buyer_id")),
                UUID.fromString(row.getString("seller_id")), row.getLong("agreed_price_cents"),
                row.getString("listing_title"), row.getString("listing_description"),
                Condition.valueOf(row.getString("listing_condition")), Instant.ofEpochMilli(row.getLong("created_at")),
                TransactionStatus.valueOf(row.getString("status")), getTime(row, "buyer_confirmed_at"),
                getTime(row, "seller_confirmed_at"), getTime(row, "cancelled_at"),
                cancelledBy == null ? null : UUID.fromString(cancelledBy), readRequests(connection, id)));
    }

    /** Requests in the order they were made, so the latest (possibly pending) one is last. */
    private List<CancellationRequest> readRequests(Connection connection, UUID transactionId) throws SQLException {
        List<CancellationRequest> requests = new ArrayList<>();
        try (var statement = connection.prepareStatement(
                "SELECT * FROM cancellation_requests WHERE transaction_id = ? ORDER BY created_at, rowid")) {
            statement.setString(1, transactionId.toString());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    requests.add(CancellationRequest.restore(UUID.fromString(rows.getString("id")), transactionId,
                            UUID.fromString(rows.getString("requester_id")),
                            Instant.ofEpochMilli(rows.getLong("created_at")),
                            CancellationStatus.valueOf(rows.getString("status")), getTime(rows, "resolved_at")));
                }
            }
        }
        return requests;
    }

    private static void setTime(PreparedStatement statement, int index, Instant time) throws SQLException {
        if (time == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setLong(index, time.toEpochMilli());
        }
    }

    private static Instant getTime(ResultSet row, String column) throws SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : Instant.ofEpochMilli(value);
    }
}
