package hotshop.ui;

import java.text.NumberFormat;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import hotshop.model.Listing;
import hotshop.model.ListingStatus;
import hotshop.model.Message;
import hotshop.model.Offer;
import hotshop.model.OfferStatus;
import hotshop.model.TransactionStatus;
import hotshop.service.ChatService;
import hotshop.service.ConversationSummary;
import hotshop.service.ConversationView;
import hotshop.service.ListingWithSeller;
import hotshop.service.PublicProfile;
import hotshop.service.SaleForParticipant;
import hotshop.service.SaleRole;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * One conversation between a buyer and the seller about one listing: a header, the offer bar,
 * the messages in their own scrolling area, and the send box. The typed draft survives reloads.
 */
final class ConversationPage {
    private static final int MESSAGE_AREA_MINIMUM_HEIGHT = 200;
    private static final int MESSAGE_MAXIMUM_WIDTH = 480;
    private static final int SEND_BOX_ROWS = 3;
    private static final NumberFormat COUNT_FORMAT = NumberFormat.getIntegerInstance(Locale.ENGLISH);
    private final MarketplaceUi app;
    private final UiPage page;
    private final UUID listingId;
    private final TextArea input = new TextArea();
    private final Label counter = UiControls.label("", "hint");
    private final Label hint = UiControls.label("", "hint");
    private final Label sendError = UiControls.label("", "error");
    private final Button send;
    private final VBox messages = new VBox(8);
    private final ScrollPane messageScroll = new ScrollPane(messages);
    private UUID conversationId;
    private boolean canSend;

