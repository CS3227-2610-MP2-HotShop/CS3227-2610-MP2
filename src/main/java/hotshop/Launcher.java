package hotshop;

/**
 * Provides the entry point for the executable JavaFX JAR.
 */
public final class Launcher {
    private Launcher() {
    }

    /**
     * Starts HotShop.
     *
     * @param args command-line arguments passed to JavaFX
     */
    public static void main(String[] args) {
        javafx.application.Application.launch(Main.class, args);
    }
}
