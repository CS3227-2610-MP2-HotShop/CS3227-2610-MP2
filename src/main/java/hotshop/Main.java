package hotshop;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * Displays the HotShop welcome window.
 */
public class Main extends Application {
    private static final int WINDOW_WIDTH = 800;
    private static final int WINDOW_HEIGHT = 600;
    private static final System.Logger LOGGER = System.getLogger(Main.class.getName());
    private ApplicationRuntime runtime;
    private Exception startupFailure;

    /** JavaFX invokes init off the application thread, before displaying the welcome window. */
    @Override
    public void init() {
        try {
            String defaultDirectory = Path.of(System.getProperty("user.home"), ".hotshop").toString();
            runtime = ApplicationRuntime.open(Path.of(System.getProperty("hotshop.dataDir", defaultDirectory)));
        } catch (Exception exception) {
            startupFailure = exception;
            LOGGER.log(System.Logger.Level.ERROR, "Unable to initialize HotShop", exception);
        }
    }

    @Override
    public void start(Stage stage) throws IOException {
        if (startupFailure != null) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("HotShop startup failed");
            alert.setHeaderText("Unable to open HotShop data");
            alert.setContentText("Check that the data folder is writable and no other HotShop instance is using it. "
                    + "Existing data has not been reset. See the application log for details.");
            alert.showAndWait();
            Platform.exit();
            return;
        }
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

    @Override
    public void stop() throws IOException {
        if (runtime != null) {
            runtime.close();
        }
    }
}
