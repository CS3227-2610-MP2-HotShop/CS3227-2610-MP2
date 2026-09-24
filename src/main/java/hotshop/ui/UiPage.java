package hotshop.ui;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import hotshop.service.ServiceException;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;

/** Owns a page's async lifecycle; detached pages never render late responses. */
final class UiPage extends VBox {
    private static final System.Logger LOGGER = System.getLogger(UiPage.class.getName());
    final VBox body = new VBox(16);
    private final MarketplaceUi app;
    private final Label status = UiControls.label("", "muted");
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Button retry = UiControls.button("Retry", "retry", () -> { });
    private BooleanSupplier dirty = () -> false;
    private Runnable leaving = () -> { };
    private boolean isBusy;
    private Consumer<Throwable> validation = failure -> { };

    UiPage(MarketplaceUi app, String title) {
        this.app = app;
        body.setId("page-body");
        getStyleClass().add("page");
        Label heading = UiControls.label(title, "page-title");
        heading.setId("page-title");
        status.setId("page-status");
        status.visibleProperty().bind(status.textProperty().isNotEmpty());
        status.managedProperty().bind(status.visibleProperty());
        progress.setMaxSize(24, 24);
        progress.setVisible(false);
        progress.managedProperty().bind(progress.visibleProperty());
        retry.setVisible(false);
        retry.managedProperty().bind(retry.visibleProperty());
        Button back = UiControls.button("Back", "back", app::back);
        back.setVisible(app.hasHistory());
        back.setManaged(back.isVisible());
        getChildren().addAll(back, heading,
                progress, status, retry, body);
    }

    boolean isBusy() {
        return isBusy;
    }

    boolean isDirty() {
        return dirty.getAsBoolean();
    }

    void setDirty(BooleanSupplier value) {
        dirty = value;
    }

    void setLeaving(Runnable action) {
        leaving = action;
    }

    void leave() {
        leaving.run();
    }

    void message(String text) {
        status.getStyleClass().setAll("success");
        status.setText(text);
    }

    void setValidation(Consumer<Throwable> handler) {
        validation = handler;
    }

    void error(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        status.getStyleClass().setAll("error");
        if (cause instanceof ServiceException || cause instanceof IllegalArgumentException) {
            status.setText(cause.getMessage());
            validation.accept(cause);
        } else {
            LOGGER.log(System.Logger.Level.ERROR, "Screen operation failed", cause);
            status.setText("This operation could not be completed. Please try again.");
        }
    }

    <T> void load(Supplier<CompletableFuture<T>> work, Consumer<T> success) {
        run(work, success, true, failure -> { });
    }

    <T> void perform(Supplier<CompletableFuture<T>> work, Consumer<T> success) {
        run(work, success, false, failure -> { });
    }

    <T> void perform(Supplier<CompletableFuture<T>> work, Consumer<T> success, Consumer<Throwable> failure) {
        run(work, success, false, failure);
    }

    private <T> void run(Supplier<CompletableFuture<T>> work, Consumer<T> success, boolean canRetry,
            Consumer<Throwable> failed) {
        if (isBusy) {
            return;
        }
        setBusy(true);
        status.setText("");
        retry.setVisible(false);
        CompletableFuture<T> future;
        try {
            future = work.get();
        } catch (RuntimeException failure) {
            setBusy(false);
            error(failure);
            failed.accept(failure);
            return;
        }
        future.whenComplete((value, failure) -> Platform.runLater(() -> {
            setBusy(false);
            if (!app.isCurrent(this)) {
                return;
            }
            if (failure == null) {
                success.accept(value);
            } else {
                error(failure);
                failed.accept(failure);
                retry.setVisible(canRetry);
                retry.setOnAction(event -> run(work, success, true, failed));
            }
        }));
    }

    private void setBusy(boolean value) {
        isBusy = value;
        body.setDisable(value);
        progress.setVisible(value);
    }
}
