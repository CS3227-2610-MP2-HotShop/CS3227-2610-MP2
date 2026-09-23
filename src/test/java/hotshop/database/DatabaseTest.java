package hotshop.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseTest {
    @TempDir
    Path directory;

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
}
