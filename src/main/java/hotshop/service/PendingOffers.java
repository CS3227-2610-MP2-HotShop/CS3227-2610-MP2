package hotshop.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import hotshop.model.Offer;
import hotshop.repository.OfferRepository;

/** Closes the pending offers of a listing whose sale terms or availability changed. */
final class PendingOffers {
    private PendingOffers() {
    }

    /** Rejects every pending offer on the listing inside the caller's transaction, after an edit or archive. */
    static void rejectAll(Connection connection, OfferRepository offers, UUID listingId, Instant time)
            throws SQLException {
        rejectAllExcept(connection, offers, listingId, null, time);
    }

    /** Rejects every pending offer on the listing except the one being accepted, inside the caller's transaction. */
    static void rejectAllExcept(Connection connection, OfferRepository offers, UUID listingId, UUID acceptedOfferId,
            Instant time) throws SQLException {
        for (Offer offer : offers.findPendingByListing(connection, listingId)) {
            if (!offer.getId().equals(acceptedOfferId)) {
                offer.reject(ServiceSupport.latest(time, offer.getCreatedAt()));
                offers.update(connection, offer);
            }
        }
    }
}
