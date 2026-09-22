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
    private final UUID id = UUID.randomUUID();
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
    private TransactionStatus status = TransactionStatus.ACTIVE;
    private Instant buyerConfirmedAt;
    private Instant sellerConfirmedAt;
    private Instant lastEventAt;

    /** Captures an accepted offer and its reserved listing without retaining either mutable object. */
    public Transaction(Listing listing, Offer offer, Instant createdAt) {
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

    /** Cancels directly only before the first participant confirmation. */
    public void cancel(UUID actorId) {
        requireActive();
        requireParticipant(actorId);
        if (hasConfirmation()) {
            throw new IllegalStateException("Cancellation now requires the other participant's agreement");
        }
        status = TransactionStatus.CANCELLED;
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
        resolveCancellation(requestId, actorId, resolvedAt, CancellationStatus.ACCEPTED);
        status = TransactionStatus.CANCELLED;
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

    private boolean hasConfirmation() {
        return buyerConfirmedAt != null || sellerConfirmedAt != null;
    }

    private boolean hasPendingCancellation() {
        return !cancellationRequests.isEmpty()
                && cancellationRequests.getLast().getStatus() == CancellationStatus.PENDING;
    }

    private void resolveCancellation(UUID requestId, UUID actorId, Instant time, CancellationStatus outcome) {
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
