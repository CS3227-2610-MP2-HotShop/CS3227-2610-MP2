package hotshop.ui;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

import hotshop.service.SaleRole;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
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
