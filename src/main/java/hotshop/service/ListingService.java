package hotshop.service;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import hotshop.database.Database;
import hotshop.model.Listing;
import hotshop.model.ListingDetails;
import hotshop.model.ListingImage;
import hotshop.model.ListingStatus;
import hotshop.repository.ListingRepository;
import hotshop.repository.UserRepository;
import hotshop.storage.ImageStorage;

/**
 * Seller listing management and buyer listing lookup, serialized with all other application
 * services. Every operation requires login and acts as the session's current user. Returned
 * futures fail with ServiceException; joining wraps it in CompletionException.
 */
public final class ListingService {
    private final Database database;
    private final ListingRepository listings;
    private final UserRepository users;
    private final ServiceWorker worker;
    private final AuthenticatedSession session;
    private final ManagedImages images;
    private final Clock clock;

    /** Wires the shared database, worker, and session with the listing-specific managed image namespace. */
    public ListingService(Database database, ListingRepository listings, UserRepository users, ServiceWorker worker,
            AuthenticatedSession session, ImageStorage storage, Clock clock) {
        this.database = database;
        this.listings = listings;
        this.users = users;
        this.worker = worker;
        this.session = session;
        this.clock = clock;
        images = new ManagedImages(database, storage, "listings", ImageStorage.LISTING_LIMITS,
                listings::getReferencedImages);
    }

    /** Lifecycle maintenance, queued with other operations; does not require login. */
    public CompletableFuture<Void> recoverImages() {
        return worker.submit(() -> {
            try {
                images.recover();
            } catch (SQLException | IOException exception) {
                throw new ServiceException(ServiceException.Code.STORAGE, "Image storage is unavailable", exception);
            }
            return null;
        });
    }

    /** Saves a new available listing owned by the current user, importing 0 to 10 new photos. */
    public CompletableFuture<ListingWithSeller> createListing(ListingDraft draft, List<ListingPhoto> photos) {
        return submit(() -> {
            UUID sellerId = session.requireUserId();
            ListingDetails details = toDetails(draft);
            return withPhotoRecovery(photos, () -> {
                List<ListingImage> saved = resolvePhotos(photos, List.of());
                Listing listing = new Listing(sellerId, details, saved, now());
                return executeTransaction(connection -> {
                    listings.insert(connection, listing);
                    return withSeller(connection, listing);
                });
            });
        });
    }

    /** Returns the current user's listings in every status, newest first. */
    public CompletableFuture<List<ListingWithSeller>> getMyListings() {
        return submit(() -> {
            UUID sellerId = session.requireUserId();
            return executeTransaction(connection -> {
                PublicProfile seller = requireSeller(connection, sellerId);
                return listings.findBySeller(connection, sellerId).stream()
                        .map(listing -> new ListingWithSeller(listing, seller)).toList();
            });
        });
    }

    /** Returns any existing listing in any status; deleted and unknown listings are not found. */
    public CompletableFuture<ListingWithSeller> getListing(UUID id) {
        return submit(() -> {
            session.requireUserId();
            requireId(id);
            return executeTransaction(connection -> withSeller(connection, requireListing(connection, id)));
        });
    }

    /**
     * Buyer search over other sellers' available listings. Reserved, sold, and archived listings
     * and the current user's own listings never appear. Results are not paginated.
     */
    public CompletableFuture<List<ListingWithSeller>> searchListings(ListingSearch search) {
        return submit(() -> {
            UUID userId = session.requireUserId();
            requireValid(search);
            return executeTransaction(connection -> {
                List<ListingWithSeller> results = new ArrayList<>();
                for (Listing listing : listings.findAvailableExcludingSeller(connection, userId).stream()
                        .filter(search::matches).sorted(search.order()).toList()) {
                    results.add(withSeller(connection, listing));
                }
                return results;
            });
        });
    }

