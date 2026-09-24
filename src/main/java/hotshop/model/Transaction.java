package hotshop.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An agreed sale with independent participant confirmations. Actor IDs must come from
 * the authenticated service; services also coordinate the corresponding listing update.
 */
public final class Transaction {
    private final UUID id;
    private final UUID listingId;
    private final UUID acceptedOfferId;
    private final UUID buyerId;
    private final UUID sellerId;
    private final long agreedPriceCents;
    private final String listingTitle;
    private final String listingDescription;
    private final Condition listingCondition;
    private final Instant createdAt;
    private final List<CancellationRequest> cancellationRequests = new ArrayList<>();
    private TransactionStatus status;
    private Instant buyerConfirmedAt;
    private Instant sellerConfirmedAt;
    private Instant cancelledAt;
    private UUID cancelledBy;
    private Instant lastEventAt;

    /** Captures an accepted offer and its reserved listing without retaining either mutable object. */
    public Transaction(Listing listing, Offer offer, Instant createdAt) {
        id = UUID.randomUUID();
        status = TransactionStatus.ACTIVE;
        Objects.requireNonNull(listing, "Listing");
        Objects.requireNonNull(offer, "Offer");
        this.createdAt = Objects.requireNonNull(createdAt, "Creation time");
        lastEventAt = createdAt;
        if (!listing.getId().equals(offer.getListingId())) {
            throw new IllegalArgumentException("Offer must refer to this listing");
        }
        if (offer.getStatus() != OfferStatus.ACCEPTED || listing.getStatus() != ListingStatus.RESERVED) {
            throw new IllegalStateException("Transaction requires an accepted offer and reserved listing");
        }
        listingId = listing.getId();
        acceptedOfferId = offer.getId();
        buyerId = offer.getBuyerId();
        sellerId = listing.getSellerId();
        agreedPriceCents = offer.getAmountCents();
        listingTitle = listing.getDetails().title();
        listingDescription = listing.getDetails().description();
        listingCondition = listing.getDetails().condition();
    }

    /** Every persisted field of a sale, used to restore it; optional fields are null when absent. */
    public record Snapshot(UUID id, UUID listingId, UUID acceptedOfferId, UUID buyerId, UUID sellerId,
            long agreedPriceCents, String listingTitle, String listingDescription, Condition listingCondition,
            Instant createdAt, TransactionStatus status, Instant buyerConfirmedAt, Instant sellerConfirmedAt,
            Instant cancelledAt, UUID cancelledBy, List<CancellationRequest> cancellationRequests) {
    }

    private Transaction(Snapshot saved) {
        id = Objects.requireNonNull(saved.id(), "Transaction ID");
        listingId = Objects.requireNonNull(saved.listingId(), "Listing ID");
        acceptedOfferId = Objects.requireNonNull(saved.acceptedOfferId(), "Accepted offer ID");
        buyerId = Objects.requireNonNull(saved.buyerId(), "Buyer ID");
        sellerId = Objects.requireNonNull(saved.sellerId(), "Seller ID");
        agreedPriceCents = saved.agreedPriceCents();
        listingTitle = Objects.requireNonNull(saved.listingTitle(), "Listing title");
        listingDescription = Objects.requireNonNull(saved.listingDescription(), "Listing description");
        listingCondition = Objects.requireNonNull(saved.listingCondition(), "Listing condition");
        createdAt = Objects.requireNonNull(saved.createdAt(), "Creation time");
        status = Objects.requireNonNull(saved.status(), "Transaction status");
        buyerConfirmedAt = saved.buyerConfirmedAt();
        sellerConfirmedAt = saved.sellerConfirmedAt();
        cancelledAt = saved.cancelledAt();
        cancelledBy = saved.cancelledBy();
        cancellationRequests.addAll(saved.cancellationRequests());
        lastEventAt = createdAt;
        validateRestoredState();
    }

    /**
     * Restores a persisted sale, rejecting combinations the live model could never produce. The
     * last-event time that orders later actions is derived from the saved times.
     */
    public static Transaction restore(Snapshot saved) {
        Objects.requireNonNull(saved, "Saved transaction");
        return new Transaction(saved);
    }

