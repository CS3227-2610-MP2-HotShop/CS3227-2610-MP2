package hotshop;

import java.io.IOException;
import java.util.Objects;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Displays the HotShop welcome window.
 */
public class Main extends Application {
    private static final int WINDOW_WIDTH = 800;
    private static final int WINDOW_HEIGHT = 600;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                Main.class.getResource("main.fxml"), "Missing main.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        scene.getStylesheets().add(Objects.requireNonNull(
                Main.class.getResource("styles.css"), "Missing styles.css").toExternalForm());
        stage.setTitle("HotShop");
        stage.setScene(scene);
        stage.show();
    }
}
