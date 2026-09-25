package hotshop;

import java.io.IOException;
import java.nio.file.Path;

import hotshop.ui.MarketplaceUi;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * Initializes local storage and displays the authenticated marketplace application.
 */
public class Main extends Application {
    private static final System.Logger LOGGER = System.getLogger(Main.class.getName());
    private ApplicationRuntime runtime;
    private Exception startupFailure;

    /** JavaFX invokes init off the application thread, before displaying the login window. */
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
        new MarketplaceUi(stage, runtime);
        stage.show();
    }

    @Override
    public void stop() throws IOException {
        if (runtime != null) {
            runtime.close();
        }
    }
}