    private void validateRestoredState() {
        if (buyerId.equals(sellerId) || agreedPriceCents <= 0 || agreedPriceCents > ListingDetails.MAX_PRICE_CENTS) {
            throw new IllegalArgumentException("Saved sale has invalid participants or price");
        }
        if ((status == TransactionStatus.COMPLETED) != (buyerConfirmedAt != null && sellerConfirmedAt != null)) {
            throw new IllegalArgumentException("Only a completed sale has both confirmations");
        }
        if ((status == TransactionStatus.CANCELLED) != (cancelledAt != null && cancelledBy != null)
                || (status != TransactionStatus.CANCELLED && (cancelledAt != null || cancelledBy != null))) {
            throw new IllegalArgumentException("Only a cancelled sale records who cancelled and when");
        }
        if (cancelledBy != null) {
            requireParticipant(cancelledBy);
        }
        if (!cancellationRequests.isEmpty() && !hasConfirmation()) {
            throw new IllegalArgumentException("Cancellation requests require an earlier confirmation");
        }
        for (int i = 0; i < cancellationRequests.size(); i++) {
            validateRestoredRequest(cancellationRequests.get(i), i == cancellationRequests.size() - 1);
        }
        boolean isCancelledByRequest = !cancellationRequests.isEmpty()
                && cancellationRequests.getLast().getStatus() == CancellationStatus.ACCEPTED;
        if (status == TransactionStatus.CANCELLED && hasConfirmation() && !isCancelledByRequest) {
            throw new IllegalArgumentException("A confirmed sale can only be cancelled by an accepted request");
        }
        if (isCancelledByRequest && !cancellationRequests.getLast().getRequesterId().equals(cancelledBy)) {
            throw new IllegalArgumentException("An accepted request's requester is who cancelled the sale");
        }
        advanceLastEvent(buyerConfirmedAt);
        advanceLastEvent(sellerConfirmedAt);
        advanceLastEvent(cancelledAt);
    }

    /**
     * Requests must belong to this sale, be made by a participant, and follow one another in time.
     * Only the latest may be pending (on an active sale) or accepted (which cancels the sale).
     */
    private void validateRestoredRequest(CancellationRequest request, boolean isLast) {
        if (!request.getTransactionId().equals(id)) {
            throw new IllegalArgumentException("Cancellation request belongs to another sale");
        }
        requireParticipant(request.getRequesterId());
        if (request.getCreatedAt().isBefore(lastEventAt)) {
            throw new IllegalArgumentException("Cancellation requests are out of order");
        }
        boolean mayBePending = isLast && status == TransactionStatus.ACTIVE;
        boolean mayBeAccepted = isLast && status == TransactionStatus.CANCELLED;
        if ((request.getStatus() == CancellationStatus.PENDING && !mayBePending)
                || (request.getStatus() == CancellationStatus.ACCEPTED && !mayBeAccepted)) {
            throw new IllegalArgumentException("Request status does not fit the sale's history");
        }
        advanceLastEvent(request.getCreatedAt());
        request.getResolvedAt().ifPresent(this::advanceLastEvent);
    }

    private void advanceLastEvent(Instant time) {
        if (time == null) {
            return;
        }
        if (time.isBefore(createdAt)) {
            throw new IllegalArgumentException("Saved event precedes the sale");
        }
        if (time.isAfter(lastEventAt)) {
            lastEventAt = time;
        }
    }

    /** Records one participant's confirmation; the second completes this transaction. */
    public void confirmCompletion(UUID actorId, Instant confirmedAt) {
        requireActive();
        requireParticipant(actorId);
        requireTime(confirmedAt);
        if (hasPendingCancellation()) {
            throw new IllegalStateException("Resolve the pending cancellation before confirming completion");
        }
        if (actorId.equals(buyerId)) {
            if (buyerConfirmedAt != null) {
                throw new IllegalStateException("Buyer has already confirmed");
            }
            buyerConfirmedAt = confirmedAt;
        } else {
            if (sellerConfirmedAt != null) {
                throw new IllegalStateException("Seller has already confirmed");
            }
            sellerConfirmedAt = confirmedAt;
        }
        if (buyerConfirmedAt != null && sellerConfirmedAt != null) {
            status = TransactionStatus.COMPLETED;
        }
        lastEventAt = confirmedAt;
    }

    /** Cancels directly only before the first participant confirmation, recording who and when. */
    public void cancel(UUID actorId, Instant cancelledAt) {
        requireActive();
        requireParticipant(actorId);
        requireTime(cancelledAt);
        if (hasConfirmation()) {
            throw new IllegalStateException("Cancellation now requires the other participant's agreement");
        }
        markCancelled(actorId, cancelledAt);
    }

    /** Requests mutual cancellation after the first confirmation, preserving earlier request history. */
    public CancellationRequest requestCancellation(UUID actorId, Instant requestedAt) {
        requireActive();
        requireParticipant(actorId);
        requireTime(requestedAt);
        if (!hasConfirmation() || hasPendingCancellation()) {
            throw new IllegalStateException("A request requires a confirmation and no pending request");
        }
        CancellationRequest request = new CancellationRequest(id, actorId, requestedAt);
        cancellationRequests.add(request);
        lastEventAt = requestedAt;
        return request;
    }

    /** Accepts the other participant's current request and cancels this transaction. */
    public void acceptCancellation(UUID requestId, UUID actorId, Instant resolvedAt) {
        UUID requesterId = resolveCancellation(requestId, actorId, resolvedAt, CancellationStatus.ACCEPTED);
        markCancelled(requesterId, resolvedAt);
    }

