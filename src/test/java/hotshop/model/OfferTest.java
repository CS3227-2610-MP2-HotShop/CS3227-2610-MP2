package hotshop.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class OfferTest {
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private Listing listing() {
        return new Listing(SELLER, ListingTest.details("Chair", 5000), List.of(), ListingTest.CREATED);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, Long.MAX_VALUE})
    void constructor_boundaryAmount_acceptsValue(long amount) {
        Offer offer = new Offer(listing(), BUYER, amount);
        assertEquals(amount, offer.getAmountCents());
    }

    @Test
    void constructor_soldListing_throwsException() {
        Listing listing = listing();
        listing.reserve();
        listing.markSold();
        assertThrows(IllegalStateException.class, () -> new Offer(listing, BUYER, 5000));
    }

    @Test
    void constructor_archivedListing_throwsException() {
        Listing listing = listing();
        listing.archive();
        assertThrows(IllegalStateException.class, () -> new Offer(listing, BUYER, 5000));
    }

    @Test
    void constructor_nullBuyer_throwsException() {
        assertThrows(NullPointerException.class, () -> new Offer(listing(), null, 5000));
    }

    private Offer closedOffer(OfferStatus status) {
        Offer offer = new Offer(listing(), BUYER, 5000);
        switch (status) {
            case ACCEPTED -> offer.accept();
            case REJECTED -> offer.reject();
            case WITHDRAWN -> offer.withdraw();
            default -> throw new IllegalArgumentException("Expected a terminal status");
        }
        return offer;
    }

    @ParameterizedTest
    @EnumSource(value = OfferStatus.class, names = {"ACCEPTED", "REJECTED", "WITHDRAWN"})
    void accept_closedOffer_throwsException(OfferStatus status) {
        Offer offer = closedOffer(status);
        assertThrows(IllegalStateException.class, offer::accept);
        assertEquals(status, offer.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = OfferStatus.class, names = {"ACCEPTED", "REJECTED", "WITHDRAWN"})
    void reject_closedOffer_throwsException(OfferStatus status) {
        Offer offer = closedOffer(status);
        assertThrows(IllegalStateException.class, offer::reject);
        assertEquals(status, offer.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = OfferStatus.class, names = {"ACCEPTED", "REJECTED", "WITHDRAWN"})
    void withdraw_closedOffer_throwsException(OfferStatus status) {
        Offer offer = closedOffer(status);
        assertThrows(IllegalStateException.class, offer::withdraw);
        assertEquals(status, offer.getStatus());
    }

    @Test
    void constructor_aboveAskingPrice_createsPendingOffer() {
        Listing listing = listing();
        Offer offer = new Offer(listing, BUYER, 6000);
        assertEquals(6000, offer.getAmountCents());
        assertEquals(listing.getId(), offer.getListingId());
        assertEquals(BUYER, offer.getBuyerId());
        assertEquals(OfferStatus.PENDING, offer.getStatus());
    }

    @Test
    void constructor_ownListing_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new Offer(listing(), SELLER, 5000));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void constructor_nonpositiveAmount_throwsException(long amount) {
        assertThrows(IllegalArgumentException.class, () -> new Offer(listing(), BUYER, amount));
    }

    @Test
    void constructor_reservedListing_throwsException() {
        Listing listing = listing();
        listing.reserve();
        assertThrows(IllegalStateException.class, () -> new Offer(listing, BUYER, 5000));
    }

    @Test
    void withdraw_pendingOffer_preservesAmountAndClosesOffer() {
        Offer offer = new Offer(listing(), BUYER, 5000);
        offer.withdraw();
        assertEquals(OfferStatus.WITHDRAWN, offer.getStatus());
        assertEquals(5000, offer.getAmountCents());
        assertThrows(IllegalStateException.class, offer::accept);
    }

    @Test
    void accept_pendingOffer_closesOffer() {
        Offer offer = new Offer(listing(), BUYER, 5000);
        offer.accept();
        assertEquals(OfferStatus.ACCEPTED, offer.getStatus());
        assertThrows(IllegalStateException.class, offer::withdraw);
    }

    @Test
    void reject_pendingOffer_closesOffer() {
        Offer offer = new Offer(listing(), BUYER, 5000);
        offer.reject();
        assertEquals(OfferStatus.REJECTED, offer.getStatus());
        assertThrows(IllegalStateException.class, offer::accept);
    }
}
