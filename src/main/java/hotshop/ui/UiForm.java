package hotshop.ui;

import java.math.BigDecimal;
import java.util.concurrent.CompletionException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import hotshop.service.ServiceException;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.Node;

/** Labelled fields with adjacent validation messages; services remain authoritative. */
final class UiForm extends VBox {
    private final Map<String, TextInputControl> inputs = new LinkedHashMap<>();
    private final Map<String, Label> errors = new LinkedHashMap<>();
    private final Map<ServiceException.Code, String> serviceFields = new EnumMap<>(ServiceException.Code.class);
    private boolean isValid = true;

    UiForm() {
        super(12);
        getStyleClass().add("form-panel");
        setMaxWidth(680);
    }

    TextField text(String id, String title, String value) {
        TextField field = new TextField(value == null ? "" : value);
        addInput(id, title, field, field);
        return field;
    }

    TextArea area(String id, String title, String value) {
        TextArea field = new TextArea(value == null ? "" : value);
        field.setWrapText(true);
        field.setPrefRowCount(4);
        addInput(id, title, field, field);
        return field;
    }

    PasswordField password(String id, String title) {
        PasswordField masked = new PasswordField();
        TextField revealed = new TextField();
        revealed.setId(id + "-visible");
        revealed.textProperty().bindBidirectional(masked.textProperty());
        CheckBox show = new CheckBox("Show " + title.toLowerCase());
        show.setId(id + "-show");
        masked.visibleProperty().bind(show.selectedProperty().not());
        masked.managedProperty().bind(masked.visibleProperty());
        revealed.visibleProperty().bind(show.selectedProperty());
        revealed.managedProperty().bind(revealed.visibleProperty());
        addInput(id, title, masked, new VBox(6, new StackPane(masked, revealed), show));
        return masked;
    }

    void field(String title, Node node) {
        Label label = new Label(title);
        label.setLabelFor(node);
        getChildren().add(new VBox(5, label, node));
    }

    void field(String id, String title, Node node) {
        field(title, node);
        Label error = UiControls.label("", "error");
        error.setId(id + "-error");
        error.managedProperty().bind(error.textProperty().isNotEmpty());
        error.visibleProperty().bind(error.managedProperty());
        errors.put(id, error);
        getChildren().add(error);
    }

    void clear(String id) {
        inputs.get(id).clear();
    }

    private void addInput(String id, String title, TextInputControl input, Node display) {
        input.setId(id);
        inputs.put(id, input);
        Label error = UiControls.label("", "error");
        error.setId(id + "-error");
        error.managedProperty().bind(error.textProperty().isNotEmpty());
        error.visibleProperty().bind(error.managedProperty());
        errors.put(id, error);
        Label label = new Label(title);
        label.setLabelFor(input);
        getChildren().add(new VBox(5, label, display, error));
    }

    void clearErrors() {
        isValid = true;
        errors.values().forEach(error -> error.setText(""));
    }

    void reject(String id, String message) {
        isValid = false;
        errors.get(id).setText(message);
    }

    boolean isValid() {
        return isValid;
    }

    String value(String id) {
        return inputs.get(id).getText();
    }

    void textLength(String id, int maximum, boolean isRequired) {
        String value = value(id).strip();
        if (isRequired && value.isEmpty()) {
            reject(id, "This field is required.");
        } else if (value.codePointCount(0, value.length()) > maximum) {
            reject(id, "Use at most " + maximum + " characters.");
        }
    }

    Long cents(String id, boolean isOptional) {
        String value = value(id).strip();
        if (isOptional && value.isEmpty()) {
            return null;
        }
        try {
            if (!value.matches("[0-9]+(\\.[0-9]{1,2})?")) {
                throw new IllegalArgumentException();
            }
            return new BigDecimal(value).movePointRight(2).longValueExact();
        } catch (IllegalArgumentException | ArithmeticException failure) {
            reject(id, "Enter an SGD amount with at most two decimal places.");
            return null;
        }
    }

    void matchingPasswords(String first, String second) {
        if (!value(first).equals(value(second))) {
            reject(second, "Passwords do not match.");
        }
    }

    void mapServiceError(ServiceException.Code code, String field) {
        serviceFields.put(code, field);
    }

    void serviceError(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof ServiceException exception) {
            String field = serviceFields.get(exception.getCode());
            if (field != null) {
                reject(field, exception.getMessage());
            }
        }
    }
}
