package hotshop.ui;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.function.Supplier;

import hotshop.service.ListingWithSeller;
import hotshop.service.MeetupSummary;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

/** Reusable cards whose TilePane wraps to the available viewport width. */
final class ListingCards {
    static final double CARD_WIDTH = 240;
    static final double CARD_HEIGHT = 304;
    static final double CONTENT_WIDTH = 208;
    static final double IMAGE_HEIGHT = 130;
    /** Room for two date lines (an overnight meetup), one time line, and two place lines. */
    static final double MEETUP_HEIGHT = 120;
    static final double DETAILS_GAP = 8;
    /** A seller card is a compact card plus its meetup area, so every seller card has the same height. */
    static final double SELLER_CARD_HEIGHT = CARD_HEIGHT + DETAILS_GAP + MEETUP_HEIGHT;
    private static final double PLACE_HEIGHT = 40;
    private static final double TITLE_HEIGHT = 52;

    private ListingCards() {
    }

    static TilePane grid() {
        TilePane grid = new TilePane(16, 16);
        grid.setPrefColumns(3);
        grid.setPrefTileWidth(CARD_WIDTH);
        grid.setTileAlignment(Pos.TOP_LEFT);
        return grid;
    }

    static Button card(MarketplaceUi app, ListingWithSeller value, Integer pending) {
        return card(app, value, pending, null);
    }

    /** Seller cards reserve the same meetup area, including listings without a sale. */
    static Button card(MarketplaceUi app, ListingWithSeller value, Integer pending, MeetupSummary meetupSummary) {
        var listing = value.listing();
        Supplier<Path> image = listing.getImages().isEmpty() ? null
                : () -> app.runtime.getListingImagePath(listing.getImages().getFirst().filename());
        Label title = UiControls.label(listing.getDetails().title(), "listing-card-title");
        fixSize(title, CONTENT_WIDTH, TITLE_HEIGHT);
        title.setAlignment(Pos.TOP_LEFT);
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        VBox details = new VBox(DETAILS_GAP, UiImages.display(image, CONTENT_WIDTH, IMAGE_HEIGHT), title,
                UiControls.label(UiControls.money(listing.getDetails().priceCents()), "price"));
        HBox footer = new HBox(8);
        footer.setAlignment(Pos.CENTER_LEFT);
        if (pending != null) {
            footer.getChildren().addAll(UiControls.label(UiControls.title(listing.getStatus()), "badge"),
                    UiControls.label(pending + (pending == 1 ? " pending offer" : " pending offers"),
                            "listing-card-footer"));
        } else {
            footer.getChildren().add(UiControls.label(UiControls.title(listing.getDetails().condition()),
                    "listing-card-footer"));
        }
        details.getChildren().add(footer);
        if (pending != null) {
            details.getChildren().add(meetupArea(meetupSummary));
        }
        Button card = UiControls.button("", "listing-card",
                () -> app.navigate(() -> app.listings.details(listing.getId())));
        card.setAccessibleText(listing.getDetails().title() + ", "
                + UiControls.money(listing.getDetails().priceCents()));
        card.setGraphic(details);
        fixWidth(details, CONTENT_WIDTH);
        fixSize(card, CARD_WIDTH, pending == null ? CARD_HEIGHT : SELLER_CARD_HEIGHT);
        card.getStyleClass().addAll("card", "listing-card");
        return card;
    }

    private static VBox meetupArea(MeetupSummary summary) {
        VBox area = new VBox(2);
        fixHeight(area, MEETUP_HEIGHT);
        if (summary == null) {
            return area;
        }
        if (summary.meetup().isEmpty()) {
            Label state = UiControls.label(MeetupBar.summary(summary, ZoneId.systemDefault()), "muted");
            state.setId("listing-meetup-summary");
            area.getChildren().add(state);
            return area;
        }
        area.setId("listing-meetup-summary");
        var time = summary.meetup().orElseThrow().getTime();
        Label date = UiControls.label(MeetupBar.dates(time, ZoneId.systemDefault()), "muted");
        date.setId("listing-meetup-date");
        date.setMinHeight(Region.USE_PREF_SIZE);
        Label clock = UiControls.label(MeetupBar.clockRange(time, ZoneId.systemDefault()), "muted");
        clock.setId("listing-meetup-time");
        clock.setMinHeight(Region.USE_PREF_SIZE);
        Label place = UiControls.label(time.location(), "muted");
        place.setId("listing-meetup-place");
        fixHeight(place, PLACE_HEIGHT);
        place.setMaxWidth(CONTENT_WIDTH);
        place.setAlignment(Pos.TOP_LEFT);
        place.setTextOverrun(OverrunStyle.ELLIPSIS);
        area.getChildren().addAll(date, clock, place);
        return area;
    }

    private static void fixSize(Region region, double width, double height) {
        fixWidth(region, width);
        fixHeight(region, height);
    }

    private static void fixWidth(Region region, double width) {
        region.setMinWidth(width);
        region.setPrefWidth(width);
        region.setMaxWidth(width);
    }

    private static void fixHeight(Region region, double height) {
        region.setMinHeight(height);
        region.setPrefHeight(height);
        region.setMaxHeight(height);
    }
}
