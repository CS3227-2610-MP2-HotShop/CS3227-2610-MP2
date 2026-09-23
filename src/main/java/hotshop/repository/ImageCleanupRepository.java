package hotshop.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/** Durable cleanup queue; references are checked again before deleting any image. */
public final class ImageCleanupRepository {
    /** Records an idempotent cleanup request in the caller's transaction. */
    public void schedule(Connection connection, String filename) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT OR IGNORE INTO image_cleanup(filename) VALUES (?)")) {
            statement.setString(1, filename);
            statement.executeUpdate();
        }
    }

    /** Removes a handled request after cleanup or discovery of a live reference. */
    public void finish(Connection connection, String filename) throws SQLException {
        try (var statement = connection.prepareStatement("DELETE FROM image_cleanup WHERE filename = ?")) {
            statement.setString(1, filename);
            statement.executeUpdate();
        }
    }

    /** Reads pending filenames without deleting files or changing the queue. */
    public Set<String> getPending(Connection connection) throws SQLException {
        Set<String> result = new HashSet<>();
        try (var statement = connection.prepareStatement("SELECT filename FROM image_cleanup");
                var rows = statement.executeQuery()) {
            while (rows.next()) {
                result.add(rows.getString(1));
            }
        }
        return result;
    }
}
