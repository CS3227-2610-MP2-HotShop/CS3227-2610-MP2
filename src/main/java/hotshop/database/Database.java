package hotshop.database;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

/** Opens configured connections and groups repository work into one transaction. */
public final class Database {
    private static final int SCHEMA_VERSION = 1;
    private final String url;

    /** Selects an absolute SQLite file; connections are opened only when work is executed. */
    public Database(Path file) {
        url = "jdbc:sqlite:" + file.toAbsolutePath().normalize();
    }

    /** Work may pass this connection to any participating repository. */
    @FunctionalInterface
    public interface Work<T> {
        /** Uses the caller-owned connection without committing, closing, or retaining it. */
        T execute(Connection connection) throws SQLException;
    }

    /** Commits successful work, rolls back failures, and closes the configured connection in either case. */
    public <T> T executeTransaction(Work<T> work) throws SQLException {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException | Error failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        }
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
            return connection;
        } catch (SQLException failure) {
            connection.close();
            throw failure;
        }
    }

    /** Applies ordered migrations atomically; never downgrades a newer database. */
    public void migrate() throws SQLException, IOException {
        String migration;
        try (var input = Database.class.getResourceAsStream("/db/migration/001_accounts.sql")) {
            if (input == null) {
                throw new IOException("Missing account migration");
            }
            migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        executeTransaction(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS schema_migrations "
                        + "(version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL)");
                int version;
                try (var rows = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_migrations")) {
                    rows.next();
                    version = rows.getInt(1);
                }
                if (version > SCHEMA_VERSION) {
                    throw new SQLException("Database requires a newer HotShop version");
                }
                if (version == 0) {
                    for (String sql : migration.split(";")) {
                        if (!sql.isBlank()) {
                            statement.execute(sql);
                        }
                    }
                    try (var insert = connection.prepareStatement(
                            "INSERT INTO schema_migrations(version, applied_at) VALUES (?, ?)")) {
                        insert.setInt(1, SCHEMA_VERSION);
                        insert.setString(2, Instant.now().toString());
                        insert.executeUpdate();
                    }
                }
            }
            return null;
        });
    }
}
