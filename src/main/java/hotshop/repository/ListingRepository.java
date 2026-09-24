package hotshop.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import hotshop.model.Category;
import hotshop.model.Condition;
import hotshop.model.Listing;
import hotshop.model.ListingDetails;
import hotshop.model.ListingImage;
import hotshop.model.ListingStatus;

/** SQL mappings only; callers supply the transaction and enforce permissions. Times are epoch milliseconds. */
public final class ListingRepository {
    /** Inserts a new listing and its ordered images in the caller's transaction. */
    public void insert(Connection connection, Listing listing) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO listings (id, seller_id, title, description, "
                + "category, price_cents, condition, pickup_location, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, listing.getId().toString());
            statement.setString(2, listing.getSellerId().toString());
            bindDetails(statement, 3, listing.getDetails());
            statement.setString(9, listing.getStatus().name());
            statement.setLong(10, listing.getCreatedAt().toEpochMilli());
            statement.setLong(11, listing.getUpdatedAt().toEpochMilli());
            statement.executeUpdate();
        }
        insertImages(connection, listing);
    }

    /** Saves changed details, status, update time, and the complete image list of an existing listing. */
    public void update(Connection connection, Listing listing) throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE listings SET title = ?, description = ?, "
                + "category = ?, price_cents = ?, condition = ?, pickup_location = ?, status = ?, updated_at = ? "
                + "WHERE id = ?")) {
            bindDetails(statement, 1, listing.getDetails());
            statement.setString(7, listing.getStatus().name());
            statement.setLong(8, listing.getUpdatedAt().toEpochMilli());
            statement.setString(9, listing.getId().toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Missing listing during update");
            }
        }
        try (var statement = connection.prepareStatement("DELETE FROM listing_images WHERE listing_id = ?")) {
            statement.setString(1, listing.getId().toString());
            statement.executeUpdate();
        }
        insertImages(connection, listing);
    }

    /** Permanently removes a listing; its image rows are removed with it. */
    public void delete(Connection connection, UUID id) throws SQLException {
        try (var statement = connection.prepareStatement("DELETE FROM listings WHERE id = ?")) {
            statement.setString(1, id.toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Missing listing during delete");
            }
        }
    }

    /** Restores one listing with its images, or empty when no listing has this ID. */
    public Optional<Listing> findById(Connection connection, UUID id) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT * FROM listings WHERE id = ?")) {
            statement.setString(1, id.toString());
            List<Listing> found = readAll(connection, statement);
            return found.stream().findFirst();
        }
    }

    /** Restores every listing of one seller in any status, newest first. */
    public List<Listing> findBySeller(Connection connection, UUID sellerId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM listings WHERE seller_id = ? ORDER BY created_at DESC, id")) {
            statement.setString(1, sellerId.toString());
            return readAll(connection, statement);
        }
    }

    /** Restores every available listing not owned by the given user, newest first. */
    public List<Listing> findAvailableExcludingSeller(Connection connection, UUID sellerId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT * FROM listings WHERE status = 'AVAILABLE' "
                + "AND seller_id <> ? ORDER BY created_at DESC, id")) {
            statement.setString(1, sellerId.toString());
            return readAll(connection, statement);
        }
    }

    /** Lists every listing image filename still referenced, so cleanup never removes a live photo. */
    public Set<String> getReferencedImages(Connection connection) throws SQLException {
        Set<String> result = new HashSet<>();
        try (var statement = connection.prepareStatement("SELECT filename FROM listing_images");
                var rows = statement.executeQuery()) {
            while (rows.next()) {
                result.add(rows.getString(1));
            }
        }
        return result;
    }

    private void insertImages(Connection connection, Listing listing) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO listing_images (listing_id, display_order, filename) VALUES (?, ?, ?)")) {
            for (ListingImage image : listing.getImages()) {
                statement.setString(1, listing.getId().toString());
                statement.setInt(2, image.displayOrder());
                statement.setString(3, image.filename());
                statement.executeUpdate();
            }
        }
    }

    private static void bindDetails(PreparedStatement statement, int first, ListingDetails details)
            throws SQLException {
        statement.setString(first, details.title());
        statement.setString(first + 1, details.description());
        statement.setString(first + 2, details.category().name());
        statement.setLong(first + 3, details.priceCents());
        statement.setString(first + 4, details.condition().name());
        statement.setString(first + 5, details.pickupLocation());
    }

    private List<Listing> readAll(Connection connection, PreparedStatement statement) throws SQLException {
        List<Listing> result = new ArrayList<>();
        try (var rows = statement.executeQuery()) {
            while (rows.next()) {
                result.add(readListing(connection, rows));
            }
        }
        return result;
    }

    private Listing readListing(Connection connection, ResultSet row) throws SQLException {
        UUID id = UUID.fromString(row.getString("id"));
        ListingDetails details = new ListingDetails(row.getString("title"), row.getString("description"),
                Category.valueOf(row.getString("category")), row.getLong("price_cents"),
                Condition.valueOf(row.getString("condition")), row.getString("pickup_location"));
        return Listing.restore(id, UUID.fromString(row.getString("seller_id")), details, readImages(connection, id),
                ListingStatus.valueOf(row.getString("status")), Instant.ofEpochMilli(row.getLong("created_at")),
                Instant.ofEpochMilli(row.getLong("updated_at")));
    }

    private List<ListingImage> readImages(Connection connection, UUID listingId) throws SQLException {
        List<ListingImage> images = new ArrayList<>();
        try (var statement = connection.prepareStatement(
                "SELECT filename, display_order FROM listing_images WHERE listing_id = ? ORDER BY display_order")) {
            statement.setString(1, listingId.toString());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    images.add(new ListingImage(rows.getString(1), rows.getInt(2)));
                }
            }
        }
        return images;
    }
}
