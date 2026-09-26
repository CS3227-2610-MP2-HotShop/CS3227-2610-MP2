package hotshop.ui;

import java.util.List;
import java.util.UUID;

import hotshop.service.ConversationSummary;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** The conversations list and the entry points that open one conversation. */
final class ChatPages {
    private final MarketplaceUi app;

    ChatPages(MarketplaceUi app) {
        this.app = app;
    }

    /** Every conversation of the current user, with offers and sales first, in the service's order. */
    void list() {
        UiPage page = app.page("Conversations");
        page.body.getChildren().add(UiControls.button("Refresh", "conversations-refresh",
                () -> app.replace(this::list)));
        page.load(app.runtime.getChats()::getConversations, conversations -> {
            if (conversations.isEmpty()) {
                page.body.getChildren().addAll(UiControls.label("No conversations yet.", "muted"),
                        UiControls.button("Search Listings", "empty-conversations", () -> app.navigate(app::search)));
                return;
            }
            List<ConversationSummary> active = conversations.stream()
                    .filter(ConversationSummary::isAboutOfferOrSale).toList();
            List<ConversationSummary> other = conversations.stream()
                    .filter(summary -> !summary.isAboutOfferOrSale()).toList();
            group(page, "Offers and sales", active);
            group(page, "Other conversations", other);
        });
    }

    /** The current buyer's chat about another seller's listing; empty until their first message. */
    void withSeller(UUID listingId) {
        new ConversationPage(app, listingId).loadWithSeller();
    }

    /** The seller's existing conversation with one buyer about one of the seller's listings. */
    void withBuyer(UUID listingId, UUID buyerId) {
        new ConversationPage(app, listingId).load(() -> app.runtime.getChats().openChatWithBuyer(listingId, buyerId));
    }

    private void open(ConversationSummary summary) {
        new ConversationPage(app, summary.listing().getId())
                .load(() -> app.runtime.getChats().openConversation(summary.conversation().getId()));
    }

    private void group(UiPage page, String title, List<ConversationSummary> conversations) {
        if (conversations.isEmpty()) {
            return;
        }
        page.body.getChildren().add(UiControls.label(title, "section-title"));
        conversations.forEach(summary -> page.body.getChildren().add(card(summary)));
    }

    private VBox card(ConversationSummary summary) {
        Label role = UiControls.label(UiControls.role(summary.role()), "badge");
        VBox card = new VBox(8, UiControls.button(summary.listing().getDetails().title(), "conversation-open",
                () -> app.navigate(() -> open(summary))),
                UiControls.actions(app.profileLink(summary.otherParticipant(), "conversation-other"), role),
                UiControls.label(summary.preview(), "muted"));
        if (summary.unreadCount() > 0) {
            Label unread = UiControls.label(summary.unreadCount() + " unread", "unread");
            unread.setId("conversation-unread");
            card.getChildren().add(unread);
        }
        card.getChildren().add(UiControls.label(UiControls.time(summary.lastActivityAt()), "hint"));
        card.getStyleClass().add("card");
        return card;
    }
}
