package hotshop.service;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

import hotshop.database.Database;
import hotshop.repository.ImageCleanupRepository;
import hotshop.storage.ImageStorage;

/**
 * One feature's managed image namespace: imports files, queues retired files, and recovers
 * orphans. Coordinates the non-atomic filesystem/database boundary on the service worker.
 */
final class ManagedImages {
    private static final System.Logger LOGGER = System.getLogger(ManagedImages.class.getName());
    private final Database database;
    private final ImageStorage storage;
    private final String namespace;
    private final ImageStorage.Limits limits;
    private final References references;
    private final ImageCleanupRepository cleanup = new ImageCleanupRepository();

    /** Lists every filename in this namespace that saved records still reference. */
    @FunctionalInterface
    interface References {
        Set<String> find(Connection connection) throws SQLException;
    }

    ManagedImages(Database database, ImageStorage storage, String namespace, ImageStorage.Limits limits,
            References references) {
        this.database = database;
        this.storage = storage;
        this.namespace = namespace;
        this.limits = limits;
        this.references = references;
    }

    /** Copies a validated image into this namespace; invalid images are reported as validation failures. */
    String importImage(Path source) throws IOException {
        try {
            return storage.importImage(source, limits);
        } catch (IllegalArgumentException exception) {
            throw new ServiceException(ServiceException.Code.VALIDATION, exception.getMessage());
        }
    }

    /** Queues a retired file in the caller's transaction, so it is removed only after commit. */
    void schedule(Connection connection, String filename) throws SQLException {
        cleanup.schedule(connection, namespace, filename);
    }

    /** Discovers crash-created orphans in this namespace and retries durable cleanup. */
    void recover() throws SQLException, IOException {
        var files = storage.listManagedFiles();
        database.executeTransaction(connection -> {
            var referenced = references.find(connection);
            for (String name : files) {
                if (!referenced.contains(name)) {
                    cleanup.schedule(connection, namespace, name);
                }
            }
            return null;
        });
        for (String name : database.executeTransaction(connection -> cleanup.getPending(connection, namespace))) {
            database.executeTransaction(connection -> {
                if (!references.find(connection).contains(name)) {
                    try {
                        storage.delete(name);
                    } catch (IOException | IllegalArgumentException exception) {
                        LOGGER.log(System.Logger.Level.WARNING,
                                "Image cleanup in " + namespace + " will retry at startup", exception);
                        return null;
                    }
                }
                cleanup.finish(connection, namespace, name);
                return null;
            });
        }
    }

    /**
     * Recovers after an operation; failures are logged and retried at startup. Rechecking
     * references matters even if a commit succeeded but closing its connection failed.
     */
    void recoverSafely() {
        try {
            recover();
        } catch (SQLException | IOException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Image cleanup in " + namespace + " will retry at startup",
                    exception);
        }
    }
}
