package hotshop;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.time.Clock;
import java.util.concurrent.CompletionException;

import hotshop.database.Database;
import hotshop.repository.ListingRepository;
import hotshop.repository.UserRepository;
import hotshop.service.AccountService;
import hotshop.service.AuthenticatedSession;
import hotshop.service.ListingService;
import hotshop.service.ServiceWorker;
import hotshop.storage.ImageStorage;

/** Owns application resources for exactly one local data directory. */
public final class ApplicationRuntime implements AutoCloseable {
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final ServiceWorker worker = new ServiceWorker();
    private final AccountService accounts;
    private final ListingService listings;
    private boolean isClosed;

    private ApplicationRuntime(FileChannel lockChannel, FileLock lock, Database database, ImageStorage profileImages,
            ImageStorage listingImages, Clock clock) {
        this.lockChannel = lockChannel;
        this.lock = lock;
        UserRepository users = new UserRepository();
        AuthenticatedSession session = new AuthenticatedSession();
        accounts = new AccountService(database, users, worker, session, profileImages);
        listings = new ListingService(database, new ListingRepository(), users, worker, session, listingImages, clock);
    }

    public AccountService getAccounts() {
        return accounts;
    }

    public ListingService getListings() {
        return listings;
    }

    /** Locks and initializes the data directory using the system clock. */
    public static ApplicationRuntime open(Path directory) throws IOException, SQLException {
        return open(directory, Clock.systemUTC());
    }

    /** Locks and initializes the data directory; failure preserves data and releases acquired resources. */
    public static ApplicationRuntime open(Path directory, Clock clock) throws IOException, SQLException {
        Files.createDirectories(directory);
        FileChannel channel = FileChannel.open(directory.resolve("application.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException exception) {
                throw new IOException("HotShop is already using this data directory", exception);
            }
            if (lock == null) {
                throw new IOException("HotShop is already using this data directory");
            }
            Database database = new Database(directory.resolve("marketplace.db"));
            database.migrate();
            ImageStorage profileImages = new ImageStorage(directory.resolve("images/profiles"));
            ImageStorage listingImages = new ImageStorage(directory.resolve("images/listings"));
            ApplicationRuntime runtime = new ApplicationRuntime(channel, lock, database, profileImages,
                    listingImages, clock);
            try {
                runtime.accounts.recoverImages().join();
                runtime.listings.recoverImages().join();
            } catch (CompletionException exception) {
                runtime.close();
                throw new IOException("Unable to initialize image storage", exception.getCause());
            }
            return runtime;
        } catch (IOException | SQLException | RuntimeException failure) {
            channel.close();
            throw failure;
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (isClosed) {
            return;
        }
        worker.close();
        try {
            lock.release();
        } finally {
            lockChannel.close();
            isClosed = true;
        }
    }
}
