package hotshop.ui;

import java.nio.file.Path;
import java.util.function.Supplier;

import hotshop.service.ListingWithSeller;
import javafx.scene.control.Button;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

/** Reusable cards whose TilePane wraps to the available viewport width. */
final class ListingCards {
    private ListingCards() {
    }

    static TilePane grid() {
        TilePane grid = new TilePane(16, 16);
        grid.setPrefColumns(3);
        grid.setPrefTileWidth(240);
        return grid;
    }

    static Button card(MarketplaceUi app, ListingWithSeller value, Integer pending) {
        var listing = value.listing();
        Supplier<Path> image = listing.getImages().isEmpty() ? null
                : () -> app.runtime.getListingImagePath(listing.getImages().getFirst().filename());
        VBox details = new VBox(8, UiImages.display(image, 208, 130),
                UiControls.label(listing.getDetails().title(), "section-title"),
                UiControls.label(UiControls.money(listing.getDetails().priceCents()), "price"),
                UiControls.label(UiControls.title(listing.getDetails().condition()), "muted"),
                UiControls.label(UiControls.title(listing.getStatus()), "badge"));
        if (pending != null) {
            details.getChildren().add(UiControls.label(pending + " pending offers", "muted"));
        }
        Button card = UiControls.button("", "listing-card",
                () -> app.navigate(() -> app.listings.details(listing.getId())));
        card.setAccessibleText(listing.getDetails().title() + ", "
                + UiControls.money(listing.getDetails().priceCents()));
        card.setGraphic(details);
        card.setPrefWidth(240);
        card.setMaxHeight(Double.MAX_VALUE);
        card.getStyleClass().add("card");
        return card;
    }
}
