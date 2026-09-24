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

import hotshop.model.Offer;
import hotshop.model.OfferStatus;

/** SQL mappings only; callers supply the transaction and enforce permissions. Times are epoch milliseconds. */
public final class OfferRepository {
    public void insert(Connection connection, Offer offer) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO offers (id, listing_id, buyer_id, "
                + "amount_cents, status, created_at, closed_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, offer.getId().toString());
            statement.setString(2, offer.getListingId().toString());
            statement.setString(3, offer.getBuyerId().toString());
            statement.setLong(4, offer.getAmountCents());
            statement.setString(5, offer.getStatus().name());
            statement.setLong(6, offer.getCreatedAt().toEpochMilli());
            setClosedAt(statement, 7, offer);
            statement.executeUpdate();
        }
    }

    /** Saves a status change; amounts and identities never change. */
    public void update(Connection connection, Offer offer) throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE offers SET status = ?, closed_at = ? WHERE id = ?")) {
            statement.setString(1, offer.getStatus().name());
            setClosedAt(statement, 2, offer);
            statement.setString(3, offer.getId().toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Missing offer during update");
            }
        }
    }

    public Optional<Offer> findById(Connection connection, UUID id) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT * FROM offers WHERE id = ?")) {
            statement.setString(1, id.toString());
            return readAll(statement).stream().findFirst();
        }
    }

    /** The buyer's pending offer on a listing, if any; there is at most one. */
    public Optional<Offer> findPending(Connection connection, UUID listingId, UUID buyerId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM offers WHERE listing_id = ? AND buyer_id = ? AND status = 'PENDING'")) {
            statement.setString(1, listingId.toString());
            statement.setString(2, buyerId.toString());
            return readAll(statement).stream().findFirst();
        }
    }

    /** Every pending offer on a listing, from any buyer. */
    public List<Offer> findPendingByListing(Connection connection, UUID listingId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM offers WHERE listing_id = ? AND status = 'PENDING' ORDER BY created_at DESC, id")) {
            statement.setString(1, listingId.toString());
            return readAll(statement);
        }
    }

    /** Every offer on a listing in any status, newest first. */
    public List<Offer> findByListing(Connection connection, UUID listingId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM offers WHERE listing_id = ? ORDER BY created_at DESC, id")) {
            statement.setString(1, listingId.toString());
            return readAll(statement);
        }
    }

    /** Every offer a buyer has made in any status, newest first. */
    public List<Offer> findByBuyer(Connection connection, UUID buyerId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM offers WHERE buyer_id = ? ORDER BY created_at DESC, id")) {
            statement.setString(1, buyerId.toString());
            return readAll(statement);
        }
    }

    /** Pending offer counts for each of the seller's listings that has any, keyed by listing ID. */
    public Map<UUID, Integer> countPendingByListingForSeller(Connection connection, UUID sellerId)
            throws SQLException {
        Map<UUID, Integer> result = new HashMap<>();
        try (var statement = connection.prepareStatement("SELECT offers.listing_id, COUNT(*) FROM offers "
                + "JOIN listings ON listings.id = offers.listing_id WHERE listings.seller_id = ? "
                + "AND offers.status = 'PENDING' GROUP BY offers.listing_id")) {
            statement.setString(1, sellerId.toString());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.put(UUID.fromString(rows.getString(1)), rows.getInt(2));
                }
            }
        }
        return result;
    }

    /** True when the listing has ever received an offer, whatever became of it. */
    public boolean existsForListing(Connection connection, UUID listingId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT 1 FROM offers WHERE listing_id = ? LIMIT 1")) {
            statement.setString(1, listingId.toString());
            try (var rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static void setClosedAt(PreparedStatement statement, int index, Offer offer) throws SQLException {
        if (offer.getClosedAt().isPresent()) {
            statement.setLong(index, offer.getClosedAt().orElseThrow().toEpochMilli());
        } else {
            statement.setNull(index, Types.INTEGER);
        }
    }

    private static List<Offer> readAll(PreparedStatement statement) throws SQLException {
        List<Offer> result = new ArrayList<>();
        try (var rows = statement.executeQuery()) {
            while (rows.next()) {
                result.add(read(rows));
            }
        }
        return result;
    }

    private static Offer read(ResultSet row) throws SQLException {
        long closedAt = row.getLong("closed_at");
        Instant closed = row.wasNull() ? null : Instant.ofEpochMilli(closedAt);
        return Offer.restore(UUID.fromString(row.getString("id")), UUID.fromString(row.getString("listing_id")),
                UUID.fromString(row.getString("buyer_id")), row.getLong("amount_cents"),
                OfferStatus.valueOf(row.getString("status")), Instant.ofEpochMilli(row.getLong("created_at")),
                closed);
    }
}
