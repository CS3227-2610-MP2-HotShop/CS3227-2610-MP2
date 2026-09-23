package hotshop.service;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;

import hotshop.database.Database;
import hotshop.model.User;
import hotshop.repository.UserRepository;
import hotshop.storage.ImageStorage;

/** Saves profile image references; file handling is delegated to the profile image namespace. */
final class ProfileImages {
    private final Database database;
    private final UserRepository users;
    private final ManagedImages images;

    ProfileImages(Database database, UserRepository users, ImageStorage storage) {
        this.database = database;
        this.users = users;
        images = new ManagedImages(database, storage, "profiles", ImageStorage.PROFILE_LIMITS,
                users::getReferencedImages);
    }

    User replace(UUID userId, Path source) throws SQLException, IOException {
        String name = images.importImage(source);
        try {
            return save(userId, name);
        } finally {
            images.recoverSafely();
        }
    }

    User remove(UUID userId) throws SQLException {
        User user = save(userId, null);
        images.recoverSafely();
        return user;
    }

    void recover() throws SQLException, IOException {
        images.recover();
    }

    private User save(UUID userId, String name) throws SQLException {
        return database.executeTransaction(connection -> {
            User old = users.findById(connection, userId).orElseThrow(() ->
                    new ServiceException(ServiceException.Code.NOT_FOUND, "Profile was not found"));
            User updated = User.restore(userId, old.getUsername(), old.getDisplayName(), name,
                    old.getPreferredPickupLocation().orElse(null));
            users.updateProfile(connection, updated);
            if (old.getProfileImage().isPresent()) {
                images.schedule(connection, old.getProfileImage().orElseThrow());
            }
            return updated;
        });
    }
}
