package hotshop.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UserTest {
    @ParameterizedTest
    @ValueSource(strings = {"abc", "ABCDEFGHIJKLMNOPQRSTUVWXYZ_123"})
    void constructor_boundaryUsername_acceptsValue(String username) {
        User user = new User(username, "Alice", "profiles/alice.jpg", "Campus");
        assertEquals(username, user.getUsername());
        assertEquals("profiles/alice.jpg", user.getProfileImage().orElseThrow());
        assertEquals("Campus", user.getPreferredPickupLocation().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 80})
    void constructor_boundaryDisplayName_acceptsValue(int length) {
        User user = new User("alice", "a".repeat(length), null, null);
        assertEquals(length, user.getDisplayName().length());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void constructor_blankDisplayName_throwsException(String name) {
        assertThrows(IllegalArgumentException.class, () -> new User("alice", name, null, null));
    }

    @Test
    void constructor_overlongDisplayName_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new User("alice", "a".repeat(81), null, null));
    }

    @Test
    void constructor_nullUsername_throwsException() {
        assertThrows(NullPointerException.class, () -> new User(null, "Alice", null, null));
    }

    @Test
    void constructor_nullDisplayName_throwsException() {
        assertThrows(NullPointerException.class, () -> new User("alice", null, null, null));
    }

    @Test
    void constructor_blankOptionalLocation_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new User("alice", "Alice", null, " "));
    }

    @Test
    void constructor_boundaryLocation_acceptsValue() {
        User user = new User("alice", "Alice", null, "a".repeat(200));
        assertEquals(200, user.getPreferredPickupLocation().orElseThrow().length());
    }

    @Test
    void constructor_overlongLocation_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new User("alice", "Alice", null, "a".repeat(201)));
    }

    @Test
    void constructor_unsafeProfileImage_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new User("alice", "Alice", "../secret", null));
    }

    @Test
    void constructor_trimmedProfile_preservesIdentityAndOptionalFields() {
        User user = new User(" Alice_1 ", " Alice ", null, null);
        assertNotNull(user.getId());
        assertEquals("Alice_1", user.getUsername());
        assertEquals("alice_1", user.getNormalizedUsername());
        assertEquals("Alice", user.getDisplayName());
        assertTrue(user.getProfileImage().isEmpty());
        assertTrue(user.getPreferredPickupLocation().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ab", "a b", "alice!", "用户abc", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void constructor_invalidUsername_throwsException(String username) {
        assertThrows(IllegalArgumentException.class, () -> new User(username, "Alice", null, null));
    }
}
