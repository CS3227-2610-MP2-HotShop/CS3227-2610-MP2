package hotshop.service;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import hotshop.database.Database;
import hotshop.model.User;
import hotshop.repository.UserRepository;
import hotshop.security.Passwords;
import hotshop.storage.ImageStorage;

/**
 * Authenticated account operations, serialized with all other application services.
 * Returned futures fail with AccountException; joining wraps it in CompletionException.
 * Completion callbacks must not block on other service calls or close the runtime.
 */
public final class AccountService {
    private final Database database;
    private final UserRepository users;
    private final ServiceWorker worker;
    private final AuthenticatedSession session;
    private final Passwords passwords = new Passwords();
    private final ProfileImages images;

    /** Wires the shared database, worker, session, and profile-specific managed image namespace. */
    public AccountService(Database database, UserRepository users, ServiceWorker worker,
            AuthenticatedSession session, ImageStorage storage) {
        this.database = database;
        this.users = users;
        this.worker = worker;
        this.session = session;
        images = new ProfileImages(database, users, storage);
    }

    /** Lifecycle maintenance, queued with account operations; does not authenticate a user. */
    public CompletableFuture<Void> recoverImages() {
        return worker.submit(() -> {
            try {
                images.recover();
            } catch (SQLException | IOException exception) {
                throw new AccountException(AccountException.Code.STORAGE, "Image storage is unavailable", exception);
            }
            return null;
        });
    }

    /** Requires login; imports validated image data, saves its reference, then retires the old copy. */
    public CompletableFuture<User> replaceProfileImage(Path source) {
        return worker.submit(() -> {
            UUID id = session.requireUserId();
            try {
                return images.replace(id, source);
            } catch (SQLException | IOException exception) {
                throw new AccountException(AccountException.Code.STORAGE, "Unable to save profile image", exception);
            }
        });
    }

    /** Requires login; clears the image reference before cleanup. An absent image is a no-op. */
    public CompletableFuture<User> removeProfileImage() {
        return worker.submit(() -> {
            UUID id = session.requireUserId();
            try {
                return images.remove(id);
            } catch (SQLException exception) {
                throw new AccountException(AccountException.Code.STORAGE, "Unable to remove profile image", exception);
            }
        });
    }

    /** Requires a logged-out session; atomically saves a unique profile and credentials without logging in. */
    public CompletableFuture<User> register(String username, String password, String displayName) {
        return worker.submit(() -> {
            session.requireLoggedOut();
            User user;
            try {
                user = new User(username, displayName, null, null);
            } catch (IllegalArgumentException | NullPointerException exception) {
                throw new AccountException(AccountException.Code.VALIDATION, "Invalid username or display name");
            }
            var hash = passwords.hash(password);
            return executeTransaction(connection -> {
                if (users.findByUsername(connection, user.getNormalizedUsername()).isPresent()) {
                    throw new AccountException(AccountException.Code.USERNAME_UNAVAILABLE, "Username is unavailable");
                }
                users.insert(connection, user, hash);
                return user;
            });
        });
    }

    /** Requires a logged-out session; establishes identity only after credential verification succeeds. */
    public CompletableFuture<User> login(String username, String password) {
        return worker.submit(() -> {
            session.requireLoggedOut();
            String normalized = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
            User user = executeTransaction(connection -> {
                User found = users.findByUsername(connection, normalized).orElseThrow(this::invalidCredentials);
                if (!passwords.matches(password, users.getCredentials(connection, found.getId()))) {
                    throw invalidCredentials();
                }
                return found;
            });
            session.login(user.getId());
            return user;
        });
    }

    /** Reads identity in queue order, returning empty when logged out. */
    public CompletableFuture<Optional<UUID>> getCurrentUserId() {
        return worker.submit(session::getCurrentUserId);
    }

    /** Requires login; retrieves the current user's latest profile including private pickup preferences. */
    public CompletableFuture<User> getOwnProfile() {
        return worker.submit(() -> {
            UUID id = session.requireUserId();
            return executeTransaction(connection -> requireUser(connection, id));
        });
    }

    /** Requires login and the current password; saves a validated replacement while retaining the session. */
    public CompletableFuture<Void> changePassword(String currentPassword, String newPassword) {
        return worker.submit(() -> {
            UUID id = session.requireUserId();
            return executeTransaction(connection -> {
                if (!passwords.matches(currentPassword, users.getCredentials(connection, id))) {
                    throw invalidCredentials();
                }
                users.updatePassword(connection, id, passwords.hash(newPassword));
                return null;
            });
        });
    }

    /** Requires login; returns permitted fields only, or NOT_FOUND when the requested user does not exist. */
    public CompletableFuture<PublicProfile> getPublicProfile(UUID id) {
        return worker.submit(() -> {
            session.requireUserId();
            if (id == null) {
                throw new AccountException(AccountException.Code.VALIDATION, "User ID is required");
            }
            return executeTransaction(connection -> {
                User user = requireUser(connection, id);
                return new PublicProfile(user.getId(), user.getDisplayName(), user.getProfileImage());
            });
        });
    }

    /** Requires login; saves both text fields atomically. Null location clears the optional preference. */
    public CompletableFuture<User> updateProfile(String displayName, String preferredPickupLocation) {
        return worker.submit(() -> {
            UUID id = session.requireUserId();
            return executeTransaction(connection -> {
                User old = requireUser(connection, id);
                User updated;
                try {
                    updated = User.restore(id, old.getUsername(), displayName,
                            old.getProfileImage().orElse(null), preferredPickupLocation);
                } catch (IllegalArgumentException | NullPointerException exception) {
                    throw new AccountException(AccountException.Code.VALIDATION, "Invalid profile details");
                }
                users.updateProfile(connection, updated);
                return updated;
            });
        });
    }

    private User requireUser(Connection connection, UUID id) throws SQLException {
        return users.findById(connection, id).orElseThrow(() ->
                new AccountException(AccountException.Code.NOT_FOUND, "Profile was not found"));
    }

    /** Clears session identity in queue order; already logged out is a successful no-op. */
    public CompletableFuture<Void> logout() {
        return worker.submit(() -> {
            session.logout();
            return null;
        });
    }

    private AccountException invalidCredentials() {
        return new AccountException(AccountException.Code.AUTHENTICATION, "Invalid username or password");
    }

    private <T> T executeTransaction(Database.Work<T> work) {
        try {
            return database.executeTransaction(work);
        } catch (SQLException exception) {
            throw new AccountException(AccountException.Code.STORAGE, "Account storage is unavailable", exception);
        }
    }
}
