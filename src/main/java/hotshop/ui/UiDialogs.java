package hotshop.ui;

import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;

/** Modal forms stay open on validation/storage errors and cannot double-submit. */
final class UiDialogs {
    private UiDialogs() {
    }

    static <T> void form(MarketplaceUi app, UiPage page, String title, String submitText, UiForm form,
            BooleanSupplier isValid, Supplier<CompletableFuture<T>> work, Consumer<T> success) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(app.stage);
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        ButtonType submit = new ButtonType(submitText, ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, submit);
        Label status = UiControls.label("", "error");
        status.setId("dialog-status");
        form.getChildren().add(status);
        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(460);
        scroll.setPrefViewportHeight(Math.min(440, app.stage.getHeight() - 180));
        dialog.getDialogPane().setContent(scroll);
        dialog.setResizable(true);
        var submitButton = dialog.getDialogPane().lookupButton(submit);
        submitButton.setId("dialog-submit");
        dialog.setOnCloseRequest(event -> {
            if (page.isBusy()) {
                event.consume();
            }
        });
        submitButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (!isValid.getAsBoolean() || page.isBusy()) {
                return;
            }
            dialog.getDialogPane().setDisable(true);
            status.setText("Saving…");
            page.perform(work, result -> {
                dialog.getDialogPane().setDisable(false);
                dialog.close();
                success.accept(result);
            }, failure -> {
                dialog.getDialogPane().setDisable(false);
                form.serviceError(failure);
                Throwable cause = failure;
                while (cause.getCause() != null && cause instanceof java.util.concurrent.CompletionException) {
                    cause = cause.getCause();
                }
                status.setText(cause instanceof hotshop.service.ServiceException ? cause.getMessage()
                        : "Could not save. Please check your input and try again.");
            });
        });
        dialog.show();
    }
}
