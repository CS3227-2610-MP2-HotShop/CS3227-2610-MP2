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
     *
     * @param listing the listing whose state is used
     * @param buyerId the buyer's user ID
     * @param createdAt the creation time
     * @throws IllegalArgumentException if the supplied values violate the model invariants
     * @throws NullPointerException if a required value is null
     * @throws IllegalStateException if the supplied listing or offer is not in the required state
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

    /**
     * Restores a persisted conversation while enforcing the same invariants as creation.
     *
     * @param saved the persisted snapshot to validate and restore
     * @return the restored conversation with its persisted identity
     * @throws IllegalArgumentException if the supplied values violate the model invariants
     * @throws NullPointerException if a required value is null
     */
    public static Conversation restore(Snapshot saved) {
        return new Conversation(Objects.requireNonNull(saved, "Conversation snapshot"));
    }

    /**
     * True while the listing is available or reserved: conversations about it can start and
     * receive messages. Sold and archived listings keep their conversations read-only.
     *
     * @param listing the listing whose state is used
     * @return true if the listing is available or reserved
     */
    public static boolean isOpenFor(Listing listing) {
        return listing.getStatus() == ListingStatus.AVAILABLE || listing.getStatus() == ListingStatus.RESERVED;
    }

    /**
     * Records that a participant has read up to a message and opened the conversation at a time.
     * Positions only move forward, so an older position or time leaves the later one in place.
     *
     * @param participant the ID of the buyer or seller in this conversation
     * @param sequence the message sequence number
     * @param time the event time, not before the preceding event or creation
     * @throws IllegalArgumentException if the user is not a participant, the sequence is negative, or the time
     *     precedes creation
     * @throws NullPointerException if a required participant or time is null
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

    /**
     * Returns true if the user is this conversation's buyer or seller.
     *
     * @param userId the user ID to look up
     * @return true if the user is this conversation's buyer or seller
     */
    public boolean isParticipant(UUID userId) {
        return buyerId.equals(userId) || sellerId.equals(userId);
    }

    /**
     * Returns the other participant's user ID.
     *
     * @param participant the ID of the buyer or seller in this conversation
     * @return the other participant's user ID
     * @throws IllegalArgumentException if the user is not a participant
     * @throws NullPointerException if a required participant or time is null
     */
    public UUID getOtherParticipant(UUID participant) {
        requireParticipant(participant);
        return participant.equals(buyerId) ? sellerId : buyerId;
    }

    /**
     * The sequence number of the last message this participant has read; 0 before any.
     *
     * @param participant the ID of the buyer or seller in this conversation
     * @return the last read message sequence, or zero before any messages are read
     * @throws IllegalArgumentException if the user is not a participant
     * @throws NullPointerException if a required participant or time is null
     */
    public long getReadSequence(UUID participant) {
        requireParticipant(participant);
        return participant.equals(buyerId) ? buyerReadSequence : sellerReadSequence;
    }

    /**
     * When this participant last opened the conversation; empty if they never have.
     *
     * @param participant the ID of the buyer or seller in this conversation
     * @return the last open time, or empty if the participant has never opened the conversation
     * @throws IllegalArgumentException if the user is not a participant
     * @throws NullPointerException if a required participant or time is null
     */
    public Optional<Instant> getLastOpenedAt(UUID participant) {
        requireParticipant(participant);
        return Optional.ofNullable(participant.equals(buyerId) ? buyerOpenedAt : sellerOpenedAt);
    }

    /**
     * Returns the stable identity of this record.
     *
     * @return the stable identity of this record
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns the ID of the associated listing.
     *
     * @return the ID of the associated listing
     */
    public UUID getListingId() {
        return listingId;
    }

    /**
     * Returns the buyer's user ID.
     *
     * @return the buyer's user ID
     */
    public UUID getBuyerId() {
        return buyerId;
    }

    /**
     * Returns the seller's user ID.
     *
     * @return the seller's user ID
     */
    public UUID getSellerId() {
        return sellerId;
    }

    /**
     * Returns the creation time.
     *
     * @return the creation time
     */
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