    /** Rejects the other participant's request without clearing existing confirmations. */
    public void rejectCancellation(UUID requestId, UUID actorId, Instant resolvedAt) {
        resolveCancellation(requestId, actorId, resolvedAt, CancellationStatus.REJECTED);
    }

    /** Withdraws the actor's own request without clearing existing confirmations. */
    public void withdrawCancellation(UUID requestId, UUID actorId, Instant resolvedAt) {
        resolveCancellation(requestId, actorId, resolvedAt, CancellationStatus.WITHDRAWN);
    }

    /** Returns immutable snapshots; later resolutions do not mutate previously returned history. */
    public List<CancellationRequest> getCancellationRequests() {
        return List.copyOf(cancellationRequests);
    }

    public UUID getId() {
        return id;
    }

    public UUID getListingId() {
        return listingId;
    }

    public UUID getAcceptedOfferId() {
        return acceptedOfferId;
    }

    public UUID getBuyerId() {
        return buyerId;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public long getAgreedPriceCents() {
        return agreedPriceCents;
    }

    public String getListingTitle() {
        return listingTitle;
    }

    public String getListingDescription() {
        return listingDescription;
    }

    public Condition getListingCondition() {
        return listingCondition;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Optional<Instant> getBuyerConfirmedAt() {
        return Optional.ofNullable(buyerConfirmedAt);
    }

    public Optional<Instant> getSellerConfirmedAt() {
        return Optional.ofNullable(sellerConfirmedAt);
    }

    /** The latest recorded event; a new action's time must not precede it. */
    public Instant getLastEventAt() {
        return lastEventAt;
    }

    /** When the sale was cancelled, directly or by an accepted request; empty unless cancelled. */
    public Optional<Instant> getCancelledAt() {
        return Optional.ofNullable(cancelledAt);
    }

    /** Who cancelled directly, or whose cancellation request was accepted; empty unless cancelled. */
    public Optional<UUID> getCancelledBy() {
        return Optional.ofNullable(cancelledBy);
    }

    /** True once either participant has confirmed; direct cancellation is then no longer allowed. */
    public boolean hasConfirmation() {
        return buyerConfirmedAt != null || sellerConfirmedAt != null;
    }

    /** True when the given participant has confirmed completion. */
    public boolean hasConfirmed(UUID participantId) {
        requireParticipant(participantId);
        return participantId.equals(buyerId) ? buyerConfirmedAt != null : sellerConfirmedAt != null;
    }

    /** The request awaiting a response, if any; it is always the latest request. */
    public Optional<CancellationRequest> getPendingCancellation() {
        return hasPendingCancellation() ? Optional.of(cancellationRequests.getLast()) : Optional.empty();
    }

    private boolean hasPendingCancellation() {
        return !cancellationRequests.isEmpty()
                && cancellationRequests.getLast().getStatus() == CancellationStatus.PENDING;
    }

    private void markCancelled(UUID participantId, Instant time) {
        status = TransactionStatus.CANCELLED;
        cancelledBy = participantId;
        cancelledAt = time;
        lastEventAt = time;
    }

    /** Resolves the pending request and returns its requester. */
    private UUID resolveCancellation(UUID requestId, UUID actorId, Instant time, CancellationStatus outcome) {
        requireActive();
        requireParticipant(actorId);
        requireTime(time);
        Objects.requireNonNull(requestId, "Request ID");
        if (!hasPendingCancellation() || !cancellationRequests.getLast().getId().equals(requestId)) {
            throw new IllegalStateException("The specified request is not pending on this transaction");
        }
        CancellationRequest request = cancellationRequests.getLast();
        boolean isRequester = request.getRequesterId().equals(actorId);
        if ((outcome == CancellationStatus.WITHDRAWN) != isRequester) {
            throw new IllegalArgumentException("Only the requester may withdraw; only the other user may respond");
        }
        cancellationRequests.set(cancellationRequests.size() - 1, request.resolve(outcome, time));
        lastEventAt = time;
        return request.getRequesterId();
    }

    private void requireActive() {
        if (status != TransactionStatus.ACTIVE) {
            throw new IllegalStateException("Transaction is no longer active");
        }
    }

    private void requireParticipant(UUID actorId) {
        Objects.requireNonNull(actorId, "Actor ID");
        if (!actorId.equals(buyerId) && !actorId.equals(sellerId)) {
            throw new IllegalArgumentException("Only transaction participants may perform this action");
        }
    }

    private void requireTime(Instant time) {
        Objects.requireNonNull(time, "Time");
        if (time.isBefore(lastEventAt)) {
            throw new IllegalArgumentException("Time cannot precede the previous transaction event");
        }
    }
}
