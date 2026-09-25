package hotshop.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.UUID;

import hotshop.ApplicationRuntime;
import hotshop.model.User;
import hotshop.service.PublicProfile;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Application shell and guarded navigation. Business operations belong to the runtime services. */
public final class MarketplaceUi {
    private static final int INITIAL_WIDTH = 1100;
    private static final int INITIAL_HEIGHT = 750;
    private static final int MINIMUM_WIDTH = 960;
    private static final int MINIMUM_HEIGHT = 640;
    final ApplicationRuntime runtime;
    final Stage stage;
    final SearchState searchState = new SearchState();
    final ListingPages listings;
    final OfferPages offers;
    final SalePages sales;
    private final BorderPane root = new BorderPane();
    private final Deque<Runnable> history = new ArrayDeque<>();
    private final AccountPages accounts;
    private UiPage current;
    private Runnable route;
    private UUID userId;
    private ScrollPane content;

    /** Installs the initial logged-out scene; the caller owns showing and closing the runtime. */
    public MarketplaceUi(Stage stage, ApplicationRuntime runtime) {
        this.stage = stage;
        this.runtime = runtime;
        accounts = new AccountPages(this);
        listings = new ListingPages(this);
        offers = new OfferPages(this);
        sales = new SalePages(this);
        Scene scene = new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT);
        scene.getStylesheets().add(Objects.requireNonNull(
                MarketplaceUi.class.getResource("/hotshop/styles.css")).toExternalForm());
        stage.setTitle("HotShop");
        stage.setScene(scene);
        stage.setWidth(INITIAL_WIDTH);
        stage.setHeight(INITIAL_HEIGHT);
        stage.setMinWidth(MINIMUM_WIDTH);
        stage.setMinHeight(MINIMUM_HEIGHT);
        stage.setOnCloseRequest(event -> {
            if (!canLeave()) {
                event.consume();
            }
        });
        login("");
    }

    UUID userId() {
        return userId;
    }

    boolean isCurrent(UiPage page) {
        return current == page;
    }

    UiPage page(String title) {
        current = new UiPage(this, title);
        content = new ScrollPane(current);
        content.setFitToWidth(true);
        content.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        root.setCenter(content);
        return current;
    }

    ScrollPane scroll() {
        return content;
    }

    void message(String text) {
        current.message(text);
    }

    void navigate(Runnable next) {
        if (!canLeave()) {
            return;
        }
        if (current != null) {
            current.leave();
        }
        if (route != null) {
            history.push(route);
        }
        route = next;
        next.run();
    }

    void back() {
        if (!history.isEmpty() && canLeave()) {
            current.leave();
            route = history.pop();
            route.run();
        }
    }

    boolean hasHistory() {
        return !history.isEmpty();
    }

    void replace(Runnable next) {
        route = next;
        next.run();
    }

    Button profileLink(PublicProfile profile, String id) {
        Button button = UiControls.button(profile.displayName(), id,
                () -> navigate(() -> accounts.profile(profile.id())));
        button.setGraphic(UiImages.display(profile.profileImage().isEmpty() ? null
                : () -> runtime.getProfileImagePath(profile.profileImage().orElseThrow()), 36, 36));
        return button;
    }

    boolean canLeave() {
        if (current == null) {
            return true;
        }
        if (current.isBusy()) {
            return false;
        }
        return !current.isDirty() || confirm("Discard Changes", "Your unsaved changes will be lost.");
    }

    boolean confirm(String action, String consequence) {
        ButtonType accept = new ButtonType(action, ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, consequence, ButtonType.CANCEL, accept);
        alert.initOwner(stage);
        alert.setTitle(action);
        alert.setHeaderText(action + "?");
        alert.getDialogPane().setId("confirmation-dialog");
        return alert.showAndWait().filter(accept::equals).isPresent();
    }

    void loggedIn(User user) {
        userId = user.getId();
        searchState.reset();
        history.clear();
        root.setLeft(sidebar());
        route = this::search;
        search();
    }

    void login(String username) {
        userId = null;
        searchState.reset();
        history.clear();
        root.setLeft(null);
        route = () -> accounts.login(username);
        route.run();
    }

    private void logout() {
        if (canLeave()) {
            current.perform(runtime.getAccounts()::logout, ignored -> login(""));
        }
    }

    private ScrollPane sidebar() {
        VBox links = new VBox(8, UiControls.label("HotShop", "brand"));
        links.getStyleClass().add("sidebar");
        links.setPrefWidth(225);
        links.getChildren().add(nav("Search", "search", this::search));
        links.getChildren().add(UiControls.label("BUYING", "nav-heading"));
        links.getChildren().addAll(nav("My Offers", "offers", offers::mine),
                nav("My Purchases", "purchases", () -> sales.list(false)));
        links.getChildren().addAll(UiControls.future("Wishlist", "nav-wishlist"),
                UiControls.future("Meetups", "nav-meetups"));
        links.getChildren().add(UiControls.label("SELLING", "nav-heading"));
        links.getChildren().addAll(nav("Dashboard", "dashboard", sales::dashboard),
                nav("My Listings", "listings", listings::mine), nav("My Sales", "sales", () -> sales.list(true)));
        links.getChildren().add(UiControls.future("Availability & Meetups", "nav-availability"));
        links.getChildren().addAll(UiControls.future("Conversations", "nav-conversations"),
                nav("My Profile", "profile", () -> accounts.profile(userId)),
                UiControls.button("Log out", "nav-logout", this::logout));
        ScrollPane sidebar = new ScrollPane(links);
        sidebar.setFitToWidth(true);
        sidebar.setPrefWidth(245);
        sidebar.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sidebar;
    }

    private Button nav(String text, String id, Runnable destination) {
        Button button = UiControls.button(text, "nav-" + id, () -> navigate(destination));
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    void search() {
        new SearchPage(this);
    }
}
