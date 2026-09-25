package hotshop.ui;

import java.util.List;
import java.util.Optional;

import hotshop.model.ListingStatus;
import hotshop.model.Offer;
import hotshop.model.TransactionStatus;
import hotshop.service.SaleRole;

/** What a conversation's offer bar shows one participant, and which offer actions it offers. */
record OfferBar(String text, List<OfferBar.Action> actions) {
    /** Offer actions the bar can show; OfferService still decides whether each one is allowed. */
    enum Action {
        ACCEPT, REJECT, WITHDRAW, VIEW_SALE, MAKE_OFFER
    }

    static OfferBar of(SaleRole viewer, Optional<Offer> latestOffer, ListingStatus listingStatus,
            Optional<TransactionStatus> saleStatus) {
        if (latestOffer.isEmpty()) {
            return new OfferBar("No offer yet", canMakeOffer(viewer, listingStatus)
                    ? List.of(Action.MAKE_OFFER) : List.of());
        }
        Offer offer = latestOffer.orElseThrow();
        String text = "Offer of " + UiControls.money(offer.getAmountCents()) + " · "
                + UiControls.title(offer.getStatus());
        return switch (offer.getStatus()) {
            case PENDING -> new OfferBar(text, viewer == SaleRole.SELLER
                    ? List.of(Action.ACCEPT, Action.REJECT) : List.of(Action.WITHDRAW));
            case ACCEPTED -> new OfferBar(text + saleStatus.map(status -> " · Sale " + UiControls.title(status))
                    .orElse(""), List.of(Action.VIEW_SALE));
            default -> new OfferBar(text, canMakeOffer(viewer, listingStatus)
                    ? List.of(Action.MAKE_OFFER) : List.of());
        };
    }

    private static boolean canMakeOffer(SaleRole viewer, ListingStatus listingStatus) {
        return viewer == SaleRole.BUYER && listingStatus == ListingStatus.AVAILABLE;
    }
}
