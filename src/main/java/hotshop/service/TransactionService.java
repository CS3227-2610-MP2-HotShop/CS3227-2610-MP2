package hotshop.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import hotshop.database.Database;
import hotshop.model.CancellationRequest;
import hotshop.model.Listing;
import hotshop.model.Transaction;
import hotshop.model.TransactionStatus;
import hotshop.repository.ListingRepository;
import hotshop.repository.OfferRepository;
import hotshop.repository.TransactionRepository;
import hotshop.repository.UserRepository;

/**
 * Agreed sales from both participants' side: completion confirmations, cancellation, and sales and
 * purchase history. Serialized with all other application services; every operation requires login
 * and acts as the session's current user. Only a sale's buyer and seller can see or act on it.
 */
public final class TransactionService {
    private static final String STORAGE_FAILURE = "Sales are unavailable right now. Please try again.";
    private final Database database;
    private final TransactionRepository transactions;
    private final ListingRepository listings;
    private final OfferRepository offers;
    private final UserRepository users;
    private final ServiceWorker worker;
    private final AuthenticatedSession session;
    private final Clock clock;

    /** Wires the shared database, worker, and session with the repositories sales touch. */
    public TransactionService(Database database, TransactionRepository transactions, ListingRepository listings,
            OfferRepository offers, UserRepository users, ServiceWorker worker, AuthenticatedSession session,
            Clock clock) {
        this.database = database;
        this.transactions = transactions;
        this.listings = listings;
        this.offers = offers;
        this.users = users;
        this.worker = worker;
        this.session = session;
        this.clock = clock;
    }

    /**
     * Records the current user's confirmation that the item changed hands. The second confirmation
     * completes the sale and marks its listing sold in the same database transaction.
     * NotificationService should notify the other participant inside this transaction when it exists.
     */
    public CompletableFuture<SaleForParticipant> confirmCompletion(UUID saleId) {
        return applyToActiveSale(saleId, (connection, sale, userId) -> {
            if (sale.getPendingCancellation().isPresent()) {
                throw ServiceException.invalidState("There is a pending cancellation request on this sale. "
                        + "Respond to it or withdraw it before confirming completion.");
            }
            if (sale.hasConfirmed(userId)) {
                throw ServiceException.invalidState(
                        "You have already confirmed this sale. Waiting for the other participant to confirm.");
            }
            sale.confirmCompletion(userId, eventTime(sale));
            if (sale.getStatus() == TransactionStatus.COMPLETED) {
                Listing listing = requireListing(connection, sale);
                listing.markSold();
                listings.update(connection, listing);
            }
        });
    }

    /**
     * Cancels an active sale before anyone confirms, releasing its listing for new offers. When they
     * exist, MeetupService cancels the sale's meetup and NotificationService notifies the other
     * participant inside this transaction.
     */
    public CompletableFuture<SaleForParticipant> cancelSale(UUID saleId) {
        return applyToActiveSale(saleId, (connection, sale, userId) -> {
            if (sale.hasConfirmation()) {
                throw ServiceException.invalidState("Completion has already been confirmed, so cancelling needs "
                        + "the other participant's agreement. Send a cancellation request instead.");
            }
            sale.cancel(userId, eventTime(sale));
            release(connection, sale);
        });
    }

    /**
     * After the first confirmation, asks the other participant to agree to cancel. While the request
     * is pending, the sale stays active and further confirmations are blocked.
     */
    public CompletableFuture<SaleForParticipant> requestCancellation(UUID saleId) {
        return applyToActiveSale(saleId, (connection, sale, userId) -> {
            if (!sale.hasConfirmation()) {
                throw ServiceException.invalidState(
                        "Nobody has confirmed completion yet, so you can cancel the sale directly instead.");
            }
            if (sale.getPendingCancellation().isPresent()) {
                throw ServiceException.invalidState("There is already a pending cancellation request on this sale.");
            }
            sale.requestCancellation(userId, eventTime(sale));
        });
    }

    /**
     * The other participant agrees to the pending request; the sale is cancelled and its listing
     * released. MeetupService and NotificationService hook in here as for {@link #cancelSale}.
     */
    public CompletableFuture<SaleForParticipant> acceptCancellation(UUID saleId) {
        return applyToActiveSale(saleId, (connection, sale, userId) -> {
            UUID requestId = requireResponder(sale, userId);
            sale.acceptCancellation(requestId, userId, eventTime(sale));
            release(connection, sale);
        });
    }

    /** The other participant declines the pending request; existing confirmations stay. */
    public CompletableFuture<SaleForParticipant> rejectCancellation(UUID saleId) {
        return applyToActiveSale(saleId, (connection, sale, userId) ->
                sale.rejectCancellation(requireResponder(sale, userId), userId, eventTime(sale)));
    }

    /** The requester takes back their pending request; existing confirmations stay. */
    public CompletableFuture<SaleForParticipant> withdrawCancellation(UUID saleId) {
        return applyToActiveSale(saleId, (connection, sale, userId) -> {
            var request = requirePendingRequest(sale);
            if (!request.getRequesterId().equals(userId)) {
                throw ServiceException.permission(
                        "Only the participant who made the cancellation request can withdraw it.");
            }
            sale.withdrawCancellation(request.getId(), userId, eventTime(sale));
        });
    }

