package hotshop.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import hotshop.database.Database;
import hotshop.model.Listing;
import hotshop.model.ListingDetails;
import hotshop.model.ListingStatus;
import hotshop.model.Offer;
import hotshop.model.OfferStatus;
import hotshop.model.Transaction;
import hotshop.model.TransactionStatus;
import hotshop.repository.ListingRepository;
import hotshop.repository.OfferRepository;
import hotshop.repository.TransactionRepository;
import hotshop.repository.UserRepository;

/**
 * Buyer offers and the seller's decisions on them, serialized with all other application services.
 * Every operation requires login and acts as the session's current user. Only an offer's buyer and
 * the listing's seller can see it. Returned futures fail with ServiceException.
 */
public final class OfferService {
    private static final String STORAGE_FAILURE = "Offers are unavailable right now. Please try again.";
    private static final long MIN_AMOUNT_CENTS = 1;
    private static final int LIVE_SALE_FIRST = 0;
    private static final int CANCELLED_SALE_NEXT = 1;
    private static final int OTHER_OFFERS_LAST = 2;
    private final Database database;
    private final OfferRepository offers;
    private final ListingRepository listings;
    private final TransactionRepository transactions;
    private final UserRepository users;
    private final ServiceWorker worker;
    private final AuthenticatedSession session;
    private final Clock clock;

    /** Wires the shared database, worker, and session with the repositories offers touch. */
    public OfferService(Database database, OfferRepository offers, ListingRepository listings,
            TransactionRepository transactions, UserRepository users, ServiceWorker worker,
            AuthenticatedSession session, Clock clock) {
        this.database = database;
        this.offers = offers;
        this.listings = listings;
        this.transactions = transactions;
        this.users = users;
        this.worker = worker;
        this.session = session;
        this.clock = clock;
    }

    /**
     * Makes a pending offer on another seller's available listing. A buyer may have only one
     * pending offer per listing; changing the amount means withdrawing and offering again.
     */
    public CompletableFuture<OfferWithListing> submitOffer(UUID listingId, long amountCents) {
        return worker.submit(() -> {
            UUID buyerId = session.requireUserId();
            requireId(listingId, "Choose a listing to make an offer on.");
            if (amountCents < MIN_AMOUNT_CENTS || amountCents > ListingDetails.MAX_PRICE_CENTS) {
                throw ServiceException.validation("Offer amounts must be between "
                        + ServiceSupport.formatPrice(MIN_AMOUNT_CENTS) + " and "
                        + ServiceSupport.formatPrice(ListingDetails.MAX_PRICE_CENTS) + ".");
            }
            return transaction(connection -> {
                Listing listing = requireListing(connection, listingId);
                if (listing.getSellerId().equals(buyerId)) {
                    throw ServiceException.permission("You can't make an offer on your own listing.");
                }
                if (listing.getStatus() != ListingStatus.AVAILABLE) {
                    throw ServiceException.invalidState("This listing is "
                            + ServiceSupport.describe(listing.getStatus())
                            + ", so it can't receive offers.");
                }
                var existing = offers.findPending(connection, listingId, buyerId);
                if (existing.isPresent()) {
                    throw ServiceException.invalidState("You already have a pending offer of "
                            + ServiceSupport.formatPrice(existing.orElseThrow().getAmountCents())
                            + " on this listing. Withdraw it before making a new one.");
                }
                Offer offer = new Offer(listing, buyerId, amountCents, ServiceSupport.now(clock));
                offers.insert(connection, offer);
                return withListing(connection, offer);
            });
        });
    }

    /** Takes back the current user's own pending offer; it stays in history as withdrawn. */
    public CompletableFuture<OfferWithListing> withdrawOffer(UUID offerId) {
        return worker.submit(() -> {
            UUID buyerId = session.requireUserId();
            requireId(offerId, "Choose an offer to withdraw.");
            return transaction(connection -> {
                Offer offer = requireOffer(connection, offerId);
                if (!offer.getBuyerId().equals(buyerId)) {
                    throw ServiceException.permission("Only the buyer who made this offer can withdraw it.");
                }
                requirePending(offer, "withdrawn");
                offer.withdraw(closeTime(offer));
                offers.update(connection, offer);
                return withListing(connection, offer);
            });
        });
    }

    /** Every offer the current user has made, in any status, newest first. */
    public CompletableFuture<List<OfferWithListing>> getMyOffers() {
        return worker.submit(() -> {
            UUID buyerId = session.requireUserId();
            return transaction(connection -> {
                List<OfferWithListing> results = new ArrayList<>();
                for (Offer offer : offers.findByBuyer(connection, buyerId)) {
                    results.add(withListing(connection, offer));
                }
                return results;
            });
        });
    }

