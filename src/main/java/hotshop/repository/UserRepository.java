package hotshop.repository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import hotshop.model.User;
import hotshop.security.PasswordHash;

/** SQL mappings only; callers supply the transaction and enforce permissions. */
public final class UserRepository {
    /** Finds a profile by normalized username inside the caller's transaction. */
    public Optional<User> findByUsername(Connection connection, String normalized) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT * FROM users WHERE normalized_username = ?")) {
            statement.setString(1, normalized);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(readUser(rows)) : Optional.empty();
            }
        }
    }

    /** Restores a profile by identity without reading credentials. */
    public Optional<User> findById(Connection connection, UUID id) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT * FROM users WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(readUser(rows)) : Optional.empty();
            }
        }
    }

    /** Inserts both registration records; the caller commits or rolls back their shared transaction. */
    public void insert(Connection connection, User user, PasswordHash password) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO users "
                + "(id, username, normalized_username, display_name) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, user.getId().toString());
            statement.setString(2, user.getUsername());
            statement.setString(3, user.getNormalizedUsername());
            statement.setString(4, user.getDisplayName());
            statement.executeUpdate();
        }
        try (var statement = connection.prepareStatement("INSERT INTO credentials "
                + "(user_id, algorithm, iterations, salt, password_hash) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, user.getId().toString());
            statement.setString(2, password.algorithm());
            statement.setInt(3, password.iterations());
            statement.setBytes(4, password.salt());
            statement.setBytes(5, password.hash());
            statement.executeUpdate();
        }
    }

    /** Reads the separate credential record; missing credentials are a storage failure. */
    public PasswordHash getCredentials(Connection connection, UUID id) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT * FROM credentials WHERE user_id = ?")) {
            statement.setString(1, id.toString());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("Missing account credentials");
                }
                return new PasswordHash(rows.getString("algorithm"), rows.getInt("iterations"),
                        rows.getBytes("salt"), rows.getBytes("password_hash"));
            }
        }
    }

    /** Updates profile fields only, preserving username and ID, inside the caller's transaction. */
    public void updateProfile(Connection connection, User user) throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE users SET display_name = ?, "
                + "profile_image = ?, preferred_pickup_location = ? WHERE id = ?")) {
            statement.setString(1, user.getDisplayName());
            statement.setString(2, user.getProfileImage().orElse(null));
            statement.setString(3, user.getPreferredPickupLocation().orElse(null));
            statement.setString(4, user.getId().toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Missing profile during update");
            }
        }
    }

    /** Replaces credential metadata and derived key without changing profile or session state. */
    public void updatePassword(Connection connection, UUID id, PasswordHash password) throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE credentials SET algorithm = ?, iterations = ?, "
                + "salt = ?, password_hash = ? WHERE user_id = ?")) {
            statement.setString(1, password.algorithm());
            statement.setInt(2, password.iterations());
            statement.setBytes(3, password.salt());
            statement.setBytes(4, password.hash());
            statement.setString(5, id.toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Missing credentials during update");
            }
        }
    }

    private User readUser(ResultSet row) throws SQLException {
        return User.restore(UUID.fromString(row.getString("id")), row.getString("username"),
                row.getString("display_name"), row.getString("profile_image"),
                row.getString("preferred_pickup_location"));
    }

    /** Lists live profile-image references so cleanup cannot remove a referenced image. */
    public Set<String> getReferencedImages(Connection connection) throws SQLException {
        Set<String> result = new HashSet<>();
        try (var statement = connection.prepareStatement(
                "SELECT profile_image FROM users WHERE profile_image IS NOT NULL");
                var rows = statement.executeQuery()) {
            while (rows.next()) {
                result.add(rows.getString(1));
            }
        }
        return result;
    }
}