    /**
     * Owner only, while available: replaces details and the complete ordered photo list together.
     * Only an actual change advances the update time. Removed photos are retired after commit.
     * Permission and status are checked before importing photos, then again in the saving
     * transaction; OfferService must reject pending offers in that transaction when it exists.
     */
    public CompletableFuture<ListingWithSeller> updateListing(UUID id, ListingDraft draft, List<ListingPhoto> photos) {
        return submit(() -> {
            UUID userId = session.requireUserId();
            requireId(id);
            ListingDetails details = toDetails(draft);
            List<ListingImage> previous = executeTransaction(connection ->
                    requireEditable(connection, id, userId)).getImages();
            return withPhotoRecovery(photos, () -> {
                List<ListingImage> saved = resolvePhotos(photos, previous);
                Set<String> keptNames = new HashSet<>();
                saved.forEach(image -> keptNames.add(image.filename()));
                return executeTransaction(connection -> {
                    Listing listing = requireEditable(connection, id, userId);
                    if (listing.update(details, saved, latest(now(), listing.getUpdatedAt()))) {
                        listings.update(connection, listing);
                        for (ListingImage image : previous) {
                            if (!keptNames.contains(image.filename())) {
                                images.schedule(connection, image.filename());
                            }
                        }
                    }
                    return withSeller(connection, listing);
                });
            });
        });
    }

    /**
     * Owner only; available or sold listings leave browsing but stay visible by ID and in My Listings.
     * OfferService must reject pending offers here when it exists.
     */
    public CompletableFuture<ListingWithSeller> archiveListing(UUID id) {
        return submit(() -> {
            UUID userId = session.requireUserId();
            requireId(id);
            return executeTransaction(connection -> {
                Listing listing = requireOwned(connection, id, userId);
                try {
                    listing.archive();
                } catch (IllegalStateException exception) {
                    throw invalidState("Only available or sold listings can be archived");
                }
                listings.update(connection, listing);
                return withSeller(connection, listing);
            });
        });
    }

    /**
     * Owner only; permanently removes an available or archived listing and retires its photos.
     * OfferService and ChatService must also refuse listings with offer or conversation history.
     */
    public CompletableFuture<Void> deleteListing(UUID id) {
        return submit(() -> {
            UUID userId = session.requireUserId();
            requireId(id);
            Listing deleted = executeTransaction(connection -> {
                Listing listing = requireOwned(connection, id, userId);
                if (!listing.isDeletable()) {
                    throw invalidState("Only available or archived listings can be deleted");
                }
                for (ListingImage image : listing.getImages()) {
                    images.schedule(connection, image.filename());
                }
                listings.delete(connection, id);
                return listing;
            });
            if (!deleted.getImages().isEmpty()) {
                images.recoverSafely();
            }
            return null;
        });
    }

    private <T> CompletableFuture<T> submit(Callable<T> operation) {
        return worker.submit(() -> {
            try {
                return operation.call();
            } catch (IOException exception) {
                throw new ServiceException(ServiceException.Code.STORAGE, "Unable to save listing photos", exception);
            }
        });
    }

    /** Runs work that may import photos, then removes any imported file that did not get saved. */
    private <T> T withPhotoRecovery(List<ListingPhoto> photos, Callable<T> work) throws Exception {
        boolean importsFiles = photos != null && photos.stream().anyMatch(ListingPhoto.NewFile.class::isInstance);
        try {
            return work.call();
        } finally {
            if (importsFiles) {
                images.recoverSafely();
            }
        }
    }

