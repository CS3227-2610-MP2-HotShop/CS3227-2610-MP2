package hotshop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import hotshop.ApplicationRuntime;

class AccountServiceTest {
    private static final String PASSWORD = "Sample1!";

    @TempDir
    Path directory;

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "ab", "a b", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void register_invalidUsername_reportsValidation(String username) throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            assertFailure(AccountException.Code.VALIDATION,
                    () -> runtime.getAccounts().register(username, PASSWORD, "Alice").join());
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void updateProfile_invalidDisplayName_preservesProfile(String displayName) throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            var accounts = runtime.getAccounts();
            accounts.register("alice", PASSWORD, "Alice").join();
            accounts.login("alice", PASSWORD).join();
            assertFailure(AccountException.Code.VALIDATION, () -> accounts.updateProfile(displayName, null).join());
            assertEquals("Alice", accounts.getOwnProfile().join().getDisplayName());
        }
    }

    @Test
    void updateProfile_boundaryLengths_preservesMaximumValuesAcrossRestart() throws Exception {
        UUID id;
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            var accounts = runtime.getAccounts();
            id = accounts.register("a".repeat(30), PASSWORD, "a").join().getId();
            accounts.login("a".repeat(30), PASSWORD).join();
            accounts.updateProfile("\ud83d\ude00".repeat(80), "x".repeat(200)).join();
            assertFailure(AccountException.Code.VALIDATION,
                    () -> accounts.updateProfile("x".repeat(81), null).join());
        }
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            var user = runtime.getAccounts().login("a".repeat(30), PASSWORD).join();
            assertEquals(id, user.getId());
            assertEquals("\ud83d\ude00".repeat(80), user.getDisplayName());
            assertEquals("x".repeat(200), user.getPreferredPickupLocation().orElseThrow());
        }
    }

    @Test
    void login_differentlyNormalizedUnicode_rejectsDifferentPassword() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            var accounts = runtime.getAccounts();
            accounts.register("alice", "Sample1!\u00e9", "Alice").join();
            assertFailure(AccountException.Code.AUTHENTICATION,
                    () -> accounts.login("alice", "Sample1!e\u0301").join());
            assertFailure(AccountException.Code.AUTHENTICATION,
                    () -> accounts.login("alice", "sample1!\u00e9").join());
            assertEquals("alice", accounts.login("alice", "Sample1!\u00e9").join().getUsername());
        }
    }

    @Test
    void changePassword_storageFailure_preservesOldPasswordAndSession() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            var accounts = runtime.getAccounts();
            var alice = accounts.register("alice", PASSWORD, "Alice").join();
            accounts.login("alice", PASSWORD).join();
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("marketplace.db"));
                    var statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER fail_password BEFORE UPDATE ON credentials "
                        + "BEGIN SELECT RAISE(ABORT, 'simulated failure'); END");
            }
            assertFailure(AccountException.Code.STORAGE,
                    () -> accounts.changePassword(PASSWORD, "Changed1!").join());
            assertEquals(alice.getId(), accounts.getCurrentUserId().join().orElseThrow());
            accounts.logout().join();
            assertFailure(AccountException.Code.AUTHENTICATION, () -> accounts.login("alice", "Changed1!").join());
            assertEquals(alice.getId(), accounts.login("alice", PASSWORD).join().getId());
        }
    }

    @Test
    void register_credentialWriteFailure_rollsBackProfile() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("marketplace.db"));
                    var statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER fail_credentials BEFORE INSERT ON credentials "
                        + "BEGIN SELECT RAISE(ABORT, 'simulated failure'); END");
                assertFailure(AccountException.Code.STORAGE,
                        () -> runtime.getAccounts().register("alice", PASSWORD, "Alice").join());
                statement.execute("DROP TRIGGER fail_credentials");
            }
            assertTrue(runtime.getAccounts().getCurrentUserId().join().isEmpty());
            var user = runtime.getAccounts().register("alice", PASSWORD, "Alice").join();
            assertEquals(user.getId(), runtime.getAccounts().login("alice", PASSWORD).join().getId());
        }
    }

    @Test
    void updateProfile_queuedBeforeLogout_usesIdentityInQueueOrder() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            var accounts = runtime.getAccounts();
            accounts.register("alice", PASSWORD, "Alice").join();
            accounts.register("bobby", PASSWORD, "Bob").join();
            accounts.login("alice", PASSWORD).join();
            var update = accounts.updateProfile("Updated Alice", "Campus");
            var logout = accounts.logout();
            var login = accounts.login("bobby", PASSWORD);
            assertEquals("alice", update.join().getUsername());
            logout.join();
            assertEquals("Bob", login.join().getDisplayName());
            assertTrue(accounts.getOwnProfile().join().getPreferredPickupLocation().isEmpty());
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Ab1!abc", "abcdefgh1!", "ABCDEFGH1!", "Abcdefgh!", "Abcdefg1 ",
        "Abcdefg1\u00a3"})
    void register_invalidPassword_rejectsWithoutCreatingAccount(String password) throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            var accounts = runtime.getAccounts();
            assertFailure(AccountException.Code.VALIDATION,
                    () -> accounts.register("alice", password, "Alice").join());
            assertFailure(AccountException.Code.AUTHENTICATION, () -> accounts.login("alice", PASSWORD).join());
            assertTrue(accounts.getCurrentUserId().join().isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 128})
    void register_boundaryPasswordLength_acceptsPassword(int length) throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            String password = "Ab1!" + "x".repeat(length - 4);
            var user = runtime.getAccounts().register("alice", password, "Alice").join();
            assertEquals(user.getId(), runtime.getAccounts().login("alice", password).join().getId());
        }
    }

    @Test
    void register_overlongPassword_rejectsValue() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            assertFailure(AccountException.Code.VALIDATION,
                    () -> runtime.getAccounts().register("alice", "Ab1!" + "x".repeat(125), "Alice").join());
        }
    }

    @Test
    void login_exactUnicodeAndWhitespace_preservesPassword() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            var accounts = runtime.getAccounts();
            String password = " Ab1!" + "\ud83d\ude00".repeat(122) + " ";
            var user = accounts.register("alice", password, "Alice").join();
            assertFailure(AccountException.Code.AUTHENTICATION,
                    () -> accounts.login("alice", password.strip()).join());
            assertEquals(user.getId(), accounts.login("alice", password).join().getId());
        }
    }

    @Test
    void register_duplicateNormalizedUsername_preservesOriginalAccount() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            var accounts = runtime.getAccounts();
            var original = accounts.register("Alice", PASSWORD, "Alice").join();
            assertFailure(AccountException.Code.USERNAME_UNAVAILABLE,
                    () -> accounts.register(" ALICE ", "Different1!", "Other").join());
            assertEquals(original.getId(), accounts.login("alice", PASSWORD).join().getId());
        }
    }

    @Test
    void login_unknownUserAndWrongPassword_returnSameError() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            var accounts = runtime.getAccounts();
            accounts.register("alice", PASSWORD, "Alice").join();
            var unknown = assertThrows(CompletionException.class, () -> accounts.login("nobody", PASSWORD).join());
            var wrong = assertThrows(CompletionException.class, () -> accounts.login("alice", "Wrong1!").join());
            assertEquals(unknown.getCause().getMessage(), wrong.getCause().getMessage());
            assertEquals(AccountException.Code.AUTHENTICATION, ((AccountException) wrong.getCause()).getCode());
            assertTrue(accounts.getCurrentUserId().join().isEmpty());
        }
    }

    @Test
    void register_loggedIn_rejectsWithoutSwitchingIdentity() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            var accounts = runtime.getAccounts();
            var alice = accounts.register("alice", PASSWORD, "Alice").join();
            accounts.login("alice", PASSWORD).join();
            assertFailure(AccountException.Code.SESSION,
                    () -> accounts.register("bobby", PASSWORD, "Bob").join());
            assertEquals(alice.getId(), accounts.getCurrentUserId().join().orElseThrow());
        }
    }

    @Test
    void login_loggedIn_rejectsSwitchUntilLogout() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            var accounts = runtime.getAccounts();
            accounts.register("alice", PASSWORD, "Alice").join();
            var bob = accounts.register("bobby", PASSWORD, "Bob").join();
            accounts.login("alice", PASSWORD).join();
            assertFailure(AccountException.Code.SESSION, () -> accounts.login("bobby", PASSWORD).join());
            accounts.logout().join();
            accounts.logout().join();
            assertTrue(accounts.getCurrentUserId().join().isEmpty());
            assertEquals(bob.getId(), accounts.login("bobby", PASSWORD).join().getId());
        }
    }

    @Test
    void profileOperations_loggedOut_rejectAccess() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            var accounts = runtime.getAccounts();
            assertFailure(AccountException.Code.SESSION, () -> accounts.getOwnProfile().join());
            assertFailure(AccountException.Code.SESSION, () -> accounts.getPublicProfile(UUID.randomUUID()).join());
            assertFailure(AccountException.Code.SESSION, () -> accounts.updateProfile("Alice", null).join());
            assertFailure(AccountException.Code.SESSION, () -> accounts.changePassword(PASSWORD, "Changed1!").join());
        }
    }

    @Test
    void updateProfile_invalidLocation_leavesBothFieldsUnchanged() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            var accounts = runtime.getAccounts();
            accounts.register("alice", PASSWORD, "Alice").join();
            accounts.login("alice", PASSWORD).join();
            accounts.updateProfile("Alice", "Campus").join();
            assertFailure(AccountException.Code.VALIDATION,
                    () -> accounts.updateProfile("Changed", "x".repeat(201)).join());
            assertEquals("Alice", accounts.getOwnProfile().join().getDisplayName());
            assertEquals("Campus", accounts.getOwnProfile().join().getPreferredPickupLocation().orElseThrow());
            assertTrue(accounts.updateProfile("Alice", null).join().getPreferredPickupLocation().isEmpty());
        }
    }

    @Test
    void getPublicProfile_unknownId_reportsNotFound() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            var accounts = runtime.getAccounts();
            accounts.register("alice", PASSWORD, "Alice").join();
            accounts.login("alice", PASSWORD).join();
            assertFailure(AccountException.Code.NOT_FOUND, () -> accounts.getPublicProfile(UUID.randomUUID()).join());
        }
    }

    @Test
    void changePassword_wrongCurrentPassword_preservesCredentialAndSession() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            var accounts = runtime.getAccounts();
            var alice = accounts.register("alice", PASSWORD, "Alice").join();
            accounts.login("alice", PASSWORD).join();
            assertFailure(AccountException.Code.AUTHENTICATION,
                    () -> accounts.changePassword("Wrong1!", "Changed1!").join());
            assertEquals(alice.getId(), accounts.getCurrentUserId().join().orElseThrow());
            accounts.logout().join();
            assertEquals(alice.getId(), accounts.login("alice", PASSWORD).join().getId());
        }
    }

    @Test
    void changePassword_invalidNewPassword_preservesCredential() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            var accounts = runtime.getAccounts();
            accounts.register("alice", PASSWORD, "Alice").join();
            accounts.login("alice", PASSWORD).join();
            assertFailure(AccountException.Code.VALIDATION, () -> accounts.changePassword(PASSWORD, "short").join());
            accounts.logout().join();
            assertEquals("alice", accounts.login("alice", PASSWORD).join().getUsername());
        }
    }

    @Test
    void changePassword_correctCurrentPassword_keepsSessionAndReplacesCredential() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            AccountService accounts = runtime.getAccounts();
            var alice = accounts.register("alice", PASSWORD, "Alice").join();
            accounts.login("alice", PASSWORD).join();
            accounts.changePassword(PASSWORD, "Changed2!").join();
            assertEquals(alice.getId(), accounts.getCurrentUserId().join().orElseThrow());
            accounts.logout().join();
            assertFailure(AccountException.Code.AUTHENTICATION, () -> accounts.login("alice", PASSWORD).join());
            assertEquals(alice.getId(), accounts.login("alice", "Changed2!").join().getId());
        }
    }

    private static void assertFailure(AccountException.Code code, Runnable action) {
        CompletionException failure = assertThrows(CompletionException.class, action::run);
        assertEquals(code, ((AccountException) failure.getCause()).getCode());
    }

    @Test
    void updateProfile_loggedIn_preservesIdentityAndKeepsLocationPrivate() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            AccountService accounts = runtime.getAccounts();
            var alice = accounts.register("alice", PASSWORD, "Alice").join();
            accounts.register("bobby", PASSWORD, "Bob").join();
            accounts.login("alice", PASSWORD).join();
            var updated = accounts.updateProfile(" Alice Updated ", " Campus ").join();
            assertEquals(alice.getId(), updated.getId());
            assertEquals("alice", updated.getUsername());
            assertEquals("Campus", accounts.getOwnProfile().join().getPreferredPickupLocation().orElseThrow());
            accounts.logout().join();
            accounts.login("bobby", PASSWORD).join();
            assertEquals(new PublicProfile(alice.getId(), "Alice Updated", java.util.Optional.empty()),
                    accounts.getPublicProfile(alice.getId()).join());
            assertTrue(accounts.getOwnProfile().join().getPreferredPickupLocation().isEmpty());
        }
    }

    @Test
    void register_reopenedDatabase_preservesAccountAndStartsLoggedOut() throws Exception {
        UUID id;
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            AccountService accounts = runtime.getAccounts();
            id = accounts.register(" Alice ", PASSWORD, " Alice ").join().getId();
            assertTrue(accounts.getCurrentUserId().join().isEmpty());
            assertEquals(id, accounts.login("ALICE", PASSWORD).join().getId());
            assertEquals(id, accounts.getCurrentUserId().join().orElseThrow());
        }
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory)) {
            AccountService accounts = runtime.getAccounts();
            assertTrue(accounts.getCurrentUserId().join().isEmpty());
            var user = accounts.login("alice", PASSWORD).join();
            assertEquals(id, user.getId());
            assertEquals("Alice", user.getDisplayName());
            assertEquals("Alice", user.getUsername());
        }
    }
}
