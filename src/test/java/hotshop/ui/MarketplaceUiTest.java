package hotshop.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import hotshop.ApplicationRuntime;
import hotshop.model.Category;
import hotshop.model.Condition;
import hotshop.model.Meetup;
import hotshop.service.ListingDraft;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.PixelFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Exercises real JavaFX controls against temporary SQLite data, without mocking services. */
class MarketplaceUiTest {
    private static final Duration MEETUP_LENGTH = Duration.ofMinutes(30);
    @TempDir
    Path directory;
    private ApplicationRuntime runtime;
    private Stage stage;
    private MarketplaceUi ui;

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            started.countDown();
        });
        assertTrue(started.await(15, TimeUnit.SECONDS));
    }

    @BeforeEach
    void openWindow() throws Exception {
        runtime = ApplicationRuntime.open(directory);
        fx(() -> {
            stage = new Stage();
            ui = new MarketplaceUi(stage, runtime);
            stage.show();
            return null;
        });
    }

    @AfterEach
    void closeWindow() throws Exception {
        fx(() -> {
            stage.hide();
            return null;
        });
        runtime.close();
    }

    @Test
    void register_validAccount_returnsToLoginAndOpensEmptySearchAfterLogin() throws Exception {
        snapshot("login-initial", 1100, 750);
        click("register-link");
        snapshot("register-minimum", 960, 640);
        type("username", "alice");
        type("display-name", "Alice");
        type("password", "Sample1!");
        type("confirm-password", "Sample1!");
        click("register-submit");
        awaitText("page-title", "Log in");
        assertEquals("alice", fx(() -> ((TextInputControl) stage.getScene().lookup("#username")).getText()));
        type("password", "Sample1!");
        click("login-submit");
        awaitText("page-title", "Search");
        assertEquals("Search for an item, or press Search to browse all listings.",
                fx(() -> ((Labeled) stage.getScene().lookup("#search-guidance")).getText()));
        assertTrue(fx(() -> stage.getScene().lookup("#nav-notifications").isDisabled()));
        assertEquals(960, fx(() -> stage.getMinWidth()));
        assertEquals(640, fx(() -> stage.getMinHeight()));
    }

    @Test
    void createListing_validForm_appearsInBuyerSearchAndPublicProfile() throws Exception {
        runtime.getAccounts().register("seller", "Sample1!", "Seller").join();
        runtime.getAccounts().register("buyer", "Sample1!", "Buyer").join();
        login("seller");
        click("nav-listings");
        awaitText("page-title", "My Listings");
        awaitReady();
        click("create-listing");
        awaitText("page-title", "Create Listing");
        awaitReady();
        type("title", "Wooden desk");
        type("description", "A sturdy desk");
        type("price", "45.50");
        type("pickup-location", "Campus gate");
        fx(() -> {
            ((ComboBox<?>) stage.getScene().lookup("#category")).getSelectionModel()
                    .select(Category.FURNITURE.ordinal());
            ((ComboBox<?>) stage.getScene().lookup("#condition")).getSelectionModel()
                    .select(Condition.GOOD.ordinal());
            return null;
        });
        snapshot("listing-editor-minimum", 960, 640);
        click("save-listing");
        awaitText("listing-title", "Wooden desk");
        awaitReady();
        snapshot("listing-owner-minimum", 960, 640);
        click("nav-logout");
        awaitText("page-title", "Log in");
        login("buyer");
        type("search-query", "desk");
        click("search-submit");
        awaitText("results-count", "1 listing");
        snapshot("search-results-minimum", 960, 640);
        click("listing-card");
        awaitText("listing-title", "Wooden desk");
        awaitReady();
        click("seller-profile");
        awaitText("profile-name", "Seller");
        awaitReady();
        snapshot("public-profile-minimum", 960, 640);
        assertTrue(fx(() -> stage.getScene().lookup("#listing-card") != null));
    }

    private void login(String username) throws Exception {
        type("username", username);
        type("password", "Sample1!");
        click("login-submit");
        awaitText("page-title", "Search");
        awaitReady();
    }

    @Test
    void offerAndComplete_twoParticipants_completesSaleThroughScreens() throws Exception {
        seedListing();
        login("buyer");
        click("search-submit");
        awaitText("results-count", "1 listing");
        click("listing-card");
        awaitText("listing-title", "Desk");
        awaitReady();
        click("make-offer");
        dialogType("offer-amount", "40.00");
        dialogClick("dialog-submit");
        awaitText("pending-offer", "Your pending offer: S$40.00");
        switchUser("seller");
        click("nav-listings");
        awaitReady();
        click("listing-card");
        awaitReady();
        confirm("accept-offer", "Accept Offer");
        awaitText("page-title", "Sale Details");
        awaitReady();
        snapshot("sale-details-minimum", 960, 640);
        confirm("sale-confirm-completion", "Confirm Completion");
        awaitText("sale-next-step", "Waiting for the other participant to confirm");
        switchUser("buyer");
        click("nav-purchases");
        awaitReady();
        click("sale-detail");
        awaitReady();
        confirm("sale-confirm-completion", "Confirm Completion");
        awaitText("sale-status", "Completed");
        assertEquals(hotshop.model.TransactionStatus.COMPLETED,
                runtime.getTransactions().getMyPurchases().join().getFirst().sale().getStatus());
    }

    @Test
    void chatWithSeller_firstMessage_startsConversation() throws Exception {
        seedListing();
        login("buyer");
        click("search-submit");
        awaitText("results-count", "1 listing");
        click("listing-card");
        awaitText("listing-title", "Desk");
        awaitReady();
        click("chat-seller");
        awaitText("page-title", "Desk");
        awaitReady();
        awaitText("send-hint", "Send a message to start a conversation with Seller.");
        type("message-input", "Is it still available?");
        click("send-message");
        awaitText("latest-message", "Is it still available?");
        assertEquals("Is it still available?", runtime.getChats().getConversations().join().getFirst().preview());
    }

    @Test
    void conversations_unreadMessage_showsCountUntilSellerOpensAndReplies() throws Exception {
        seedListing();
        runtime.getAccounts().login("buyer", "Sample1!").join();
        var listing = runtime.getListings().searchListings(hotshop.service.ListingSearch.all()).join().getFirst();
        runtime.getChats().messageSeller(listing.listing().getId(), "Is it still available?").join();
        runtime.getAccounts().logout().join();
        login("seller");
        awaitText("nav-conversations", "Conversations (1)");
        click("nav-conversations");
        awaitText("page-title", "Conversations");
        awaitReady();
        awaitText("conversation-unread", "1 unread");
        snapshot("conversations-minimum", 960, 640);
        snapshot("conversations-default", 1100, 750);
        click("conversation-open");
        awaitText("page-title", "Desk");
        awaitReady();
        awaitText("nav-conversations", "Conversations");
        awaitText("latest-message", "Is it still available?");
        type("message-input", "Yes, it is.");
        click("send-message");
        awaitText("latest-message", "Yes, it is.");
        snapshot("conversation-minimum", 960, 640);
        snapshot("conversation-default", 1100, 750);
        assertTrue(fx(() -> stage.getScene().lookup("#message-scroll").getLayoutBounds().getHeight() >= 200));
    }

    @Test
    void chatWithBuyer_pendingOffer_acceptsFromOfferBarAndOpensSale() throws Exception {
        seedListing();
        submitOfferAsBuyer();
        login("seller");
        click("nav-listings");
        awaitReady();
        click("listing-card");
        awaitReady();
        click("chat-buyer");
        awaitText("page-title", "Desk");
        awaitReady();
        awaitText("offer-bar-text", "Offer of S$40.00 · Pending");
        confirm("offer-bar-accept", "Accept Offer");
        awaitText("page-title", "Sale Details");
        awaitReady();
        awaitText("sale-status", "Active");
    }

    @Test
    void openChat_activeSale_showsSaleMeetupInConversation() throws Exception {
        seedListing();
        var offer = submitOfferAsBuyer();
        runtime.getAccounts().login("seller", "Sample1!").join();
        runtime.getOffers().acceptOffer(offer.getId()).join();
        runtime.getAccounts().logout().join();
        login("seller");
        click("nav-sales");
        awaitReady();
        click("sale-detail");
        awaitReady();
        click("sale-open-chat");
        awaitText("page-title", "Desk");
        awaitReady();
        awaitText("meetup-bar-text", "No meetup times offered yet");
    }

    private hotshop.model.Offer submitOfferAsBuyer() {
        runtime.getAccounts().login("buyer", "Sample1!").join();
        var listing = runtime.getListings().searchListings(hotshop.service.ListingSearch.all()).join().getFirst();
        var offer = runtime.getOffers().submitOffer(listing.listing().getId(), 4000).join().offer();
        runtime.getAccounts().logout().join();
        return offer;
    }

    @Test
    void makeOffer_withMessage_showsMessageInConversation() throws Exception {
        seedListing();
        login("buyer");
        click("search-submit");
        awaitText("results-count", "1 listing");
        click("listing-card");
        awaitText("listing-title", "Desk");
        awaitReady();
        click("make-offer");
        dialogType("offer-amount", "40.00");
        dialogType("offer-message", "Could you do S$40?");
        dialogClick("dialog-submit");
        awaitText("pending-offer", "Your pending offer: S$40.00");
        awaitReady();
        click("chat-seller");
        awaitText("page-title", "Desk");
        awaitReady();
        awaitText("latest-message", "Could you do S$40?");
        awaitText("offer-bar-text", "Offer of S$40.00 · Pending");
    }

    @Test
    void withdrawOffer_fromOfferBar_showsWithdrawnAndOffersMakeOffer() throws Exception {
        seedListing();
        submitOfferAsBuyer();
        login("buyer");
        click("nav-conversations");
        awaitReady();
        click("conversation-open");
        awaitText("offer-bar-text", "Offer of S$40.00 · Pending");
        awaitReady();
        click("offer-bar-withdraw");
        awaitText("offer-bar-text", "Offer of S$40.00 · Withdrawn");
        awaitReady();
        assertTrue(fx(() -> stage.getScene().lookup("#offer-bar-make-offer") != null));
    }

    @Test
    void openConversation_soldListing_showsCompletedSaleAndDisablesSending() throws Exception {
        seedListing();
        runtime.getAccounts().login("buyer", "Sample1!").join();
        var listing = runtime.getListings().searchListings(hotshop.service.ListingSearch.all()).join().getFirst();
        var offer = runtime.getOffers().submitOffer(listing.listing().getId(), 4000).join().offer();
        runtime.getAccounts().logout().join();
        runtime.getAccounts().login("seller", "Sample1!").join();
        var sale = runtime.getOffers().acceptOffer(offer.getId()).join().transactionId();
        runtime.getTransactions().confirmCompletion(sale).join();
        runtime.getAccounts().logout().join();
        runtime.getAccounts().login("buyer", "Sample1!").join();
        runtime.getTransactions().confirmCompletion(sale).join();
        runtime.getAccounts().logout().join();
        login("buyer");
        click("nav-conversations");
        awaitReady();
        click("conversation-open");
        awaitText("page-title", "Desk");
        awaitReady();
        awaitText("meetup-bar-text", "Sale completed");
        awaitText("send-hint", "This listing is sold, so no new messages can be sent.");
        assertTrue(fx(() -> stage.getScene().lookup("#message-input").isDisabled()));
        assertTrue(fx(() -> stage.getScene().lookup("#send-message").isDisabled()));
    }

    @Test
    void listingDetails_archivedListingWithoutConversation_disablesChatWithSeller() throws Exception {
        seedListing();
        runtime.getAccounts().login("seller", "Sample1!").join();
        var listing = runtime.getListings().getMyListings().join().getFirst().listing().listing();
        runtime.getListings().archiveListing(listing.getId()).join();
        runtime.getAccounts().logout().join();
        login("buyer");
        fx(() -> {
            ui.navigate(() -> ui.listings.details(listing.getId()));
            return null;
        });
        awaitText("listing-title", "Desk");
        awaitReady();
        awaitText("chat-seller-hint", "Conversations can only be started about available or reserved listings.");
        assertTrue(fx(() -> stage.getScene().lookup("#chat-seller").isDisabled()));
    }

    @Test
    void navigate_unsentMessage_cancelKeepsDraftAndDiscardLeaves() throws Exception {
        seedListing();
        login("buyer");
        click("search-submit");
        awaitText("results-count", "1 listing");
        click("listing-card");
        awaitReady();
        click("chat-seller");
        awaitText("page-title", "Desk");
        awaitReady();
        type("message-input", "Draft question");
        confirm("nav-search", "Cancel");
        awaitText("page-title", "Desk");
        assertEquals("Draft question", fx(() ->
                ((TextInputControl) stage.getScene().lookup("#message-input")).getText()));
        confirm("nav-search", "Discard Changes");
        awaitText("page-title", "Search");
        assertTrue(runtime.getChats().getConversations().join().isEmpty());
    }

    @Test
    void messageInput_enterKey_sendsMessage() throws Exception {
        openChatWithSellerAsBuyer();
        type("message-input", "Sent with Enter");
        press("message-input", KeyCode.ENTER, false, false);
        awaitText("latest-message", "Sent with Enter");
        assertEquals("", fx(() -> ((TextInputControl) stage.getScene().lookup("#message-input")).getText()));
    }

    @Test
    void messageInput_shiftEnter_addsNewLineWithoutSending() throws Exception {
        openChatWithSellerAsBuyer();
        type("message-input", "First line");
        fx(() -> {
            ((TextInputControl) stage.getScene().lookup("#message-input")).end();
            return null;
        });
        press("message-input", KeyCode.ENTER, true, false);
        assertEquals("First line\n", fx(() ->
                ((TextInputControl) stage.getScene().lookup("#message-input")).getText()));
        assertTrue(runtime.getChats().getConversations().join().isEmpty());
    }

    @Test
    void offerTime_activeSale_addsOfferedTimeToMeetupBar() throws Exception {
        UUID sale = activeSale();
        login("seller");
        openFirstConversation();
        awaitText("meetup-bar-text", "No meetup times offered yet");
        snapshot("conversation-meetup-minimum", 960, 640);
        click("meetup-offer-time");
        dialogSet("meetup-date", node -> ((DatePicker) node).setValue(LocalDate.now().plusDays(2)));
        dialogClick("dialog-submit");
        awaitText("meetup-bar-text", "1 time offered");
        snapshot("conversation-meetup-default", 1100, 750);
        var slot = runtime.getMeetups().getMeetupSummary(sale).join().offeredSlots().getFirst();
        assertEquals("Campus", slot.time().location());
        assertEquals(LocalDate.now().plusDays(2).atTime(LocalTime.NOON),
                LocalDateTime.ofInstant(slot.time().startAt(), ZoneId.systemDefault()));
        assertEquals(MEETUP_LENGTH, slot.time().length());
    }

    @Test
    void chooseTime_offeredTime_booksMeetup() throws Exception {
        UUID sale = activeSale();
        offerSlotAsSeller(sale, 2);
        login("buyer");
        openFirstConversation();
        awaitText("meetup-bar-text", "1 time offered");
        click("meetup-choose-time");
        dialogClick("meetup-book");
        var meetup = awaitMeetup(sale);
        awaitText("meetup-bar-text", "Meetup: " + MeetupBar.format(meetup.getTime(), ZoneId.systemDefault()));
    }

    @Test
    void proposeMove_bookedMeetup_showsOwnProposal() throws Exception {
        UUID sale = bookedSale();
        login("buyer");
        openFirstConversation();
        click("meetup-propose-move");
        dialogSet("meetup-date", node -> ((DatePicker) node).setValue(LocalDate.now().plusDays(3)));
        dialogType("meetup-place", "Campus gate");
        dialogClick("dialog-submit");
        awaitText("meetup-withdraw-move", "Withdraw Proposal");
        var proposal = runtime.getMeetups().getMeetupSummary(sale).join().meetup().orElseThrow()
                .getPendingProposal().orElseThrow();
        assertEquals("Campus gate", proposal.getTime().location());
        assertEquals(LocalDate.now().plusDays(3),
                LocalDateTime.ofInstant(proposal.getTime().startAt(), ZoneId.systemDefault()).toLocalDate());
    }

    @Test
    void acceptMove_proposalFromOtherParticipant_movesMeetup() throws Exception {
        UUID sale = bookedSale();
        runtime.getAccounts().login("buyer", "Sample1!").join();
        var meetupId = runtime.getMeetups().getMeetupSummary(sale).join().meetup().orElseThrow().getId();
        Instant moved = LocalDate.now().plusDays(4).atTime(15, 0).atZone(ZoneId.systemDefault()).toInstant();
        runtime.getMeetups().proposeMove(meetupId, moved, moved.plus(MEETUP_LENGTH), "Campus gate").join();
        runtime.getAccounts().logout().join();
        login("seller");
        openFirstConversation();
        click("meetup-accept-move");
        awaitText("meetup-propose-move", "Propose Move");
        assertEquals(moved, runtime.getMeetups().getMeetupSummary(sale).join().meetup().orElseThrow()
                .getTime().startAt());
    }

    @Test
    void cancelMeetup_confirmed_letsSellerOfferNewTimes() throws Exception {
        UUID sale = bookedSale();
        login("seller");
        openFirstConversation();
        confirm("meetup-cancel", "Cancel Meetup");
        awaitText("meetup-bar-text", "No meetup times offered yet");
        assertTrue(runtime.getMeetups().getMeetupSummary(sale).join().meetup().isEmpty());
    }

    @Test
    void saleDetails_bookedMeetup_showsMeetupInsteadOfArrangeMeetup() throws Exception {
        UUID sale = bookedSale();
        login("seller");
        click("nav-sales");
        awaitReady();
        click("sale-detail");
        awaitReady();
        var time = runtime.getMeetups().getMeetupSummary(sale).join().meetup().orElseThrow().getTime();
        awaitText("sale-meetup", "Meetup: " + MeetupBar.format(time, ZoneId.systemDefault()));
        assertTrue(fx(() -> stage.getScene().lookup("#arrange-meetup") == null));
    }

    @Test
    void mySales_activeSaleWithOfferedTime_showsMeetupSummary() throws Exception {
        UUID sale = activeSale();
        offerSlotAsSeller(sale, 2);
        login("seller");
        click("nav-sales");
        awaitReady();
        awaitText("sale-meetup-summary", "1 time offered");
    }

    @Test
    void myListings_reservedListing_showsMeetupSummary() throws Exception {
        activeSale();
        login("seller");
        click("nav-listings");
        awaitReady();
        awaitText("listing-meetup-summary", "No meetup times yet");
    }

    @Test
    void dashboard_bookedMeetup_countsUpcomingMeetup() throws Exception {
        bookedSale();
        login("seller");
        click("nav-dashboard");
        awaitReady();
        awaitText("dashboard-upcoming-meetups", "1");
    }

    @Test
    void sidebar_loggedIn_hasNoSeparateMeetupPages() throws Exception {
        runtime.getAccounts().register("alice", "Sample1!", "Alice").join();
        login("alice");
        assertTrue(fx(() -> stage.getScene().lookup("#nav-meetups") == null));
        assertTrue(fx(() -> stage.getScene().lookup("#nav-availability") == null));
    }

    private UUID bookedSale() throws Exception {
        UUID sale = activeSale();
        UUID slot = offerSlotAsSeller(sale, 2);
        runtime.getAccounts().login("buyer", "Sample1!").join();
        runtime.getMeetups().bookSlot(slot).join();
        runtime.getAccounts().logout().join();
        return sale;
    }

    private UUID offerSlotAsSeller(UUID sale, int daysAhead) {
        Instant start = LocalDate.now().plusDays(daysAhead).atTime(14, 0).atZone(ZoneId.systemDefault()).toInstant();
        runtime.getAccounts().login("seller", "Sample1!").join();
        var summary = runtime.getMeetups().offerSlot(sale, start, start.plus(MEETUP_LENGTH), "Campus").join();
        runtime.getAccounts().logout().join();
        return summary.offeredSlots().getLast().id();
    }

    private Meetup awaitMeetup(UUID sale) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            var meetup = runtime.getMeetups().getMeetupSummary(sale).join().meetup();
            if (meetup.isPresent()) {
                return meetup.orElseThrow();
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Meetup was not booked");
    }

    private UUID activeSale() {
        seedListing();
        var offer = submitOfferAsBuyer();
        runtime.getAccounts().login("seller", "Sample1!").join();
        UUID sale = runtime.getOffers().acceptOffer(offer.getId()).join().transactionId();
        runtime.getAccounts().logout().join();
        return sale;
    }

    private void openFirstConversation() throws Exception {
        click("nav-conversations");
        awaitReady();
        click("conversation-open");
        awaitText("page-title", "Desk");
        awaitReady();
    }

    private void dialogSet(String id, Consumer<Node> change) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            boolean isDone = fx(() -> {
                for (Window window : List.copyOf(Window.getWindows())) {
                    if (window != stage && window.isShowing()) {
                        window.getScene().getRoot().applyCss();
                        var node = window.getScene().lookup("#" + id);
                        if (node != null) {
                            change.accept(node);
                            return true;
                        }
                    }
                }
                return false;
            });
            if (isDone) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Dialog control not shown: " + id);
    }

    private void openChatWithSellerAsBuyer() throws Exception {
        seedListing();
        login("buyer");
        click("search-submit");
        awaitText("results-count", "1 listing");
        click("listing-card");
        awaitReady();
        click("chat-seller");
        awaitText("page-title", "Desk");
        awaitReady();
    }

    private void press(String id, KeyCode key, boolean isShiftDown, boolean isShortcutDown) throws Exception {
        fx(() -> {
            var target = stage.getScene().lookup("#" + id);
            target.requestFocus();
            target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key, isShiftDown, isShortcutDown,
                    false, false));
            return null;
        });
    }

    private void seedListing() {
        runtime.getAccounts().register("seller", "Sample1!", "Seller").join();
        runtime.getAccounts().register("buyer", "Sample1!", "Buyer").join();
        runtime.getAccounts().login("seller", "Sample1!").join();
        runtime.getListings().createListing(new ListingDraft("Desk", "A desk", Category.FURNITURE,
                4500, Condition.GOOD, "Campus"), List.of()).join();
        runtime.getAccounts().logout().join();
    }

    @Test
    void editListing_changedDetails_confirmsAndRejectsPendingOffers() throws Exception {
        seedListing();
        runtime.getAccounts().login("buyer", "Sample1!").join();
        var listing = runtime.getListings().searchListings(hotshop.service.ListingSearch.all()).join().getFirst();
        runtime.getOffers().submitOffer(listing.listing().getId(), 4000).join();
        runtime.getAccounts().logout().join();
        login("seller");
        click("nav-listings");
        awaitReady();
        click("listing-card");
        awaitReady();
        assertTrue(fx(() -> stage.getScene().lookup("#delete-listing").isDisabled()));
        click("edit-listing");
        awaitReady();
        type("title", "Updated desk");
        confirm("save-listing", "Save Listing");
        awaitText("listing-title", "Updated desk");
        awaitReady();
        assertEquals(hotshop.model.OfferStatus.REJECTED,
                runtime.getOffers().getOffersForListing(listing.listing().getId()).join()
                        .getFirst().offer().getStatus());
    }

    @Test
    void changePassword_wrongCurrentPassword_showsErrorBesideCurrentPassword() throws Exception {
        runtime.getAccounts().register("alice", "Sample1!", "Alice").join();
        login("alice");
        click("nav-profile");
        awaitReady();
        click("change-password");
        dialogType("current-password", "Wrong1!");
        dialogType("password", "Different1!");
        dialogType("confirm-password", "Different1!");
        dialogClick("dialog-submit");
        awaitText("page-status", "Invalid username or password");
        fx(() -> {
            var scene = Window.getWindows().stream().filter(window -> window != stage && window.isShowing())
                    .findFirst().orElseThrow().getScene();
            assertEquals("Invalid username or password",
                    ((Label) scene.lookup("#current-password-error")).getText());
            assertEquals("", ((Label) scene.lookup("#password-error")).getText());
            return null;
        });
        assertTrue(runtime.getAccounts().getCurrentUserId().join().isPresent());
    }

    @Test
    void changePassword_validConfirmation_retainsSessionAndAcceptsNewPassword() throws Exception {
        runtime.getAccounts().register("alice", "Sample1!", "Alice").join();
        login("alice");
        click("nav-profile");
        awaitReady();
        click("change-password");
        dialogType("current-password", "Sample1!");
        dialogType("password", "Different1!");
        dialogType("confirm-password", "Different1!");
        dialogClick("dialog-submit");
        awaitText("page-status", "Password changed. You remain logged in.");
        assertTrue(runtime.getAccounts().getCurrentUserId().join().isPresent());
        click("nav-logout");
        awaitText("page-title", "Log in");
        type("username", "alice");
        type("password", "Different1!");
        click("login-submit");
        awaitText("page-title", "Search");
    }

    @Test
    void back_unsubmittedInvalidFilter_preservesDraftWithoutChangingResults() throws Exception {
        seedListing();
        login("buyer");
        type("search-query", "desk");
        click("search-submit");
        awaitText("results-count", "1 listing");
        type("search-query", "unfinished query");
        type("minimum-price", "not a price");
        click("listing-card");
        awaitText("listing-title", "Desk");
        awaitReady();
        click("back");
        awaitText("results-count", "1 listing");
        assertEquals("unfinished query", fx(() ->
                ((TextInputControl) stage.getScene().lookup("#search-query")).getText()));
        assertEquals("not a price", fx(() ->
                ((TextInputControl) stage.getScene().lookup("#minimum-price")).getText()));
    }

    @Test
    void cancelSale_afterOneConfirmation_requiresOtherParticipantAndReleasesListing() throws Exception {
        seedListing();
        runtime.getAccounts().login("buyer", "Sample1!").join();
        var listing = runtime.getListings().searchListings(hotshop.service.ListingSearch.all()).join().getFirst();
        var offer = runtime.getOffers().submitOffer(listing.listing().getId(), 4000).join().offer();
        runtime.getAccounts().logout().join();
        runtime.getAccounts().login("seller", "Sample1!").join();
        var accepted = runtime.getOffers().acceptOffer(offer.getId()).join();
        runtime.getTransactions().confirmCompletion(accepted.transactionId()).join();
        runtime.getAccounts().logout().join();
        login("seller");
        click("nav-sales");
        awaitReady();
        click("sale-detail");
        awaitReady();
        click("sale-request-cancellation");
        awaitText("sale-next-step", "Waiting for the other participant to respond to your request");
        assertTrue(fx(() -> stage.getScene().lookup("#sale-confirm-completion").isDisabled()));
        switchUser("buyer");
        click("nav-purchases");
        awaitReady();
        click("sale-detail");
        awaitReady();
        confirm("sale-accept-cancellation", "Accept Cancellation");
        awaitText("sale-status", "Cancelled");
        assertEquals(hotshop.model.ListingStatus.AVAILABLE,
                runtime.getListings().getListing(listing.listing().getId()).join().listing().getStatus());
    }

    @Test
    void register_mismatchedPassword_keepsInputAndShowsFieldError() throws Exception {
        click("register-link");
        type("username", "alice");
        type("display-name", "Alice");
        type("password", "Sample1!");
        type("confirm-password", "Different1!");
        click("register-submit");
        awaitText("confirm-password-error", "Passwords do not match.");
        assertEquals("alice", fx(() -> ((TextInputControl) stage.getScene().lookup("#username")).getText()));
        assertTrue(runtime.getAccounts().getCurrentUserId().join().isEmpty());
    }

    @Test
    void navigate_dirtyProfile_cancelKeepsInputAndDiscardLeavesWithoutSaving() throws Exception {
        runtime.getAccounts().register("alice", "Sample1!", "Alice").join();
        login("alice");
        click("nav-profile");
        awaitReady();
        snapshot("own-profile-minimum", 960, 640);
        type("display-name", "Unsaved");
        confirm("nav-search", "Cancel");
        awaitText("page-title", "My Profile");
        assertEquals("Unsaved", fx(() -> ((TextInputControl) stage.getScene().lookup("#display-name")).getText()));
        confirm("nav-search", "Discard Changes");
        awaitText("page-title", "Search");
        assertEquals("Alice", runtime.getAccounts().getOwnProfile().join().getDisplayName());
    }

    private void switchUser(String username) throws Exception {
        awaitReady();
        click("nav-logout");
        awaitText("page-title", "Log in");
        login(username);
    }

    private void dialogType(String id, String value) throws Exception {
        fx(() -> {
            var scene = Window.getWindows().stream().filter(window -> window != stage && window.isShowing())
                    .findFirst().orElseThrow().getScene();
            scene.getRoot().applyCss();
            ((TextInputControl) scene.lookup("#" + id)).setText(value);
            return null;
        });
    }

    private void dialogClick(String id) throws Exception {
        fx(() -> {
            var scene = Window.getWindows().stream().filter(window -> window != stage && window.isShowing())
                    .findFirst().orElseThrow().getScene();
            scene.getRoot().applyCss();
            ((Button) scene.lookup("#" + id)).fire();
            return null;
        });
    }

    private void confirm(String id, String action) throws Exception {
        Platform.runLater(() -> ((Button) stage.getScene().lookup("#" + id)).fire());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            boolean handled = fx(() -> {
                for (Window window : List.copyOf(Window.getWindows())) {
                    if (window != stage && window.isShowing()) {
                        window.getScene().getRoot().applyCss();
                        for (var node : window.getScene().getRoot().lookupAll(".button")) {
                            if (node instanceof Button button && button.getText().equals(action)) {
                                button.fire();
                                return true;
                            }
                        }
                    }
                }
                return false;
            });
            if (handled) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Confirmation not shown: " + action);
    }

    @Test
    void saveProfile_validText_persistsNameAndPrivatePickupPreference() throws Exception {
        runtime.getAccounts().register("alice", "Sample1!", "Alice").join();
        login("alice");
        click("nav-profile");
        awaitText("page-title", "My Profile");
        awaitReady();
        type("display-name", "Alice Tan");
        type("preferred-location", "Library lobby");
        click("save-profile");
        awaitText("page-status", "Profile saved.");
        assertEquals("Alice Tan", runtime.getAccounts().getOwnProfile().join().getDisplayName());
        assertEquals("Library lobby", runtime.getAccounts().getOwnProfile().join()
                .getPreferredPickupLocation().orElseThrow());
    }

    private void awaitReady() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (fx(() -> {
                stage.getScene().getRoot().applyCss();
                stage.getScene().getRoot().layout();
                var body = stage.getScene().lookup("#page-body");
                return body != null && !body.isDisabled();
            })) {
                return;
            }
            Thread.sleep(25);
        }
        assertTrue(fx(() -> !stage.getScene().lookup("#page-body").isDisabled()));
    }

    private void snapshot(String name, int width, int height) throws Exception {
        fx(() -> {
            stage.setWidth(width);
            stage.setHeight(height);
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            return null;
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (fx(() -> stage.getScene().getWidth() > width || stage.getScene().getHeight() > height)) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Window did not resize to the requested dimensions");
            }
            Thread.sleep(25);
        }
        fx(() -> {
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            var image = stage.getScene().snapshot(null);
            int imageWidth = (int) image.getWidth();
            int imageHeight = (int) image.getHeight();
            int[] pixels = new int[imageWidth * imageHeight];
            image.getPixelReader().getPixels(0, 0, imageWidth, imageHeight,
                    PixelFormat.getIntArgbInstance(), pixels, 0, imageWidth);
            BufferedImage output = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
            output.setRGB(0, 0, imageWidth, imageHeight, pixels, 0, imageWidth);
            Path folder = Path.of("build", "ui-checks");
            Files.createDirectories(folder);
            ImageIO.write(output, "png", folder.resolve(name + ".png").toFile());
            return null;
        });
    }

    private void type(String id, String value) throws Exception {
        fx(() -> {
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            ((TextInputControl) stage.getScene().lookup("#" + id)).setText(value);
            return null;
        });
    }

    private void click(String id) throws Exception {
        fx(() -> {
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            ((Button) stage.getScene().lookup("#" + id)).fire();
            return null;
        });
    }

    private void awaitText(String id, String expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (fx(() -> stage.getScene().lookup("#" + id) instanceof Labeled label
                    && label.getText().equals(expected))) {
                return;
            }
            Thread.sleep(25);
        }
        assertEquals(expected, fx(() -> ((Labeled) stage.getScene().lookup("#" + id)).getText()));
    }

    private static <T> T fx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }
}
