package hotshop.service;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import hotshop.repository.MeetupRepository;
import hotshop.repository.OfferRepository;
import hotshop.repository.TransactionRepository;
import hotshop.repository.UserRepository;
import hotshop.storage.ImageStorage;

/**
 * Seller listing management and buyer listing lookup, serialized with all other application
 * services. Every operation requires login and acts as the session's current user. Returned
 * futures fail with ServiceException; joining wraps it in CompletionException.
 */
public final class ListingService {
    private static final String STORAGE_FAILURE = "Listings are unavailable right now. Please try again.";
    /** Reserved listings come first because they await a physical handover and confirmation. */
    private static final List<ListingStatus> MY_LISTINGS_ORDER = List.of(ListingStatus.RESERVED,
            ListingStatus.AVAILABLE, ListingStatus.SOLD, ListingStatus.ARCHIVED);
    private final Database database;
    private final ListingRepository listings;
    private final OfferRepository offers;
    private final TransactionRepository transactions;
    private final MeetupRepository meetups;
    private final UserRepository users;
    private final ServiceWorker worker;
    private final AuthenticatedSession session;
    private final ManagedImages images;
    private final Clock clock;

    /** Wires the shared database, worker, and session with the listing-specific managed image namespace. */
    public ListingService(Database database, ListingRepository listings, OfferRepository offers,
            TransactionRepository transactions, MeetupRepository meetups, UserRepository users, ServiceWorker worker,
            AuthenticatedSession session, ImageStorage storage, Clock clock) {
        this.database = database;
        this.listings = listings;
        this.offers = offers;
        this.transactions = transactions;
        this.meetups = meetups;
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
                Listing listing = new Listing(sellerId, details, saved, ServiceSupport.now(clock));
                return transaction(connection -> {
                    listings.insert(connection, listing);
                    return withSeller(connection, listing);
                });
            });
        });
    }

    /**
     * Returns the current user's listings in every status with their pending offer counts: reserved
     * first (awaiting handover), then available, sold, and archived, each newest first. Reserved
     * listings also carry their active sale's meetup summary.
     */
    public CompletableFuture<List<OwnListing>> getMyListings() {
        return submit(() -> {
            UUID sellerId = session.requireUserId();
            Instant now = ServiceSupport.now(clock);
            return transaction(connection -> {
                PublicProfile seller = ServiceSupport.publicProfile(connection, users, sellerId);
                Map<UUID, Integer> pending = offers.countPendingByListingForSeller(connection, sellerId);
                List<OwnListing> results = new ArrayList<>();
                for (Listing listing : listings.findBySeller(connection, sellerId).stream()
                        .sorted(Comparator.comparingInt(listing -> MY_LISTINGS_ORDER.indexOf(listing.getStatus())))
                        .toList()) {
                    results.add(new OwnListing(new ListingWithSeller(listing, seller),
                            pending.getOrDefault(listing.getId(), 0), reservedMeetup(connection, listing, now)));
                }
                return results;
            });
        });
    }

    /** Available listings on a public profile, newest first; private seller counts are excluded. */
    public CompletableFuture<List<ListingWithSeller>> getPublicListings(UUID sellerId) {
        return submit(() -> {
            session.requireUserId();
            if (sellerId == null) {
                throw ServiceException.validation("Choose a profile first.");
            }
            return transaction(connection -> {
                PublicProfile seller = PublicProfile.of(users.findById(connection, sellerId)
                        .orElseThrow(() -> ServiceException.notFound("This profile no longer exists.")));
                return listings.findBySeller(connection, sellerId).stream()
                        .filter(listing -> listing.getStatus() == ListingStatus.AVAILABLE)
                        .map(listing -> new ListingWithSeller(listing, seller)).toList();
            });
        });
    }

    /** The active sale's meetup summary for a reserved listing; other listings have none. */
    private Optional<MeetupSummary> reservedMeetup(Connection connection, Listing listing, Instant now)
            throws SQLException {
        if (listing.getStatus() != ListingStatus.RESERVED) {
            return Optional.empty();
        }
        Optional<UUID> sale = transactions.findActiveIdForListing(connection, listing.getId());
        return sale.isPresent()
                ? Optional.of(SaleMeetups.load(connection, meetups, sale.orElseThrow(), now)) : Optional.empty();
    }

    /** Returns any existing listing in any status; deleted and unknown listings are not found. */
    public CompletableFuture<ListingWithSeller> getListing(UUID id) {
        return submit(() -> {
            session.requireUserId();
            requireId(id);
            return transaction(connection -> withSeller(connection, requireListing(connection, id)));
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
            return transaction(connection -> {
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
     * An actual change advances the update time and rejects every pending offer in the same
     * transaction; an unchanged save changes nothing. Removed photos are retired after commit.
     * Permission and status are checked before importing photos and again when saving.
     */
    public CompletableFuture<ListingWithSeller> updateListing(UUID id, ListingDraft draft, List<ListingPhoto> photos) {
        return submit(() -> {
            UUID userId = session.requireUserId();
            requireId(id);
            ListingDetails details = toDetails(draft);
            List<ListingImage> previous = transaction(connection ->
                    requireEditable(connection, id, userId)).getImages();
            return withPhotoRecovery(photos, () -> {
                List<ListingImage> saved = resolvePhotos(photos, previous);
                Set<String> keptNames = new HashSet<>();
                saved.forEach(image -> keptNames.add(image.filename()));
                return transaction(connection -> {
                    Listing listing = requireEditable(connection, id, userId);
                    Instant time = ServiceSupport.latest(ServiceSupport.now(clock), listing.getUpdatedAt());
                    if (listing.update(details, saved, time)) {
                        listings.update(connection, listing);
                        PendingOffers.rejectAll(connection, offers, id, time);
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
     * Every pending offer is rejected in the same transaction.
     */
    public CompletableFuture<ListingWithSeller> archiveListing(UUID id) {
        return submit(() -> {
            UUID userId = session.requireUserId();
            requireId(id);
            return transaction(connection -> {
                Listing listing = requireOwned(connection, id, userId);
                if (listing.getStatus() == ListingStatus.ARCHIVED) {
                    throw ServiceException.invalidState("This listing is already archived.");
                }
                if (listing.getStatus() == ListingStatus.RESERVED) {
                    throw ServiceException.invalidState("This listing is reserved, so it can't be archived "
                            + "until its sale is completed or cancelled.");
                }
                listing.archive();
                listings.update(connection, listing);
                PendingOffers.rejectAll(connection, offers, id, ServiceSupport.now(clock));
                return withSeller(connection, listing);
            });
        });
    }

    /**
     * Owner only; permanently removes an available or archived listing that has never received an
     * offer, and retires its photos. ChatService must also refuse listings with conversation history.
     */
    public CompletableFuture<Void> deleteListing(UUID id) {
        return submit(() -> {
            UUID userId = session.requireUserId();
            requireId(id);
            Listing deleted = transaction(connection -> {
                Listing listing = requireOwned(connection, id, userId);
                if (!listing.isDeletable()) {
                    throw ServiceException.invalidState("This listing is " + ServiceSupport.describe(
                            listing.getStatus()) + ", so it can't be deleted.");
                }
                if (offers.existsForListing(connection, id)) {
                    throw ServiceException.invalidState(
                            "This listing has offer history, so it can't be deleted. Archive it instead.");
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
                throw new ServiceException(ServiceException.Code.STORAGE,
                        "Your photos couldn't be saved. Please try again.", exception);
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
            throw ServiceException.validation("Every photo entry must be a new file or one of this listing's photos.");
        }
        if (photos.size() > Listing.MAX_IMAGES) {
            throw ServiceException.validation("A listing can have at most " + Listing.MAX_IMAGES
                    + " photos, but " + photos.size() + " were chosen.");
        }
        Set<String> currentNames = new HashSet<>();
        current.forEach(image -> currentNames.add(image.filename()));
        Set<String> kept = new HashSet<>();
        for (ListingPhoto photo : photos) {
            if (photo instanceof ListingPhoto.Existing existing
                    && (!currentNames.contains(existing.filename()) || !kept.add(existing.filename()))) {
                throw ServiceException.validation(
                        "A kept photo doesn't belong to this listing or appears more than once.");
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

    /**
     * Model messages name the field and its limits. A missing value's exception names only the
     * field, so it is reported as required; an unnamed one falls back to a general message.
     */
    private ListingDetails toDetails(ListingDraft draft) {
        if (draft == null) {
            throw ServiceException.validation("Enter the listing details first.");
        }
        try {
            return new ListingDetails(draft.title(), draft.description(), draft.category(), draft.priceCents(),
                    draft.condition(), draft.pickupLocation());
        } catch (IllegalArgumentException exception) {
            throw ServiceException.validation(exception.getMessage() + ".");
        } catch (NullPointerException exception) {
            String field = exception.getMessage();
            throw ServiceException.validation(field == null
                    ? "Fill in every listing detail." : field + " is required.");
        }
    }

    private Listing requireListing(Connection connection, UUID id) throws SQLException {
        return listings.findById(connection, id)
                .orElseThrow(() -> ServiceException.notFound("This listing no longer exists."));
    }

    /** Permission is checked in the service even though screens only offer these actions to owners. */
    private Listing requireOwned(Connection connection, UUID id, UUID userId) throws SQLException {
        Listing listing = requireListing(connection, id);
        if (!listing.getSellerId().equals(userId)) {
            throw ServiceException.permission("Only the seller can change this listing.");
        }
        return listing;
    }

    private Listing requireEditable(Connection connection, UUID id, UUID userId) throws SQLException {
        Listing listing = requireOwned(connection, id, userId);
        if (listing.getStatus() != ListingStatus.AVAILABLE) {
            throw ServiceException.invalidState("This listing is " + ServiceSupport.describe(listing.getStatus())
                    + ", so it can't be edited.");
        }
        return listing;
    }

    private ListingWithSeller withSeller(Connection connection, Listing listing) throws SQLException {
        return new ListingWithSeller(listing, ServiceSupport.publicProfile(connection, users, listing.getSellerId()));
    }

    private static void requireValid(ListingSearch search) {
        if (search == null) {
            throw ServiceException.validation("Choose what to search for first.");
        }
        requirePriceBound(search.minPriceCents());
        requirePriceBound(search.maxPriceCents());
        if (search.minPriceCents() != null && search.maxPriceCents() != null
                && search.minPriceCents() > search.maxPriceCents()) {
            throw ServiceException.validation("The minimum price (" + ServiceSupport.formatPrice(search.minPriceCents())
                    + ") is higher than the maximum price (" + ServiceSupport.formatPrice(search.maxPriceCents())
                    + ").");
        }
    }

    private static void requirePriceBound(Long priceCents) {
        if (priceCents != null && (priceCents < 0 || priceCents > ListingDetails.MAX_PRICE_CENTS)) {
            throw ServiceException.validation("Price filters must be between " + ServiceSupport.formatPrice(0)
                    + " and " + ServiceSupport.formatPrice(ListingDetails.MAX_PRICE_CENTS) + ".");
        }
    }

    private static void requireId(UUID id) {
        if (id == null) {
            throw ServiceException.validation("Choose a listing first.");
        }
    }

    private <T> T transaction(Database.Work<T> work) {
        return ServiceSupport.transaction(database, STORAGE_FAILURE, work);
    }
}
