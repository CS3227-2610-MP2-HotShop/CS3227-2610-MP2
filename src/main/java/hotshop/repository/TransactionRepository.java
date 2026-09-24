package hotshop.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import hotshop.model.Transaction;
import hotshop.model.TransactionStatus;

/**
 * SQL mappings for agreed sales. This milestone only saves new sales and reads their status;
 * confirmations and cancellation are written by the future TransactionService.
 */
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
}