    /** Every sale where the current user is the seller, needing action first, then newest first. */
    public CompletableFuture<List<SaleForParticipant>> getMySales() {
        return salesFor(transactions::findBySeller);
    }

    /** Every sale where the current user is the buyer, needing action first, then newest first. */
    public CompletableFuture<List<SaleForParticipant>> getMyPurchases() {
        return salesFor(transactions::findByBuyer);
    }

    /** The current user's seller summary; only completed sales count towards the total value. */
    public CompletableFuture<SalesDashboard> getSalesDashboard() {
        return worker.submit(() -> {
            UUID userId = session.requireUserId();
            return transaction(connection -> {
                List<Transaction> sales = transactions.findBySeller(connection, userId);
                List<Transaction> completed = sales.stream()
                        .filter(sale -> sale.getStatus() == TransactionStatus.COMPLETED).toList();
                int active = (int) sales.stream().filter(sale -> sale.getStatus() == TransactionStatus.ACTIVE).count();
                long total = completed.stream().mapToLong(Transaction::getAgreedPriceCents).sum();
                int pendingOffers = offers.countPendingByListingForSeller(connection, userId).values().stream()
                        .mapToInt(Integer::intValue).sum();
                return new SalesDashboard(pendingOffers, active,
                        completed.size(), total);
            });
        });
    }

    /** Finds the current user's sales from one side, buyer or seller. */
    @FunctionalInterface
    private interface SaleFinder {
        List<Transaction> find(Connection connection, UUID userId) throws SQLException;
    }

    private CompletableFuture<List<SaleForParticipant>> salesFor(SaleFinder finder) {
        return worker.submit(() -> {
            UUID userId = session.requireUserId();
            return transaction(connection -> forParticipant(connection, finder.find(connection, userId), userId));
        });
    }

    /** A change to one active sale, applied inside the operation's database transaction. */
    @FunctionalInterface
    private interface SaleChange {
        void apply(Connection connection, Transaction sale, UUID userId) throws SQLException;
    }

    /** Loads the sale, checks the current user takes part and that it is active, applies and saves the change. */
    private CompletableFuture<SaleForParticipant> applyToActiveSale(UUID saleId, SaleChange change) {
        return worker.submit(() -> {
            UUID userId = session.requireUserId();
            if (saleId == null) {
                throw ServiceException.validation("Choose a sale first.");
            }
            return transaction(connection -> {
                Transaction sale = transactions.findById(connection, saleId)
                        .orElseThrow(() -> ServiceException.notFound("This sale no longer exists."));
                if (!userId.equals(sale.getBuyerId()) && !userId.equals(sale.getSellerId())) {
                    throw ServiceException.permission("Only the buyer and seller of this sale can do that.");
                }
                if (sale.getStatus() != TransactionStatus.ACTIVE) {
                    throw ServiceException.invalidState("This sale is already "
                            + ServiceSupport.describe(sale.getStatus()) + ", so it can't be changed.");
                }
                change.apply(connection, sale, userId);
                transactions.update(connection, sale);
                return forParticipant(connection, sale, userId);
            });
        });
    }

    private static CancellationRequest requirePendingRequest(Transaction sale) {
        return sale.getPendingCancellation().orElseThrow(() ->
                ServiceException.invalidState("There is no pending cancellation request on this sale."));
    }

    /** Returns the pending request's ID after checking the current user is the one who must respond. */
    private static UUID requireResponder(Transaction sale, UUID userId) {
        CancellationRequest request = requirePendingRequest(sale);
        if (request.getRequesterId().equals(userId)) {
            throw ServiceException.permission("Only the other participant can respond to your cancellation "
                    + "request. You can withdraw it instead.");
        }
        return request.getId();
    }

    /** Releases a cancelled sale's listing so it can receive offers again. */
    private void release(Connection connection, Transaction sale) throws SQLException {
        Listing listing = requireListing(connection, sale);
        listing.release();
        listings.update(connection, listing);
    }

    private Listing requireListing(Connection connection, Transaction sale) throws SQLException {
        return listings.findById(connection, sale.getListingId())
                .orElseThrow(() -> new SQLException("Sale's listing is missing"));
    }

    private List<SaleForParticipant> forParticipant(Connection connection, List<Transaction> sales, UUID userId)
            throws SQLException {
        List<SaleForParticipant> results = new ArrayList<>();
        for (Transaction sale : sales) {
            results.add(forParticipant(connection, sale, userId));
        }
        results.sort(Comparator.comparingInt(result -> SaleProgress.rank(result.sale())));
        return results;
    }

    private SaleForParticipant forParticipant(Connection connection, Transaction sale, UUID userId)
            throws SQLException {
        boolean isBuyer = userId.equals(sale.getBuyerId());
        UUID otherId = isBuyer ? sale.getSellerId() : sale.getBuyerId();
        return new SaleForParticipant(sale, ServiceSupport.publicProfile(connection, users, otherId),
                isBuyer ? SaleRole.BUYER : SaleRole.SELLER, SaleProgress.nextStep(sale, userId),
                SaleProgress.availableActions(sale, userId));
    }

    /** Event times never precede the sale's last event, even if the system clock moved backwards. */
    private Instant eventTime(Transaction sale) {
        return ServiceSupport.latest(ServiceSupport.now(clock), sale.getLastEventAt());
    }

    private <T> T transaction(Database.Work<T> work) {
        return ServiceSupport.transaction(database, STORAGE_FAILURE, work);
    }
}
