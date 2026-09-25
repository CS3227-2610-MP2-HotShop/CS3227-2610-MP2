package hotshop.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A time and place the seller offers one sale's buyer for the handover. Immutable; withdrawn by deletion. */
public record MeetupSlot(UUID id, UUID transactionId, MeetupTime time, Instant createdAt) {
    public MeetupSlot {
        Objects.requireNonNull(id, "Slot ID");
        Objects.requireNonNull(transactionId, "Transaction ID");
        Objects.requireNonNull(time, "Meetup time");
        Objects.requireNonNull(createdAt, "Creation time");
    }

    /** Offers a new slot for the sale. */
    public static MeetupSlot offer(UUID transactionId, MeetupTime time, Instant createdAt) {
        return new MeetupSlot(UUID.randomUUID(), transactionId, time, createdAt);
    }
}
