package hotshop.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseTest {
    private static final String FIRST = "/db/test-migration/001_first.sql";
    private static final String SECOND = "/db/test-migration/002_second.sql";

    @TempDir
    Path directory;

    @Test
    void migrate_newDatabase_appliesEveryMigrationInOrder() throws Exception {
        Database database = new Database(directory.resolve("test.db"), List.of(FIRST, SECOND));
        database.migrate();
        assertEquals(List.of(1, 2), appliedVersions(database));
        assertTrue(tableExists(database, "first"));
        assertTrue(tableExists(database, "second"));
    }

    @Test
    void migrate_olderDatabaseWithData_appliesOnlyPendingAndKeepsData() throws Exception {
        Path file = directory.resolve("test.db");
        new Database(file, List.of(FIRST)).migrate();
        Database upgraded = new Database(file, List.of(FIRST, SECOND));
        upgraded.executeTransaction(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO first VALUES (42)");
            }
            return null;
        });
        upgraded.migrate();
        assertEquals(List.of(1, 2), appliedVersions(upgraded));
        assertTrue(tableExists(upgraded, "second"));
        assertEquals(1, countRows(upgraded, "first"));
    }

    @Test
    void migrate_currentDatabase_changesNothing() throws Exception {
        Database database = new Database(directory.resolve("test.db"), List.of(FIRST, SECOND));
        database.migrate();
        database.migrate();
        assertEquals(List.of(1, 2), appliedVersions(database));
    }

    @Test
    void migrate_failingMigration_keepsEarlierVersionsAndRollsBackFailedOne() throws Exception {
        Database database = new Database(directory.resolve("test.db"),
                List.of(FIRST, "/db/test-migration/002_broken.sql"));
        assertThrows(SQLException.class, database::migrate);
        assertEquals(List.of(1), appliedVersions(database));
        assertTrue(tableExists(database, "first"));
        assertFalse(tableExists(database, "partial"));
    }

    @Test
    void migrate_missingMigrationFile_throwsWithoutApplyingAny() throws Exception {
        Database database = new Database(directory.resolve("test.db"),
                List.of(FIRST, "/db/test-migration/missing.sql"));
        assertThrows(IOException.class, database::migrate);
        assertFalse(tableExists(database, "first"));
    }

    @Test
    void executeTransaction_failedWork_rollsBackAllChanges() throws Exception {
        Database database = new Database(directory.resolve("test.db"));
        database.executeTransaction(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE example (value INTEGER)");
            }
            return null;
        });
        assertThrows(SQLException.class, () -> database.executeTransaction(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO example VALUES (1)");
            }
            throw new SQLException("Simulated repository failure");
        }));
        int count = database.executeTransaction(connection -> {
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT COUNT(*) FROM example")) {
                rows.next();
                return rows.getInt(1);
            }
        });
        assertEquals(0, count);
    }

    @Test
    void executeTransaction_newConnection_enforcesForeignKeysAndBoundsLockWait() throws Exception {
        Database database = new Database(directory.resolve("test.db"));
        database.migrate();
        assertThrows(SQLException.class, () -> database.executeTransaction(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO credentials "
                        + "VALUES ('missing', 'test', 1, X'01', X'01')");
            }
            return null;
        }));
        int timeout = database.executeTransaction(connection -> {
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("PRAGMA busy_timeout")) {
                rows.next();
                return rows.getInt(1);
            }
        });
        assertEquals(5000, timeout);
    }

    private static List<Integer> appliedVersions(Database database) throws SQLException {
        return database.executeTransaction(connection -> {
            List<Integer> versions = new ArrayList<>();
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT version FROM schema_migrations ORDER BY version")) {
                while (rows.next()) {
                    versions.add(rows.getInt(1));
                }
            }
            return versions;
        });
    }

    private static int countRows(Database database, String table) throws SQLException {
        return database.executeTransaction(connection -> {
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                rows.next();
                return rows.getInt(1);
            }
        });
    }

    private static boolean tableExists(Database database, String table) throws SQLException {
        return database.executeTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
                statement.setString(1, table);
                try (var rows = statement.executeQuery()) {
                    return rows.next();
                }
            }
        });
    }
}
