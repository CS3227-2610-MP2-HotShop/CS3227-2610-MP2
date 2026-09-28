package hotshop.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import hotshop.model.Meetup;
import hotshop.model.MeetupSlot;

/**
 * A sale's meetup state for either participant: the future slots the seller has offered, soonest
 * first, and the sale's meetup (scheduled, or completed for history). Models are detached copies.
 */
public record MeetupSummary(UUID saleId, List<MeetupSlot> offeredSlots, Optional<Meetup> meetup) {
    /**
     * Creates a meetup summary with an unmodifiable copy of its offered slots.
     *
     * @param saleId the ID of the agreed sale
     * @param offeredSlots the offered slots, copied into an unmodifiable list
     * @param meetup the scheduled or completed meetup, or empty if none exists
     */
    public MeetupSummary {
        offeredSlots = List.copyOf(offeredSlots);
    }
}
