package hotshop.ui;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Supplier;

import hotshop.service.ListingWithSeller;
import hotshop.service.MeetupSummary;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

/** Reusable cards whose TilePane wraps to the available viewport width. */
final class ListingCards {
    private static final double CARD_WIDTH = 240;
    private static final double CARD_HEIGHT = 304;
    private static final double SELLER_CARD_HEIGHT = 432;
    private static final double MEETUP_HEIGHT = 120;
    private static final double PLACE_HEIGHT = 40;
    private static final double CONTENT_WIDTH = 208;
    private static final double IMAGE_HEIGHT = 130;
    private static final double TITLE_HEIGHT = 52;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

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
        title.setMinWidth(CONTENT_WIDTH);
        title.setPrefWidth(CONTENT_WIDTH);
        title.setMaxWidth(CONTENT_WIDTH);
        title.setMinHeight(TITLE_HEIGHT);
        title.setPrefHeight(TITLE_HEIGHT);
        title.setMaxHeight(TITLE_HEIGHT);
        title.setAlignment(Pos.TOP_LEFT);
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        VBox details = new VBox(8, UiImages.display(image, CONTENT_WIDTH, IMAGE_HEIGHT), title,
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
        details.setMinWidth(CONTENT_WIDTH);
        details.setPrefWidth(CONTENT_WIDTH);
        details.setMaxWidth(CONTENT_WIDTH);
        double height = pending == null ? CARD_HEIGHT : SELLER_CARD_HEIGHT;
        card.setMinSize(CARD_WIDTH, height);
        card.setPrefSize(CARD_WIDTH, height);
        card.setMaxSize(CARD_WIDTH, height);
        card.getStyleClass().addAll("card", "listing-card");
        return card;
    }

    private static VBox meetupArea(MeetupSummary summary) {
        VBox area = new VBox(2);
        area.setMinHeight(MEETUP_HEIGHT);
        area.setPrefHeight(MEETUP_HEIGHT);
        area.setMaxHeight(MEETUP_HEIGHT);
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
        var start = time.startAt().atZone(ZoneId.systemDefault());
        var end = time.endAt().atZone(ZoneId.systemDefault());
        String dates = DATE.format(start);
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            dates += " to\n" + DATE.format(end);
        }
        Label date = UiControls.label(dates, "muted");
        date.setId("listing-meetup-date");
        date.setMinHeight(VBox.USE_PREF_SIZE);
        Label clock = UiControls.label(CLOCK.format(start) + " to " + CLOCK.format(end), "muted");
        clock.setId("listing-meetup-time");
        clock.setMinHeight(VBox.USE_PREF_SIZE);
        Label place = UiControls.label(time.location(), "muted");
        place.setId("listing-meetup-place");
        place.setMinHeight(PLACE_HEIGHT);
        place.setPrefHeight(PLACE_HEIGHT);
        place.setMaxHeight(PLACE_HEIGHT);
        place.setMaxWidth(CONTENT_WIDTH);
        place.setAlignment(Pos.TOP_LEFT);
        place.setTextOverrun(OverrunStyle.ELLIPSIS);
        area.getChildren().addAll(date, clock, place);
        return area;
    }
}
