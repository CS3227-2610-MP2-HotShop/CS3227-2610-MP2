package hotshop.service;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;

import hotshop.database.Database;
import hotshop.model.User;
import hotshop.repository.ImageCleanupRepository;
import hotshop.repository.UserRepository;
import hotshop.storage.ImageStorage;

/** Coordinates the non-atomic filesystem/database boundary on the service worker. */
final class ProfileImages {
    private static final System.Logger LOGGER = System.getLogger(ProfileImages.class.getName());
    private final Database database;
    private final UserRepository users;
    private final ImageStorage storage;
    private final ImageCleanupRepository cleanup = new ImageCleanupRepository();

    ProfileImages(Database database, UserRepository users, ImageStorage storage) {
        this.database = database;
        this.users = users;
        this.storage = storage;
    }

    User replace(UUID userId, Path source) throws SQLException, IOException {
        String name;
        try {
            name = storage.importImage(source, ImageStorage.PROFILE_LIMITS);
        } catch (IllegalArgumentException exception) {
            throw new AccountException(AccountException.Code.VALIDATION, exception.getMessage());
        }
        try {
            return save(userId, name);
        } finally {
            // Rechecking references is essential if a commit succeeded but its connection close failed.
            recoverSafely();
        }
    }

    User remove(UUID userId) throws SQLException {
        User user = save(userId, null);
        recoverSafely();
        return user;
    }

    private User save(UUID userId, String name) throws SQLException {
        return database.executeTransaction(connection -> {
            User old = users.findById(connection, userId).orElseThrow(() ->
                    new AccountException(AccountException.Code.NOT_FOUND, "Profile was not found"));
            User updated = User.restore(userId, old.getUsername(), old.getDisplayName(), name,
                    old.getPreferredPickupLocation().orElse(null));
            users.updateProfile(connection, updated);
            if (old.getProfileImage().isPresent()) {
                cleanup.schedule(connection, old.getProfileImage().orElseThrow());
            }
            return updated;
        });
    }

    /** Discovers crash-created orphans in this feature's namespace and retries durable cleanup. */
    void recover() throws SQLException, IOException {
        var files = storage.listManagedFiles();
        database.executeTransaction(connection -> {
            var referenced = users.getReferencedImages(connection);
            for (String name : files) {
                if (!referenced.contains(name)) {
                    cleanup.schedule(connection, name);
                }
            }
            return null;
        });
        for (String name : database.executeTransaction(cleanup::getPending)) {
            database.executeTransaction(connection -> {
                if (!users.getReferencedImages(connection).contains(name)) {
                    try {
                        storage.delete(name);
                    } catch (IOException | IllegalArgumentException exception) {
                        LOGGER.log(System.Logger.Level.WARNING,
                                "Profile image cleanup will retry at startup", exception);
                        return null;
                    }
                }
                cleanup.finish(connection, name);
                return null;
            });
        }
    }

    private void recoverSafely() {
        try {
            recover();
        } catch (SQLException | IOException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Profile image cleanup will retry at startup", exception);
        }
    }
}
