package hotshop.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import hotshop.model.Conversation;
import hotshop.model.Listing;
import hotshop.model.Offer;
import hotshop.model.OfferStatus;

/**
 * One conversation as seen by one participant: the listing, the other participant's public
 * profile, the viewer's role, the buyer's latest offer on the listing, the active sale between
 * them (for its meetup), unread messages and offer events, a preview line, when anything last
 * happened, and whether new messages can be sent. The records are detached copies.
 */
public record ConversationSummary(Conversation conversation, Listing listing, PublicProfile otherParticipant,
        SaleRole role, Optional<Offer> latestOffer, Optional<UUID> activeSaleId, int unreadCount, String preview,
        Instant lastActivityAt, boolean canSend) {
    /** True while the buyer has a pending offer or the two have an active sale; such conversations are listed first. */
    public boolean isAboutOfferOrSale() {
        return activeSaleId.isPresent() || latestOffer.filter(offer -> offer.getStatus() == OfferStatus.PENDING)
                .isPresent();
    }
}
