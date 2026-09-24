package hotshop.database;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Opens configured connections and groups repository work into one transaction. */
public final class Database {
    /** Released migrations in order; a migration's version is its one-based position. Append only. */
    private static final List<String> MIGRATIONS = List.of(
            "/db/migration/001_accounts.sql",
            "/db/migration/002_listings.sql",
            "/db/migration/003_offers.sql",
            "/db/migration/004_sale_completion.sql",
            "/db/migration/005_meetups.sql");
    private final String url;
    private final List<String> migrations;

    /** Selects an absolute SQLite file; connections are opened only when work is executed. */
    public Database(Path file) {
        this(file, MIGRATIONS);
    }

    /** Uses the given ordered migration resources instead of the released schema. */
    Database(Path file, List<String> migrations) {
        url = "jdbc:sqlite:" + file.toAbsolutePath().normalize();
        this.migrations = List.copyOf(migrations);
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

    /**
     * Applies each pending migration in order, one transaction per version, so a failure leaves
     * the database at the last fully applied version. Never downgrades a newer database.
     */
    public void migrate() throws SQLException, IOException {
        List<String> scripts = new ArrayList<>();
        for (String resource : migrations) {
            scripts.add(readMigration(resource));
        }
        int current = executeTransaction(this::readSchemaVersion);
        if (current > scripts.size()) {
            throw new SQLException("Database requires a newer HotShop version");
        }
        for (int version = current + 1; version <= scripts.size(); version++) {
            applyMigration(version, scripts.get(version - 1));
        }
    }

    private static String readMigration(String resource) throws IOException {
        try (var input = Database.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing migration " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private int readSchemaVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_migrations "
                    + "(version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL)");
            try (var rows = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_migrations")) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private void applyMigration(int version, String script) throws SQLException {
        executeTransaction(connection -> {
            try (Statement statement = connection.createStatement()) {
                for (String sql : script.split(";")) {
                    if (!sql.isBlank()) {
                        statement.execute(sql);
                    }
                }
            }
            try (var insert = connection.prepareStatement(
                    "INSERT INTO schema_migrations(version, applied_at) VALUES (?, ?)")) {
                insert.setInt(1, version);
                insert.setString(2, Instant.now().toString());
                insert.executeUpdate();
            }
            return null;
        });
    }
}