    ConversationPage(MarketplaceUi app, UUID listingId) {
        this.app = app;
        this.listingId = listingId;
        page = app.fixedPage("Conversation");
        send = UiControls.primary("Send", "send-message", this::send);
        input.setId("message-input");
        input.setWrapText(true);
        input.setPrefRowCount(SEND_BOX_ROWS);
        input.textProperty().addListener((property, previous, text) -> updateSendBox());
        input.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() != KeyCode.ENTER) {
                return;
            }
            event.consume();
            if (event.isShiftDown()) {
                input.replaceSelection("\n");
            } else {
                send();
            }
        });
        counter.setId("message-count");
        hint.setId("send-hint");
        sendError.setId("send-error");
        messages.setId("messages");
        messageScroll.setId("message-scroll");
        messageScroll.setFitToWidth(true);
        messageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        messageScroll.setMinHeight(MESSAGE_AREA_MINIMUM_HEIGHT);
        messages.heightProperty().addListener((property, previous, height) ->
                messageScroll.setVvalue(messageScroll.getVmax()));
        VBox.setVgrow(messageScroll, Priority.ALWAYS);
        page.body.setSpacing(10);
        page.setDirty(() -> !input.getText().isBlank());
    }

    /** The buyer's conversation about a listing, or an empty chat that their first message starts. */
    void loadWithSeller() {
        page.load(() -> chats().openChatWithSeller(listingId), view -> view.ifPresentOrElse(this::show,
                () -> page.load(() -> app.runtime.getListings().getListing(listingId), this::showUnstarted)));
    }

    void load(Supplier<CompletableFuture<ConversationView>> opening) {
        page.load(opening, this::show);
    }

    private void reload() {
        if (conversationId == null) {
            loadWithSeller();
        } else {
            load(() -> chats().openConversation(conversationId));
        }
    }

    private void show(ConversationView view) {
        ConversationSummary summary = view.summary();
        conversationId = summary.conversation().getId();
        Optional<Offer> latest = summary.latestOffer();
        if (summary.activeSaleId().isPresent()) {
            render(summary, view.messages(), Optional.of(TransactionStatus.ACTIVE));
        } else if (latest.isPresent() && latest.orElseThrow().getStatus() == OfferStatus.ACCEPTED) {
            UUID offerId = latest.orElseThrow().getId();
            page.load(() -> sales(summary.role()), sales -> render(summary, view.messages(), sales.stream()
                    .filter(sale -> sale.sale().getAcceptedOfferId().equals(offerId))
                    .map(sale -> sale.sale().getStatus()).findFirst()));
        } else {
            render(summary, view.messages(), Optional.empty());
        }
    }

    private void render(ConversationSummary summary, List<Message> history, Optional<TransactionStatus> saleStatus) {
        Listing listing = summary.listing();
        header(listing, summary.otherParticipant(), summary.role());
        showBody(offerBar(listing, summary.role(), summary.latestOffer(), saleStatus), history,
                summary.otherParticipant(), summary.canSend(),
                summary.canSend() ? "" : closedReason(listing.getStatus()));
    }

    /**
     * No conversation yet: the buyer sees the listing, an empty history, and a send box that starts
     * it. ChatService only returns no conversation for listings that can still be asked about.
     */
    private void showUnstarted(ListingWithSeller value) {
        Listing listing = value.listing();
        header(listing, value.seller(), SaleRole.BUYER);
        showBody(offerBar(listing, SaleRole.BUYER, Optional.empty(), Optional.empty()), List.of(), value.seller(),
                true, "Send a message to start a conversation with " + value.seller().displayName() + ".");
    }

    private void showBody(HBox offerBar, List<Message> history, PublicProfile other, boolean isOpen,
            String hintText) {
        canSend = isOpen;
        page.body.getChildren().setAll(offerBar, messageScroll, sendBox());
        showMessages(history, other);
        hint.setText(hintText);
        updateSendBox();
    }

    private void header(Listing listing, PublicProfile other, SaleRole role) {
        page.setTitle(listing.getDetails().title());
        Label roleBadge = UiControls.label(UiControls.role(role), "badge");
        roleBadge.setId("conversation-role");
        Label status = UiControls.label(UiControls.title(listing.getStatus()), "badge");
        status.setId("conversation-listing-status");
        page.setHeadingExtras(app.profileLink(other, "conversation-other"), roleBadge, status,
                UiControls.button("View Listing", "conversation-view-listing",
                        () -> app.navigate(() -> app.listings.details(listingId))));
    }

    private HBox offerBar(Listing listing, SaleRole role, Optional<Offer> latest,
            Optional<TransactionStatus> saleStatus) {
        OfferBar bar = OfferBar.of(role, latest, listing.getStatus(), saleStatus);
        Label text = UiControls.label(bar.text(), "section-title");
        text.setId("offer-bar-text");
        HBox row = new HBox(10, text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("offer-bar");
        HBox.setHgrow(text, Priority.ALWAYS);
        text.setMaxWidth(Double.MAX_VALUE);
        for (OfferBar.Action action : bar.actions()) {
            row.getChildren().add(offerAction(action, listing, role, latest));
        }
        return row;
    }

    private Button offerAction(OfferBar.Action action, Listing listing, SaleRole role, Optional<Offer> latest) {
        var offers = app.runtime.getOffers();
        return switch (action) {
            case ACCEPT -> UiControls.primary("Accept Offer", "offer-bar-accept", () ->
                    app.offers.accept(page, latest.orElseThrow(), accepted ->
                            app.navigate(() -> app.sales.details(accepted.transactionId(), true))));
            case REJECT -> UiControls.button("Reject Offer", "offer-bar-reject", () ->
                    page.perform(() -> offers.rejectOffer(latest.orElseThrow().getId()), ignored -> reload()));
            case WITHDRAW -> UiControls.button("Withdraw Offer", "offer-bar-withdraw", () ->
                    page.perform(() -> offers.withdrawOffer(latest.orElseThrow().getId()), ignored -> reload()));
            case MAKE_OFFER -> UiControls.primary("Make Offer", "offer-bar-make-offer", () ->
                    app.offers.makeOffer(page, listing, this::reload));
            case VIEW_SALE -> UiControls.button("View Sale", "offer-bar-view-sale", () ->
                    app.navigate(() -> app.sales.forOffer(latest.orElseThrow().getId(), role == SaleRole.SELLER)));
        };
    }

    private void showMessages(List<Message> history, PublicProfile other) {
        messages.getChildren().clear();
        if (history.isEmpty()) {
            messages.getChildren().add(UiControls.label("No messages yet.", "muted"));
        }
        Label newest = null;
        for (Message message : history) {
            boolean isOwn = message.senderId().equals(app.userId());
            Label text = UiControls.label(message.text(), "message-text");
            VBox bubble = new VBox(4, UiControls.label((isOwn ? "You" : other.displayName()) + " · "
                    + UiControls.time(message.sentAt()), "hint"), text);
            bubble.getStyleClass().add(isOwn ? "message-own" : "message-other");
            bubble.setMaxWidth(MESSAGE_MAXIMUM_WIDTH);
            HBox line = new HBox(bubble);
            line.setAlignment(isOwn ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            messages.getChildren().add(line);
            newest = text;
        }
        if (newest != null) {
            newest.setId("latest-message");
        }
        messageScroll.setVvalue(messageScroll.getVmax());
    }

    private VBox sendBox() {
        HBox.setHgrow(input, Priority.ALWAYS);
        VBox controls = new VBox(6, send, counter);
        HBox row = new HBox(10, input, controls);
        return new VBox(4, row, sendError, hint);
    }

    private void updateSendBox() {
        String text = input.getText();
        counter.setText(text.codePointCount(0, text.length()) + " / " + COUNT_FORMAT.format(Message.MAX_LENGTH));
        input.setDisable(!canSend);
        send.setDisable(!canSend || text.isBlank());
    }

    private void send() {
        String text = input.getText();
        if (!canSend || text.isBlank()) {
            return;
        }
        sendError.setText("");
        Supplier<CompletableFuture<ConversationView>> sending = conversationId == null
                ? () -> chats().messageSeller(listingId, text)
                : () -> chats().sendMessage(conversationId, text);
        page.perform(sending, view -> {
            input.clear();
            show(view);
        }, failure -> sendError.setText(UiPage.describe(failure)));
    }

    private static String closedReason(ListingStatus status) {
        return "This listing is " + UiControls.title(status).toLowerCase(Locale.ROOT)
                + ", so no new messages can be sent.";
    }

    private CompletableFuture<List<SaleForParticipant>> sales(SaleRole role) {
        var transactions = app.runtime.getTransactions();
        return role == SaleRole.SELLER ? transactions.getMySales() : transactions.getMyPurchases();
    }

    private ChatService chats() {
        return app.runtime.getChats();
    }
}
