package hotshop.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import hotshop.model.ListingStatus;
import hotshop.model.Offer;
import hotshop.model.OfferStatus;
import hotshop.model.TransactionStatus;
import hotshop.service.SaleRole;

class OfferBarTest {
    private static final Instant MADE = Instant.parse("2026-09-26T02:00:00Z");
    private static final Instant CLOSED = Instant.parse("2026-09-26T03:00:00Z");

    @Test
    void of_sellerPendingOfferOnAvailableListing_offersAcceptAndReject() {
        OfferBar bar = OfferBar.of(SaleRole.SELLER, Optional.of(offer(OfferStatus.PENDING)),
                ListingStatus.AVAILABLE, Optional.empty());
        assertEquals("Offer of S$40.00 · Pending", bar.text());
        assertEquals(List.of(OfferBar.Action.ACCEPT, OfferBar.Action.REJECT), bar.actions());
    }

    @Test
    void of_buyerPendingOffer_offersWithdrawOnly() {
        OfferBar bar = OfferBar.of(SaleRole.BUYER, Optional.of(offer(OfferStatus.PENDING)),
                ListingStatus.AVAILABLE, Optional.empty());
        assertEquals("Offer of S$40.00 · Pending", bar.text());
        assertEquals(List.of(OfferBar.Action.WITHDRAW), bar.actions());
    }

    @Test
    void of_acceptedOfferWithCompletedSale_showsSaleStatusAndViewSale() {
        OfferBar bar = OfferBar.of(SaleRole.BUYER, Optional.of(offer(OfferStatus.ACCEPTED)),
                ListingStatus.SOLD, Optional.of(TransactionStatus.COMPLETED));
        assertEquals("Offer of S$40.00 · Accepted · Sale Completed", bar.text());
        assertEquals(List.of(OfferBar.Action.VIEW_SALE), bar.actions());
    }

    @Test
    void of_buyerRejectedOfferOnAvailableListing_offersMakeOffer() {
        OfferBar bar = OfferBar.of(SaleRole.BUYER, Optional.of(offer(OfferStatus.REJECTED)),
                ListingStatus.AVAILABLE, Optional.empty());
        assertEquals("Offer of S$40.00 · Rejected", bar.text());
        assertEquals(List.of(OfferBar.Action.MAKE_OFFER), bar.actions());
    }

    @Test
    void of_sellerWithoutOffer_showsNoOfferYet() {
        OfferBar bar = OfferBar.of(SaleRole.SELLER, Optional.empty(), ListingStatus.AVAILABLE, Optional.empty());
        assertEquals("No offer yet", bar.text());
        assertEquals(List.of(), bar.actions());
    }

    @Test
    void of_buyerWithoutOfferOnAvailableListing_offersMakeOffer() {
        OfferBar bar = OfferBar.of(SaleRole.BUYER, Optional.empty(), ListingStatus.AVAILABLE, Optional.empty());
        assertEquals("No offer yet", bar.text());
        assertEquals(List.of(OfferBar.Action.MAKE_OFFER), bar.actions());
    }

    @Test
    void of_buyerWithdrawnOfferOnReservedListing_offersNothing() {
        OfferBar bar = OfferBar.of(SaleRole.BUYER, Optional.of(offer(OfferStatus.WITHDRAWN)),
                ListingStatus.RESERVED, Optional.empty());
        assertEquals("Offer of S$40.00 · Withdrawn", bar.text());
        assertEquals(List.of(), bar.actions());
    }

    @Test
    void of_buyerWithoutOfferOnSoldListing_offersNothing() {
        OfferBar bar = OfferBar.of(SaleRole.BUYER, Optional.empty(), ListingStatus.SOLD, Optional.empty());
        assertEquals(List.of(), bar.actions());
    }

    @Test
    void of_sellerRejectedOfferOnAvailableListing_showsStatusOnly() {
        OfferBar bar = OfferBar.of(SaleRole.SELLER, Optional.of(offer(OfferStatus.REJECTED)),
                ListingStatus.AVAILABLE, Optional.empty());
        assertEquals("Offer of S$40.00 · Rejected", bar.text());
        assertEquals(List.of(), bar.actions());
    }

    @Test
    void of_acceptedOfferWithActiveSale_offersViewSaleToSeller() {
        OfferBar bar = OfferBar.of(SaleRole.SELLER, Optional.of(offer(OfferStatus.ACCEPTED)),
                ListingStatus.RESERVED, Optional.of(TransactionStatus.ACTIVE));
        assertEquals("Offer of S$40.00 · Accepted · Sale Active", bar.text());
        assertEquals(List.of(OfferBar.Action.VIEW_SALE), bar.actions());
    }

    @Test
    void of_buyerWithdrawnOfferOnAvailableListing_offersMakeOffer() {
        OfferBar bar = OfferBar.of(SaleRole.BUYER, Optional.of(offer(OfferStatus.WITHDRAWN)),
                ListingStatus.AVAILABLE, Optional.empty());
        assertEquals("Offer of S$40.00 · Withdrawn", bar.text());
        assertEquals(List.of(OfferBar.Action.MAKE_OFFER), bar.actions());
    }

    @Test
    void of_sellerAcceptedOfferWithCancelledSale_showsCancelledSaleAndViewSale() {
        OfferBar bar = OfferBar.of(SaleRole.SELLER, Optional.of(offer(OfferStatus.ACCEPTED)),
                ListingStatus.AVAILABLE, Optional.of(TransactionStatus.CANCELLED));
        assertEquals("Offer of S$40.00 · Accepted · Sale Cancelled", bar.text());
        assertEquals(List.of(OfferBar.Action.VIEW_SALE), bar.actions());
    }

    private static Offer offer(OfferStatus status) {
        return Offer.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 4000, status, MADE,
                status == OfferStatus.PENDING ? null : CLOSED);
    }
}