    /**
     * Turns the requested ordered photo list into saved images, importing new files. Kept photos
     * must already belong to the listing, and each photo may appear only once.
     */
    private List<ListingImage> resolvePhotos(List<ListingPhoto> photos, List<ListingImage> current)
            throws IOException {
        if (photos == null || photos.stream().anyMatch(Objects::isNull)) {
            throw validation("Photo list must not contain empty entries");
        }
        if (photos.size() > Listing.MAX_IMAGES) {
            throw validation("A listing may have at most " + Listing.MAX_IMAGES + " photos");
        }
        Set<String> currentNames = new HashSet<>();
        current.forEach(image -> currentNames.add(image.filename()));
        Set<String> kept = new HashSet<>();
        for (ListingPhoto photo : photos) {
            if (photo instanceof ListingPhoto.Existing existing
                    && (!currentNames.contains(existing.filename()) || !kept.add(existing.filename()))) {
                throw validation("Kept photos must belong to this listing and appear once");
            }
        }
        List<ListingImage> result = new ArrayList<>();
        for (ListingPhoto photo : photos) {
            String name = switch (photo) {
                case ListingPhoto.Existing existing -> existing.filename();
                case ListingPhoto.NewFile file -> images.importImage(file.source());
            };
            result.add(new ListingImage(name, result.size()));
        }
        return result;
    }

    private ListingDetails toDetails(ListingDraft draft) {
        if (draft == null) {
            throw validation("Listing details are required");
        }
        try {
            return new ListingDetails(draft.title(), draft.description(), draft.category(), draft.priceCents(),
                    draft.condition(), draft.pickupLocation());
        } catch (IllegalArgumentException exception) {
            throw validation(exception.getMessage());
        } catch (NullPointerException exception) {
            throw validation("Every listing detail is required");
        }
    }

    private Listing requireListing(Connection connection, UUID id) throws SQLException {
        return listings.findById(connection, id).orElseThrow(() ->
                new ServiceException(ServiceException.Code.NOT_FOUND, "Listing was not found"));
    }

    /** Permission is checked in the service even though screens only offer these actions to owners. */
    private Listing requireOwned(Connection connection, UUID id, UUID userId) throws SQLException {
        Listing listing = requireListing(connection, id);
        if (!listing.getSellerId().equals(userId)) {
            throw new ServiceException(ServiceException.Code.PERMISSION, "You can only change your own listings");
        }
        return listing;
    }

    private Listing requireEditable(Connection connection, UUID id, UUID userId) throws SQLException {
        Listing listing = requireOwned(connection, id, userId);
        if (listing.getStatus() != ListingStatus.AVAILABLE) {
            throw invalidState("Only available listings can be edited");
        }
        return listing;
    }

    private ListingWithSeller withSeller(Connection connection, Listing listing) throws SQLException {
        return new ListingWithSeller(listing, requireSeller(connection, listing.getSellerId()));
    }

    private PublicProfile requireSeller(Connection connection, UUID sellerId) throws SQLException {
        return PublicProfile.of(users.findById(connection, sellerId)
                .orElseThrow(() -> new SQLException("Listing seller is missing")));
    }

    private static void requireValid(ListingSearch search) {
        if (search == null) {
            throw validation("Search criteria are required");
        }
        requirePriceBound(search.minPriceCents());
        requirePriceBound(search.maxPriceCents());
        if (search.minPriceCents() != null && search.maxPriceCents() != null
                && search.minPriceCents() > search.maxPriceCents()) {
            throw validation("Minimum price cannot exceed maximum price");
        }
    }

    private static void requirePriceBound(Long priceCents) {
        if (priceCents != null && (priceCents < 0 || priceCents > ListingDetails.MAX_PRICE_CENTS)) {
            throw validation("Price filters must be between 0 and S$1,000,000");
        }
    }

    private static void requireId(UUID id) {
        if (id == null) {
            throw validation("Listing ID is required");
        }
    }

    private static ServiceException validation(String message) {
        return new ServiceException(ServiceException.Code.VALIDATION, message);
    }

    private static ServiceException invalidState(String message) {
        return new ServiceException(ServiceException.Code.INVALID_STATE, message);
    }

    /** Guards against the system clock moving backwards between edits. */
    private static Instant latest(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }

    /** SQLite stores milliseconds, so times are truncated to match what a restart restores. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private <T> T executeTransaction(Database.Work<T> work) {
        try {
            return database.executeTransaction(work);
        } catch (SQLException exception) {
            throw new ServiceException(ServiceException.Code.STORAGE, "Listing storage is unavailable", exception);
        }
    }
}
