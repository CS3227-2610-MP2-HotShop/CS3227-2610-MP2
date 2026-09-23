package hotshop.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class OfferTest {
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OFFER = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final UUID LISTING = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final Instant LATER = ListingTest.CREATED.plusSeconds(60);

    @ParameterizedTest
    @ValueSource(longs = {ListingDetails.MAX_PRICE_CENTS + 1, Long.MAX_VALUE})
    void constructor_amountAboveMaximum_throwsException(long amount) {
        assertThrows(IllegalArgumentException.class,
                () -> new Offer(listing(), BUYER, amount, ListingTest.CREATED));
    }

    @Test
    void constructor_creationTime_startsPendingWithoutCloseTime() {
        Offer offer = new Offer(listing(), BUYER, 5000, ListingTest.CREATED);
        assertEquals(ListingTest.CREATED, offer.getCreatedAt());
        assertTrue(offer.getClosedAt().isEmpty());
    }

    @Test
    void constructor_nullCreationTime_throwsException() {
        assertThrows(NullPointerException.class, () -> new Offer(listing(), BUYER, 5000, null));
    }

    @ParameterizedTest
    @EnumSource(value = OfferStatus.class, names = {"ACCEPTED", "REJECTED", "WITHDRAWN"})
    void close_pendingOffer_recordsCloseTime(OfferStatus status) {
        Offer offer = new Offer(listing(), BUYER, 5000, ListingTest.CREATED);
        switch (status) {
            case ACCEPTED -> offer.accept(LATER);
            case REJECTED -> offer.reject(LATER);
            default -> offer.withdraw(LATER);
        }
        assertEquals(LATER, offer.getClosedAt().orElseThrow());
    }

    @Test
    void accept_timeBeforeCreation_throwsWithoutClosing() {
        Offer offer = new Offer(listing(), BUYER, 5000, LATER);
        assertThrows(IllegalArgumentException.class, () -> offer.accept(ListingTest.CREATED));
        assertEquals(OfferStatus.PENDING, offer.getStatus());
        assertTrue(offer.getClosedAt().isEmpty());
    }

    @Test
    void restore_persistedValues_preservesIdentityStatusAndTimes() {
        Offer offer = Offer.restore(OFFER, LISTING, BUYER, 4500, OfferStatus.REJECTED, ListingTest.CREATED, LATER);
        assertEquals(OFFER, offer.getId());
        assertEquals(LISTING, offer.getListingId());
        assertEquals(BUYER, offer.getBuyerId());
        assertEquals(4500, offer.getAmountCents());
        assertEquals(OfferStatus.REJECTED, offer.getStatus());
        assertEquals(ListingTest.CREATED, offer.getCreatedAt());
        assertEquals(LATER, offer.getClosedAt().orElseThrow());
    }

    @Test
    void restore_pendingWithCloseTime_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> Offer.restore(OFFER, LISTING, BUYER, 4500,
                OfferStatus.PENDING, ListingTest.CREATED, LATER));
    }

    @Test
    void restore_closedWithoutCloseTime_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> Offer.restore(OFFER, LISTING, BUYER, 4500,
                OfferStatus.ACCEPTED, ListingTest.CREATED, null));
    }

    @Test
    void restore_invalidAmount_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> Offer.restore(OFFER, LISTING, BUYER, 0,
                OfferStatus.PENDING, ListingTest.CREATED, null));
    }

    private Listing listing() {
        return new Listing(SELLER, ListingTest.details("Chair", 5000), List.of(), ListingTest.CREATED);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, ListingDetails.MAX_PRICE_CENTS})
    void constructor_boundaryAmount_acceptsValue(long amount) {
        Offer offer = new Offer(listing(), BUYER, amount, ListingTest.CREATED);
        assertEquals(amount, offer.getAmountCents());
    }

    @Test
    void constructor_soldListing_throwsException() {
        Listing listing = listing();
        listing.reserve();
        listing.markSold();
        assertThrows(IllegalStateException.class, () -> new Offer(listing, BUYER, 5000, ListingTest.CREATED));
    }

    @Test
    void constructor_archivedListing_throwsException() {
        Listing listing = listing();
        listing.archive();
        assertThrows(IllegalStateException.class, () -> new Offer(listing, BUYER, 5000, ListingTest.CREATED));
    }

    @Test
    void constructor_nullBuyer_throwsException() {
        assertThrows(NullPointerException.class, () -> new Offer(listing(), null, 5000, ListingTest.CREATED));
    }

    private Offer closedOffer(OfferStatus status) {
        Offer offer = new Offer(listing(), BUYER, 5000, ListingTest.CREATED);
        switch (status) {
            case ACCEPTED -> offer.accept(ListingTest.CREATED);
            case REJECTED -> offer.reject(ListingTest.CREATED);
            case WITHDRAWN -> offer.withdraw(ListingTest.CREATED);
            default -> throw new IllegalArgumentException("Expected a terminal status");
        }
        return offer;
    }

    @ParameterizedTest
    @EnumSource(value = OfferStatus.class, names = {"ACCEPTED", "REJECTED", "WITHDRAWN"})
    void accept_closedOffer_throwsException(OfferStatus status) {
        Offer offer = closedOffer(status);
        assertThrows(IllegalStateException.class, () -> offer.accept(ListingTest.CREATED));
        assertEquals(status, offer.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = OfferStatus.class, names = {"ACCEPTED", "REJECTED", "WITHDRAWN"})
    void reject_closedOffer_throwsException(OfferStatus status) {
        Offer offer = closedOffer(status);
        assertThrows(IllegalStateException.class, () -> offer.reject(ListingTest.CREATED));
        assertEquals(status, offer.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = OfferStatus.class, names = {"ACCEPTED", "REJECTED", "WITHDRAWN"})
    void withdraw_closedOffer_throwsException(OfferStatus status) {
        Offer offer = closedOffer(status);
        assertThrows(IllegalStateException.class, () -> offer.withdraw(ListingTest.CREATED));
        assertEquals(status, offer.getStatus());
    }

    @Test
    void constructor_aboveAskingPrice_createsPendingOffer() {
        Listing listing = listing();
        Offer offer = new Offer(listing, BUYER, 6000, ListingTest.CREATED);
        assertEquals(6000, offer.getAmountCents());
        assertEquals(listing.getId(), offer.getListingId());
        assertEquals(BUYER, offer.getBuyerId());
        assertEquals(OfferStatus.PENDING, offer.getStatus());
    }

    @Test
    void constructor_ownListing_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new Offer(listing(), SELLER, 5000, ListingTest.CREATED));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void constructor_nonpositiveAmount_throwsException(long amount) {
        assertThrows(IllegalArgumentException.class, () -> new Offer(listing(), BUYER, amount, ListingTest.CREATED));
    }

    @Test
    void constructor_reservedListing_throwsException() {
        Listing listing = listing();
        listing.reserve();
        assertThrows(IllegalStateException.class, () -> new Offer(listing, BUYER, 5000, ListingTest.CREATED));
    }

    @Test
    void withdraw_pendingOffer_preservesAmountAndClosesOffer() {
        Offer offer = new Offer(listing(), BUYER, 5000, ListingTest.CREATED);
        offer.withdraw(ListingTest.CREATED);
        assertEquals(OfferStatus.WITHDRAWN, offer.getStatus());
        assertEquals(5000, offer.getAmountCents());
        assertThrows(IllegalStateException.class, () -> offer.accept(ListingTest.CREATED));
    }

    @Test
    void accept_pendingOffer_closesOffer() {
        Offer offer = new Offer(listing(), BUYER, 5000, ListingTest.CREATED);
        offer.accept(ListingTest.CREATED);
        assertEquals(OfferStatus.ACCEPTED, offer.getStatus());
        assertThrows(IllegalStateException.class, () -> offer.withdraw(ListingTest.CREATED));
    }

    @Test
    void reject_pendingOffer_closesOffer() {
        Offer offer = new Offer(listing(), BUYER, 5000, ListingTest.CREATED);
        offer.reject(ListingTest.CREATED);
        assertEquals(OfferStatus.REJECTED, offer.getStatus());
        assertThrows(IllegalStateException.class, () -> offer.accept(ListingTest.CREATED));
    }
}
