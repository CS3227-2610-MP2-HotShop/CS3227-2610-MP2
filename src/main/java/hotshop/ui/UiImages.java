package hotshop.ui;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Supplier;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.Node;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/** Bounded background image display with an explicit missing-image fallback. */
final class UiImages {
    private UiImages() {
    }

    static Node display(Supplier<Path> source, double width, double height) {
        StackPane pane = new StackPane(UiControls.label(width < 64 ? "?" : "No image", "muted"));
        pane.setAccessibleText("Image");
        pane.setMinSize(width, height);
        pane.setPrefSize(width, height);
        pane.setMaxSize(width, height);
        pane.getStyleClass().add("image-placeholder");
        if (source == null) {
            return pane;
        }
        try {
            Image image = new Image(source.get().toUri().toString(), width, height, true, true, true);
            ImageView view = new ImageView(image);
            view.setPreserveRatio(true);
            view.setFitWidth(width);
            view.setFitHeight(height);
            image.progressProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue.doubleValue() == 1 && !image.isError()) {
                    pane.getChildren().setAll(view);
                }
            });
            if (image.getProgress() == 1 && !image.isError()) {
                pane.getChildren().setAll(view);
            }
        } catch (IllegalArgumentException failure) {
            pane.getChildren().setAll(UiControls.label("Image unavailable", "muted"));
        }
        return pane;
    }

    static Path choose(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a JPEG or PNG image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JPEG / PNG", "*.jpg", "*.jpeg", "*.png"));
        File selected = chooser.showOpenDialog(owner);
        return selected == null ? null : selected.toPath();
    }
}
