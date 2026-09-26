package hotshop.ui;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

import hotshop.service.SaleRole;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.Node;

/** Small presentation building blocks shared by the feature screens. */
final class UiControls {
    private UiControls() {
    }

    static Label label(String text, String style) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add(style);
        return label;
    }

    static Button button(String text, String id, Runnable action) {
        Button button = new Button(text);
        button.setId(id);
        button.setOnAction(event -> action.run());
        return button;
    }

    static Button primary(String text, String id, Runnable action) {
        Button button = button(text, id, action);
        button.getStyleClass().add("primary");
        return button;
    }

    static Button future(String text, String id) {
        Button button = new Button(text + " — Coming soon");
        button.setId(id);
        button.setDisable(true);
        button.setWrapText(true);
        return button;
    }

    /** A conversation's one-row status bar: the text takes the spare width, then the given buttons. */
    static HBox bar(String text, String textId, Node... buttons) {
        Label label = label(text, "section-title");
        label.setId(textId);
        label.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(label, Priority.ALWAYS);
        HBox row = new HBox(10, label);
        row.getChildren().addAll(buttons);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("offer-bar");
        return row;
    }

    static FlowPane actions(Node... nodes) {
        return new FlowPane(10, 10, nodes);
    }

    static String money(long cents) {
        return "S$" + BigDecimal.valueOf(cents, 2).toPlainString();
    }

    static String title(Enum<?> value) {
        String text = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** How a participant's side of a listing reads on a badge: "Buying" or "Selling". */
    static String role(SaleRole role) {
        return role == SaleRole.BUYER ? "Buying" : "Selling";
    }

    static String time(Instant value) {
        return DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault()).format(value);
    }
}
