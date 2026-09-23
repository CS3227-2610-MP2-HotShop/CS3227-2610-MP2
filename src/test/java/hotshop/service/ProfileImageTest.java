package hotshop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hotshop.ApplicationRuntime;

class ProfileImageTest {
    @TempDir
    Path directory;

    @Test
    void replaceProfileImage_databaseFailure_preservesPreviousImageAndCleansImport() throws Exception {
        Path source = image();
        Path data = directory.resolve("data");
        try (ApplicationRuntime runtime = ApplicationRuntime.open(data)) {
            var accounts = runtime.getAccounts();
            accounts.register("alice", "Sample1!", "Alice").join();
            accounts.login("alice", "Sample1!").join();
            String original = accounts.replaceProfileImage(source).join().getProfileImage().orElseThrow();
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + data.resolve("marketplace.db"));
                    var statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER fail_profile BEFORE UPDATE ON users "
                        + "BEGIN SELECT RAISE(ABORT, 'simulated failure'); END");
            }
            assertFailure(AccountException.Code.STORAGE, () -> accounts.replaceProfileImage(source).join());
            assertEquals(original, accounts.getOwnProfile().join().getProfileImage().orElseThrow());
            try (var files = Files.list(data.resolve("images/profiles"))) {
                assertEquals(1, files.count());
            }
            assertTrue(Files.exists(data.resolve("images/profiles").resolve(original)));
        }
    }

    @Test
    void removeProfileImage_cleanupFailure_commitsAndRetriesAfterRestart() throws Exception {
        Path source = image();
        Path data = directory.resolve("data");
        Path blocked;
        try (ApplicationRuntime runtime = ApplicationRuntime.open(data)) {
            var accounts = runtime.getAccounts();
            accounts.register("alice", "Sample1!", "Alice").join();
            accounts.login("alice", "Sample1!").join();
            String original = accounts.replaceProfileImage(source).join().getProfileImage().orElseThrow();
            blocked = data.resolve("images/profiles").resolve(original);
            Files.delete(blocked);
            Files.createDirectory(blocked);
            assertTrue(accounts.removeProfileImage().join().getProfileImage().isEmpty());
            assertTrue(Files.isDirectory(blocked));
        }
        Files.delete(blocked);
        Files.copy(source, blocked);
        try (ApplicationRuntime runtime = ApplicationRuntime.open(data)) {
            runtime.getAccounts().login("alice", "Sample1!").join();
            assertTrue(runtime.getAccounts().getOwnProfile().join().getProfileImage().isEmpty());
            assertFalse(Files.exists(blocked));
        }
    }

    @Test
    void open_orphanImages_removesOnlyUnreferencedManagedFiles() throws Exception {
        Path source = image();
        Path data = directory.resolve("data");
        String saved;
        try (ApplicationRuntime runtime = ApplicationRuntime.open(data)) {
            var accounts = runtime.getAccounts();
            accounts.register("alice", "Sample1!", "Alice").join();
            accounts.login("alice", "Sample1!").join();
            saved = accounts.replaceProfileImage(source).join().getProfileImage().orElseThrow();
        }
        Path orphan = data.resolve("images/profiles").resolve(UUID.randomUUID() + ".png");
        Files.copy(source, orphan);
        Path unrelated = data.resolve("images/profiles/notes.txt");
        Files.writeString(unrelated, "preserve");
        try (ApplicationRuntime runtime = ApplicationRuntime.open(data)) {
            assertFalse(Files.exists(orphan));
            assertTrue(Files.exists(unrelated));
            assertTrue(Files.exists(data.resolve("images/profiles").resolve(saved)));
            runtime.getAccounts().login("alice", "Sample1!").join();
            assertEquals(saved, runtime.getAccounts().getOwnProfile().join().getProfileImage().orElseThrow());
        }
    }

    @Test
    void replaceProfileImage_invalidImage_preservesExistingProfile() throws Exception {
        Path source = image();
        Path data = directory.resolve("data");
        try (ApplicationRuntime runtime = ApplicationRuntime.open(data)) {
            var accounts = runtime.getAccounts();
            accounts.register("alice", "Sample1!", "Alice").join();
            accounts.login("alice", "Sample1!").join();
            String saved = accounts.replaceProfileImage(source).join().getProfileImage().orElseThrow();
            Files.writeString(source, "not an image");
            assertFailure(AccountException.Code.VALIDATION, () -> accounts.replaceProfileImage(source).join());
            assertEquals(saved, accounts.getOwnProfile().join().getProfileImage().orElseThrow());
        }
    }

    @Test
    void imageOperations_loggedOut_rejectBeforeAccessingFiles() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory.resolve("data"))) {
            assertFailure(AccountException.Code.SESSION,
                    () -> runtime.getAccounts().replaceProfileImage(directory.resolve("missing.png")).join());
            assertFailure(AccountException.Code.SESSION, () -> runtime.getAccounts().removeProfileImage().join());
        }
    }

    private Path image() throws Exception {
        Path source = directory.resolve("photo.png");
        assertTrue(ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", source.toFile()));
        return source;
    }

    private void assertFailure(AccountException.Code code, Runnable action) {
        var failure = assertThrows(CompletionException.class, action::run);
        assertEquals(code, ((AccountException) failure.getCause()).getCode());
    }

    @Test
    void replaceProfileImage_validReplacement_persistsAndRetiresOldCopy() throws Exception {
        Path source = directory.resolve("photo.png");
        ImageIO.write(new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB), "png", source.toFile());
        Path data = directory.resolve("data");
        String replacement;
        try (ApplicationRuntime runtime = ApplicationRuntime.open(data)) {
            var accounts = runtime.getAccounts();
            accounts.register("alice", "Sample1!", "Alice").join();
            accounts.login("alice", "Sample1!").join();
            String original = accounts.replaceProfileImage(source).join().getProfileImage().orElseThrow();
            replacement = accounts.replaceProfileImage(source).join().getProfileImage().orElseThrow();
            assertFalse(Files.exists(data.resolve("images/profiles").resolve(original)));
            assertTrue(Files.exists(data.resolve("images/profiles").resolve(replacement)));
            assertTrue(Files.exists(source));
        }
        try (ApplicationRuntime runtime = ApplicationRuntime.open(data)) {
            var accounts = runtime.getAccounts();
            accounts.login("alice", "Sample1!").join();
            assertEquals(replacement, accounts.getOwnProfile().join().getProfileImage().orElseThrow());
            assertTrue(accounts.removeProfileImage().join().getProfileImage().isEmpty());
            assertTrue(accounts.removeProfileImage().join().getProfileImage().isEmpty());
            assertFalse(Files.exists(data.resolve("images/profiles").resolve(replacement)));
        }
    }
}
