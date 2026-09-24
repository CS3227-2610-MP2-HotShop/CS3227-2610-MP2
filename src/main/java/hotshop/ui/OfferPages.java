package hotshop.ui;

import hotshop.model.Listing;
import hotshop.model.ListingDetails;
import hotshop.model.ListingStatus;
import hotshop.model.OfferStatus;
import hotshop.service.OfferWithBuyer;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

/** Buyer offer history, seller incoming offers, and the amount-entry dialog. */
final class OfferPages {
    private final MarketplaceUi app;

    OfferPages(MarketplaceUi app) {
        this.app = app;
    }

    void mine() {
        UiPage page = app.page("My Offers");
        page.load(app.runtime.getOffers()::getMyOffers, offers -> {
            if (offers.isEmpty()) {
                page.body.getChildren().addAll(UiControls.label("You have no offers yet.", "muted"),
                        UiControls.button("Search Listings", "empty-search", () -> app.navigate(app::search)));
            }
            for (var value : offers) {
                var offer = value.offer();
                VBox row = new VBox(10);
                row.getStyleClass().add("card");
                row.getChildren().addAll(UiControls.button(value.listing().listing().getDetails().title(),
                        "offer-listing", () -> app.navigate(() -> app.listings.details(offer.getListingId()))),
                        app.profileLink(value.listing().seller(), "offer-seller"),
                        UiControls.label("Your offer: " + UiControls.money(offer.getAmountCents()), "price"),
                        UiControls.label(UiControls.title(offer.getStatus()) + value.saleStatus()
                                .map(status -> " · Sale " + UiControls.title(status)).orElse(""), "badge"),
                        UiControls.label("Listing: " + UiControls.title(value.listing().listing().getStatus())
                                + " · Offered " + UiControls.time(offer.getCreatedAt()), "muted"));
                if (offer.getStatus() == OfferStatus.PENDING) {
                    row.getChildren().add(UiControls.button("Withdraw Offer", "withdraw-offer", () ->
                            page.perform(() -> app.runtime.getOffers().withdrawOffer(offer.getId()),
                                    ignored -> mine())));
                } else if (offer.getStatus() == OfferStatus.ACCEPTED) {
                    row.getChildren().add(UiControls.button("View Purchase", "offer-purchase", () ->
                            app.navigate(() -> app.sales.forOffer(offer.getId(), false))));
                }
                page.body.getChildren().add(row);
            }
        });
    }

    void buyerActions(UiPage page, Listing listing) {
        page.load(app.runtime.getOffers()::getMyOffers, offers -> {
            var pending = offers.stream().map(value -> value.offer())
                    .filter(offer -> offer.getListingId().equals(listing.getId())
                            && offer.getStatus() == OfferStatus.PENDING).findFirst();
            if (pending.isPresent()) {
                var offer = pending.orElseThrow();
                var amount = UiControls.label("Your pending offer: "
                        + UiControls.money(offer.getAmountCents()), "price");
                amount.setId("pending-offer");
                page.body.getChildren().addAll(amount,
                        UiControls.button("Withdraw Offer", "withdraw-offer", () ->
                                page.perform(() -> app.runtime.getOffers().withdrawOffer(offer.getId()),
                                        ignored -> app.listings.details(listing.getId()))));
            } else {
                Button makeOffer = UiControls.primary("Make Offer", "make-offer", () -> makeOffer(page, listing));
                makeOffer.setDisable(listing.getStatus() != ListingStatus.AVAILABLE);
                page.body.getChildren().add(makeOffer);
                if (makeOffer.isDisabled()) {
                    page.body.getChildren().add(UiControls.label(
                            "Only available listings can receive offers.", "hint"));
                }
            }
        });
    }

    private void makeOffer(UiPage page, Listing listing) {
        UiForm form = new UiForm();
        form.getChildren().addAll(UiControls.label(listing.getDetails().title(), "section-title"),
                UiControls.label("Asking price: " + UiControls.money(listing.getDetails().priceCents()), "price"));
        form.text("offer-amount", "Your offer (SGD)", "");
        long[] amount = new long[1];
        UiDialogs.form(app, page, "Make Offer", "Submit Offer", form, () -> {
            form.clearErrors();
            Long cents = form.cents("offer-amount", false);
            if (cents != null && (cents < 1 || cents > ListingDetails.MAX_PRICE_CENTS)) {
                form.reject("offer-amount", "Enter S$0.01 to S$1,000,000.00.");
            }
            if (form.isValid()) {
                amount[0] = cents;
            }
            return form.isValid();
        }, () -> app.runtime.getOffers().submitOffer(listing.getId(), amount[0]),
                ignored -> app.listings.details(listing.getId()));
    }

    void incoming(UiPage page, Listing listing, Button delete) {
        page.load(() -> app.runtime.getOffers().getOffersForListing(listing.getId()), offers -> {
            delete.setDisable(!listing.isDeletable() || !offers.isEmpty());
            page.body.getChildren().add(UiControls.label("Incoming Offers", "section-title"));
            if (offers.isEmpty()) {
                page.body.getChildren().add(UiControls.label("No offers have been received yet.", "muted"));
            }
            for (OfferWithBuyer value : offers) {
                var offer = value.offer();
                VBox row = new VBox(10, app.profileLink(value.buyer(), "offer-buyer"),
                        UiControls.label(UiControls.money(offer.getAmountCents()), "price"),
                        UiControls.label(UiControls.title(offer.getStatus()) + value.saleStatus()
                                .map(status -> " · Sale " + UiControls.title(status)).orElse(""), "badge"),
                        UiControls.label(UiControls.time(offer.getCreatedAt()), "muted"));
                row.getStyleClass().add("card");
                if (offer.getStatus() == OfferStatus.PENDING) {
                    Button accept = UiControls.primary("Accept Offer", "accept-offer", () -> {
                        if (app.confirm("Accept Offer", "Accept " + UiControls.money(offer.getAmountCents())
                                + "? This reserves the listing, creates a sale "
                                + "and rejects all other pending offers.")) {
                            page.perform(() -> app.runtime.getOffers().acceptOffer(offer.getId()), accepted ->
                                    app.replace(() -> app.sales.details(accepted.transactionId(), true)));
                        }
                    });
                    accept.setDisable(listing.getStatus() != ListingStatus.AVAILABLE);
                    row.getChildren().add(UiControls.actions(accept,
                            UiControls.button("Reject Offer", "reject-offer", () ->
                                    page.perform(() -> app.runtime.getOffers().rejectOffer(offer.getId()),
                                            ignored -> app.listings.details(listing.getId())))));
                } else if (offer.getStatus() == OfferStatus.ACCEPTED) {
                    row.getChildren().add(UiControls.button("View Sale", "offer-sale", () ->
                            app.navigate(() -> app.sales.forOffer(offer.getId(), true))));
                }
                page.body.getChildren().add(row);
            }
        });
    }
}
