package hotshop.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The messages between one buyer and the seller about one listing. It records how far each
 * participant has read; services enforce one conversation per buyer and listing, authorize
 * callers, and decide when sending is allowed.
 */
public final class Conversation {
    private final UUID id;
    private final UUID listingId;
    private final UUID buyerId;
    private final UUID sellerId;
    private final Instant createdAt;
    private long buyerReadSequence;
    private Instant buyerOpenedAt;
    private long sellerReadSequence;
    private Instant sellerOpenedAt;

    /** The saved state of a conversation, one component per stored column. */
    public record Snapshot(UUID id, UUID listingId, UUID buyerId, UUID sellerId, Instant createdAt,
            long buyerReadSequence, Instant buyerOpenedAt, long sellerReadSequence, Instant sellerOpenedAt) {
    }

    /**
     * Starts the buyer's conversation about an available or reserved listing. The buyer starts
     * it, so they have seen everything in it so far; the seller has not opened it yet.
     */
    public Conversation(Listing listing, UUID buyerId, Instant createdAt) {
        this(new Snapshot(UUID.randomUUID(), Objects.requireNonNull(listing, "Listing").getId(), buyerId,
                listing.getSellerId(), createdAt, 0, createdAt, 0, null));
        if (!isOpenFor(listing)) {
            throw new IllegalStateException("Conversations start only on available or reserved listings");
        }
    }

    private Conversation(Snapshot saved) {
        id = Objects.requireNonNull(saved.id(), "Conversation ID");
        listingId = Objects.requireNonNull(saved.listingId(), "Listing ID");
        buyerId = Objects.requireNonNull(saved.buyerId(), "Buyer ID");
        sellerId = Objects.requireNonNull(saved.sellerId(), "Seller ID");
        createdAt = Objects.requireNonNull(saved.createdAt(), "Creation time");
        if (buyerId.equals(sellerId)) {
            throw new IllegalArgumentException("A user cannot start a conversation about their own listing");
        }
        buyerReadSequence = validSequence(saved.buyerReadSequence());
        buyerOpenedAt = validOpenedAt(saved.buyerOpenedAt());
        sellerReadSequence = validSequence(saved.sellerReadSequence());
        sellerOpenedAt = validOpenedAt(saved.sellerOpenedAt());
    }

    /** Restores a persisted conversation while enforcing the same invariants as creation. */
    public static Conversation restore(Snapshot saved) {
        return new Conversation(Objects.requireNonNull(saved, "Conversation snapshot"));
    }

    /**
     * True while the listing is available or reserved: conversations about it can start and
     * receive messages. Sold and archived listings keep their conversations read-only.
     */
    public static boolean isOpenFor(Listing listing) {
        return listing.getStatus() == ListingStatus.AVAILABLE || listing.getStatus() == ListingStatus.RESERVED;
    }

    /**
     * Records that a participant has read up to a message and opened the conversation at a time.
     * Positions only move forward, so an older position or time leaves the later one in place.
     */
    public void markRead(UUID participant, long sequence, Instant time) {
        requireParticipant(participant);
        validSequence(sequence);
        Instant openedAt = validOpenedAt(Objects.requireNonNull(time, "Open time"));
        if (participant.equals(buyerId)) {
            buyerReadSequence = Math.max(buyerReadSequence, sequence);
            buyerOpenedAt = later(buyerOpenedAt, openedAt);
        } else {
            sellerReadSequence = Math.max(sellerReadSequence, sequence);
            sellerOpenedAt = later(sellerOpenedAt, openedAt);
        }
    }

    public boolean isParticipant(UUID userId) {
        return buyerId.equals(userId) || sellerId.equals(userId);
    }

    public UUID getOtherParticipant(UUID participant) {
        requireParticipant(participant);
        return participant.equals(buyerId) ? sellerId : buyerId;
    }

    /** The sequence number of the last message this participant has read; 0 before any. */
    public long getReadSequence(UUID participant) {
        requireParticipant(participant);
        return participant.equals(buyerId) ? buyerReadSequence : sellerReadSequence;
    }

    /** When this participant last opened the conversation; empty if they never have. */
    public Optional<Instant> getLastOpenedAt(UUID participant) {
        requireParticipant(participant);
        return Optional.ofNullable(participant.equals(buyerId) ? buyerOpenedAt : sellerOpenedAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getListingId() {
        return listingId;
    }

    public UUID getBuyerId() {
        return buyerId;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private void requireParticipant(UUID userId) {
        if (!isParticipant(Objects.requireNonNull(userId, "Participant"))) {
            throw new IllegalArgumentException("Only the buyer and seller take part in this conversation");
        }
    }

    private static long validSequence(long sequence) {
        if (sequence < 0) {
            throw new IllegalArgumentException("Read positions cannot be negative");
        }
        return sequence;
    }

    private Instant validOpenedAt(Instant openedAt) {
        if (openedAt != null && openedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("A conversation cannot be opened before it starts");
        }
        return openedAt;
    }

    private static Instant later(Instant current, Instant candidate) {
        return current == null || candidate.isAfter(current) ? candidate : current;
    }
}
