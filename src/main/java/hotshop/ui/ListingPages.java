package hotshop.ui;

import java.util.UUID;

import hotshop.model.Conversation;
import hotshop.model.Listing;
import hotshop.model.ListingStatus;
import hotshop.service.ListingWithSeller;
import hotshop.service.SaleRole;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;

/** Listing management and the shared owner/buyer detail page. */
final class ListingPages {
    private final MarketplaceUi app;

    ListingPages(MarketplaceUi app) {
        this.app = app;
    }

    void mine() {
        UiPage page = app.page("My Listings");
        page.body.getChildren().add(UiControls.primary("Create Listing", "create-listing",
                () -> app.navigate(() -> new ListingEditor(app, null))));
        page.load(app.runtime.getListings()::getMyListings, values -> {
            if (values.isEmpty()) {
                page.body.getChildren().add(UiControls.label("You have no listings yet. Create your first listing.",
                        "muted"));
            } else {
                var grid = ListingCards.grid();
                values.forEach(value -> grid.getChildren().add(
                        ListingCards.card(app, value.listing(), value.pendingOffers())));
                page.body.getChildren().add(grid);
            }
        });
    }

    void details(UUID id) {
        UiPage page = app.page("Listing Details");
        page.load(() -> app.runtime.getListings().getListing(id), value -> render(page, value));
    }

    private void render(UiPage page, ListingWithSeller value) {
        Listing listing = value.listing();
        var details = listing.getDetails();
        Label title = UiControls.label(details.title(), "page-title");
        title.setId("listing-title");
        page.body.getChildren().addAll(title,
                UiControls.label(UiControls.money(details.priceCents()), "price"),
                UiControls.label(UiControls.title(listing.getStatus()), "badge"));
        FlowPane gallery = new FlowPane(12, 12);
        if (listing.getImages().isEmpty()) {
            gallery.getChildren().add(UiImages.display(null, 280, 200));
        } else {
            listing.getImages().forEach(image -> gallery.getChildren().add(UiImages.display(
                    () -> app.runtime.getListingImagePath(image.filename()), 280, 200)));
        }
        page.body.getChildren().addAll(gallery, UiControls.label(details.description(), "description"),
                UiControls.label("Category: " + UiControls.title(details.category()), "muted"),
                UiControls.label("Condition: " + UiControls.title(details.condition()), "muted"),
                UiControls.label("Pickup: " + details.pickupLocation(), "muted"),
                app.profileLink(value.seller(), "seller-profile"));
        if (listing.getSellerId().equals(app.userId())) {
            ownerActions(page, listing);
        } else {
            buyerChat(page, listing);
        }
    }

    /** Buyers can start chats only about open listings, but can always reopen one they started. */
    private void buyerChat(UiPage page, Listing listing) {
        Button chat = UiControls.button("Chat with seller", "chat-seller",
                () -> app.navigate(() -> app.chats.withSeller(listing.getId())));
        page.body.getChildren().add(UiControls.actions(chat, UiControls.future("Save to wishlist", "save-wishlist")));
        if (Conversation.isOpenFor(listing)) {
            app.offers.buyerActions(page, listing);
            return;
        }
        chat.setDisable(true);
        Label closed = UiControls.label("Conversations can only be started about available or reserved listings.",
                "hint");
        closed.setId("chat-seller-hint");
        page.body.getChildren().add(closed);
        page.load(app.runtime.getChats()::getConversations, conversations -> {
            if (conversations.stream().anyMatch(summary -> summary.role() == SaleRole.BUYER
                    && summary.listing().getId().equals(listing.getId()))) {
                chat.setDisable(false);
                page.body.getChildren().remove(closed);
            }
            app.offers.buyerActions(page, listing);
        });
    }

    private void ownerActions(UiPage page, Listing listing) {
        var edit = UiControls.button("Edit Listing", "edit-listing",
                () -> app.navigate(() -> new ListingEditor(app, listing)));
        edit.setDisable(listing.getStatus() != ListingStatus.AVAILABLE);
        var archive = UiControls.button("Archive Listing", "archive-listing", () -> {
            if (app.confirm("Archive Listing", "This hides the listing from search and rejects pending offers. "
                    + "Archiving cannot be undone.")) {
                page.perform(() -> app.runtime.getListings().archiveListing(listing.getId()),
                        ignored -> details(listing.getId()));
            }
        });
        archive.setDisable(listing.getStatus() != ListingStatus.AVAILABLE
                && listing.getStatus() != ListingStatus.SOLD);
        var delete = UiControls.button("Delete Listing", "delete-listing", () -> {
            if (app.confirm("Delete Listing", "Permanently remove this listing and its managed photos?")) {
                page.perform(() -> app.runtime.getListings().deleteListing(listing.getId()),
                        ignored -> app.replace(this::mine));
            }
        });
        delete.setDisable(!listing.isDeletable());
        page.body.getChildren().add(UiControls.actions(edit, archive, delete));
        page.body.getChildren().add(UiControls.label("Only available listings can be edited. Reserved listings cannot "
                + "be archived. Deletion requires an available/archived listing without offer history.", "hint"));
        app.offers.incoming(page, listing, delete);
    }
}
