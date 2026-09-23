package hotshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hotshop.service.AccountException;

class ApplicationRuntimeTest {
    @TempDir
    Path directory;

    @Test
    void open_corruptDatabase_failsWithoutReplacingDataAndReleasesLock() throws Exception {
        Path file = directory.resolve("marketplace.db");
        Files.writeString(file, "not a database");
        assertThrows(SQLException.class, () -> ApplicationRuntime.open(directory));
        assertEquals("not a database", Files.readString(file));
        Files.delete(file);
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            assertNotNull(runtime.getAccounts());
        }
    }

    @Test
    void open_newerSchema_rejectsDowngrade() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            assertNotNull(runtime.getAccounts());
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("marketplace.db"));
                var statement = connection.createStatement()) {
            statement.execute("INSERT INTO schema_migrations VALUES (999, 'future')");
        }
        assertThrows(SQLException.class, () -> ApplicationRuntime.open(directory));
    }

    @Test
    void close_queuedRegistration_drainsWorkAndRejectsNewOperations() throws Exception {
        ApplicationRuntime runtime = ApplicationRuntime.open(directory);
        var pending = runtime.getAccounts().register("alice", "Sample1!", "Alice");
        runtime.close();
        runtime.close();
        var id = pending.join().getId();
        var failure = assertThrows(CompletionException.class, () -> runtime.getAccounts().getCurrentUserId().join());
        assertEquals(AccountException.Code.SESSION, ((AccountException) failure.getCause()).getCode());
        try (ApplicationRuntime reopened = ApplicationRuntime.open(directory)) {
            assertEquals(id, reopened.getAccounts().login("alice", "Sample1!").join().getId());
        }
    }

    @Test
    void open_lockedDirectory_rejectsSecondInstanceAndAllowsReopen() throws Exception {
        try (ApplicationRuntime first = ApplicationRuntime.open(directory)) {
            assertNotNull(first);
            assertThrows(IOException.class, () -> ApplicationRuntime.open(directory));
        }
        try (ApplicationRuntime reopened = ApplicationRuntime.open(directory)) {
            assertNotNull(reopened);
        }
    }
}