    /**
     * The seller's view of every offer on one of their listings: accepted offers with a live sale
     * first, then accepted offers whose sale was cancelled, then all others, each group newest first.
     */
    public CompletableFuture<List<OfferWithBuyer>> getOffersForListing(UUID listingId) {
        return worker.submit(() -> {
            UUID sellerId = session.requireUserId();
            requireId(listingId, "Choose a listing to see its offers.");
            return transaction(connection -> {
                requireSeller(requireListing(connection, listingId), sellerId, "view offers on");
                Map<UUID, TransactionStatus> sales = transactions.findStatusesByListing(connection, listingId);
                List<OfferWithBuyer> results = new ArrayList<>();
                for (Offer offer : offers.findByListing(connection, listingId)) {
                    results.add(new OfferWithBuyer(offer,
                            ServiceSupport.publicProfile(connection, users, offer.getBuyerId()),
                            Optional.ofNullable(sales.get(offer.getId()))));
                }
                results.sort(Comparator.comparingInt(OfferService::rank));
                return results;
            });
        });
    }

    /**
     * Seller only. In one database transaction: reserves the listing, accepts the offer, rejects
     * every other pending offer on it, and saves the new sale. NotificationService should add its
     * notifications inside this transaction when it exists.
     */
    public CompletableFuture<AcceptedOffer> acceptOffer(UUID offerId) {
        return worker.submit(() -> {
            UUID sellerId = session.requireUserId();
            requireId(offerId, "Choose an offer to accept.");
            return transaction(connection -> {
                Offer offer = requireOffer(connection, offerId);
                Listing listing = requireListing(connection, offer.getListingId());
                requireSeller(listing, sellerId, "accept offers on");
                requirePending(offer, "accepted");
                if (listing.getStatus() != ListingStatus.AVAILABLE) {
                    throw ServiceException.invalidState("This listing is "
                            + ServiceSupport.describe(listing.getStatus())
                            + ", so no offer can be accepted.");
                }
                Instant time = closeTime(offer);
                offer.accept(time);
                listing.reserve();
                offers.update(connection, offer);
                PendingOffers.rejectAllExcept(connection, offers, listing.getId(), offer.getId(), time);
                listings.update(connection, listing);
                Transaction sale = new Transaction(listing, offer, time);
                transactions.insert(connection, sale);
                return new AcceptedOffer(offer, listing, sale.getId());
            });
        });
    }

    /**
     * Seller only; declines one pending offer and leaves the listing available for others.
     * NotificationService should add its notification inside this transaction when it exists.
     */
    public CompletableFuture<OfferWithBuyer> rejectOffer(UUID offerId) {
        return worker.submit(() -> {
            UUID sellerId = session.requireUserId();
            requireId(offerId, "Choose an offer to reject.");
            return transaction(connection -> {
                Offer offer = requireOffer(connection, offerId);
                requireSeller(requireListing(connection, offer.getListingId()), sellerId, "reject offers on");
                requirePending(offer, "rejected");
                offer.reject(closeTime(offer));
                offers.update(connection, offer);
                return new OfferWithBuyer(offer, ServiceSupport.publicProfile(connection, users, offer.getBuyerId()),
                        Optional.empty());
            });
        });
    }

    /** Offers are already newest first; this stable ranking moves accepted offers to the top. */
    private static int rank(OfferWithBuyer result) {
        if (result.offer().getStatus() != OfferStatus.ACCEPTED) {
            return OTHER_OFFERS_LAST;
        }
        return result.saleStatus().orElse(TransactionStatus.ACTIVE) == TransactionStatus.CANCELLED
                ? CANCELLED_SALE_NEXT : LIVE_SALE_FIRST;
    }

    /** Closing times never precede the offer, even if the system clock moved backwards. */
    private Instant closeTime(Offer offer) {
        return ServiceSupport.latest(ServiceSupport.now(clock), offer.getCreatedAt());
    }

    private static void requireSeller(Listing listing, UUID userId, String action) {
        if (!listing.getSellerId().equals(userId)) {
            throw ServiceException.permission("Only the seller can " + action + " this listing.");
        }
    }

    private OfferWithListing withListing(Connection connection, Offer offer) throws SQLException {
        Listing listing = requireListing(connection, offer.getListingId());
        var seller = ServiceSupport.publicProfile(connection, users, listing.getSellerId());
        return new OfferWithListing(offer, new ListingWithSeller(listing, seller),
                transactions.findStatusByOffer(connection, offer.getId()));
    }

    private Listing requireListing(Connection connection, UUID id) throws SQLException {
        return listings.findById(connection, id)
                .orElseThrow(() -> ServiceException.notFound("This listing no longer exists."));
    }

    private Offer requireOffer(Connection connection, UUID id) throws SQLException {
        return offers.findById(connection, id)
                .orElseThrow(() -> ServiceException.notFound("This offer no longer exists."));
    }

    /** Names the actual status so the user knows why the action is refused. */
    private static void requirePending(Offer offer, String action) {
        if (offer.getStatus() != OfferStatus.PENDING) {
            throw ServiceException.invalidState("This offer was already " + ServiceSupport.describe(offer.getStatus())
                    + ", so it can't be " + action + ".");
        }
    }

    private static void requireId(UUID id, String message) {
        if (id == null) {
            throw ServiceException.validation(message);
        }
    }

    private <T> T transaction(Database.Work<T> work) {
        return ServiceSupport.transaction(database, STORAGE_FAILURE, work);
    }
}
