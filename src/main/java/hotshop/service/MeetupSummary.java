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
    public MeetupSummary {
        offeredSlots = List.copyOf(offeredSlots);
    }
}
