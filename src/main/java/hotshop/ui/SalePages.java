package hotshop.ui;

import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.UUID;

import hotshop.model.TransactionStatus;
import hotshop.service.SaleAction;
import hotshop.service.SaleForParticipant;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Purchase/sale history, seller summary, and participant-specific sale actions. */
final class SalePages {
    private final MarketplaceUi app;

    SalePages(MarketplaceUi app) {
        this.app = app;
    }

    void list(boolean isSeller) {
        UiPage page = app.page(isSeller ? "My Sales" : "My Purchases");
        page.load(() -> findSales(isSeller), sales -> {
            if (sales.isEmpty()) {
                page.body.getChildren().addAll(UiControls.label(isSeller
                        ? "You have no sales yet." : "You have no purchases yet.", "muted"),
                        UiControls.button(isSeller ? "My Listings" : "Search Listings", "empty-sales", () ->
                                app.navigate(isSeller ? app.listings::mine : app::search)));
            }
            for (SaleForParticipant value : sales) {
                var sale = value.sale();
                VBox row = new VBox(10,
                        UiControls.button(sale.getListingTitle(), "sale-detail", () ->
                                app.navigate(() -> details(sale.getId(), isSeller))),
                        UiControls.label(UiControls.money(sale.getAgreedPriceCents()), "price"),
                        app.profileLink(value.otherParticipant(), "sale-counterpart"),
                        UiControls.label(UiControls.title(sale.getStatus()), "badge"),
                        UiControls.label(value.nextStep().getDescription(), "muted"),
                        UiControls.label("Agreed " + UiControls.time(sale.getCreatedAt()), "hint"));
                row.getStyleClass().add("card");
                page.body.getChildren().add(row);
            }
        });
    }

    void dashboard() {
        UiPage page = app.page("Seller Dashboard");
        page.load(app.runtime.getTransactions()::getSalesDashboard, summary -> {
            page.body.getChildren().addAll(UiControls.actions(
                    summary("Pending offers", Integer.toString(summary.pendingOffers())),
                    summary("Active sales", Integer.toString(summary.activeSales())),
                    summary("Completed sales", Integer.toString(summary.completedSales())),
                    summary("Completed sales value", UiControls.money(summary.totalSalesValueCents()))),
                    UiControls.actions(
                            UiControls.button("My Listings", "dashboard-listings",
                                    () -> app.navigate(app.listings::mine)),
                            UiControls.button("My Sales", "dashboard-sales", () -> app.navigate(() -> list(true)))),
                    UiControls.future("Upcoming meetups", "dashboard-meetups"));
        });
    }

    private VBox summary(String title, String value) {
        VBox card = new VBox(10, UiControls.label(title, "muted"), UiControls.label(value, "price"));
        card.getStyleClass().add("card");
        card.setPrefWidth(235);
        return card;
    }

    void forOffer(UUID offerId, boolean isSeller) {
        UiPage page = app.page("Sale Details");
        page.load(() -> findSales(isSeller), values -> values.stream()
                .filter(value -> value.sale().getAcceptedOfferId().equals(offerId)).findFirst()
                .ifPresentOrElse(value -> render(page, value, isSeller),
                        () -> page.error(new IllegalArgumentException("This sale is unavailable."))));
    }

    void details(UUID id, boolean isSeller) {
        UiPage page = app.page("Sale Details");
        page.load(() -> findSales(isSeller), values -> values.stream()
                .filter(value -> value.sale().getId().equals(id)).findFirst()
                .ifPresentOrElse(value -> render(page, value, isSeller),
                        () -> page.error(new IllegalArgumentException("This sale is unavailable."))));
    }

    private CompletableFuture<List<SaleForParticipant>> findSales(boolean isSeller) {
        return isSeller ? app.runtime.getTransactions().getMySales() : app.runtime.getTransactions().getMyPurchases();
    }

    private void render(UiPage page, SaleForParticipant value, boolean isSeller) {
        var sale = value.sale();
        Label status = UiControls.label(UiControls.title(sale.getStatus()), "badge");
        status.setId("sale-status");
        Label next = UiControls.label(value.nextStep().getDescription(), "section-title");
        next.setId("sale-next-step");
        page.body.getChildren().addAll(UiControls.label(sale.getListingTitle(), "section-title"), status,
                UiControls.label("Agreed price: " + UiControls.money(sale.getAgreedPriceCents()), "price"),
                UiControls.label(sale.getListingDescription(), "description"),
                UiControls.label("Agreed condition: " + UiControls.title(sale.getListingCondition()), "muted"),
                app.profileLink(value.otherParticipant(), "sale-counterpart"),
                UiControls.label("Buyer confirmed: " + sale.getBuyerConfirmedAt().map(UiControls::time)
                        .orElse("Not yet"), "muted"),
                UiControls.label("Seller confirmed: " + sale.getSellerConfirmedAt().map(UiControls::time)
                        .orElse("Not yet"), "muted"), next,
                UiControls.actions(UiControls.button("View Listing", "sale-listing", () ->
                        app.navigate(() -> app.listings.details(sale.getListingId()))),
                        UiControls.button("Open Chat", "sale-open-chat", () -> app.navigate(isSeller
                                ? () -> app.chats.withBuyer(sale.getListingId(), sale.getBuyerId())
                                : () -> app.chats.withSeller(sale.getListingId())))));
        sale.getCancelledAt().ifPresent(time -> page.body.getChildren().add(UiControls.label(
                "Cancelled " + UiControls.time(time) + " by "
                        + (sale.getCancelledBy().orElseThrow().equals(app.userId()) ? "you"
                                : value.otherParticipant().displayName()), "muted")));
        addAction(page, value, SaleAction.CONFIRM_COMPLETION, isSeller);
        if (sale.getPendingCancellation().isPresent()) {
            if (sale.getPendingCancellation().orElseThrow().getRequesterId().equals(app.userId())) {
                addAction(page, value, SaleAction.WITHDRAW_CANCELLATION, isSeller);
            } else {
                addAction(page, value, SaleAction.ACCEPT_CANCELLATION, isSeller);
                addAction(page, value, SaleAction.REJECT_CANCELLATION, isSeller);
            }
        } else {
            addAction(page, value, sale.hasConfirmation() ? SaleAction.REQUEST_CANCELLATION : SaleAction.CANCEL_SALE,
                    isSeller);
        }
        if (sale.getStatus() == TransactionStatus.ACTIVE) {
            page.body.getChildren().add(UiControls.future("Arrange Meetup", "arrange-meetup"));
        } else {
            page.body.getChildren().add(UiControls.label(
                    "This sale is closed; no further changes are available.", "hint"));
        }
        page.body.getChildren().add(UiControls.label("Cancellation history", "section-title"));
        if (sale.getCancellationRequests().isEmpty()) {
            page.body.getChildren().add(UiControls.label("No cancellation requests.", "muted"));
        }
        sale.getCancellationRequests().forEach(request -> page.body.getChildren().add(UiControls.label(
                (request.getRequesterId().equals(app.userId()) ? "You" : value.otherParticipant().displayName())
                        + " requested cancellation " + UiControls.time(request.getCreatedAt()) + " · "
                        + UiControls.title(request.getStatus()) + request.getResolvedAt()
                                .map(time -> " · Resolved " + UiControls.time(time)).orElse(""), "muted")));
    }

    private void addAction(UiPage page, SaleForParticipant value, SaleAction action, boolean isSeller) {
        String title = switch (action) {
            case CONFIRM_COMPLETION -> "Confirm Completion";
            case CANCEL_SALE -> "Cancel Sale";
            case REQUEST_CANCELLATION -> "Request Cancellation";
            case ACCEPT_CANCELLATION -> "Accept Cancellation";
            case REJECT_CANCELLATION -> "Reject Cancellation";
            case WITHDRAW_CANCELLATION -> "Withdraw Cancellation";
        };
        var button = UiControls.button(title, "sale-" + action.name().toLowerCase(java.util.Locale.ROOT)
                .replace('_', '-'), () -> {
                    String consequence = switch (action) {
                        case CONFIRM_COMPLETION -> "This records your completion confirmation. "
                                + "If the other participant "
                                + "has confirmed, the sale completes and the listing is marked sold.";
                        case CANCEL_SALE, ACCEPT_CANCELLATION -> "This cancels the sale "
                                + "and makes the listing available "
                                + "again. Previous offers remain closed.";
                        default -> null;
                    };
                    if (consequence == null || app.confirm(title, consequence)) {
                        page.perform(() -> execute(action, value.sale().getId()),
                                updated -> details(updated.sale().getId(), isSeller));
                    }
                });
        button.setDisable(!value.availableActions().contains(action));
        page.body.getChildren().add(button);
    }

    private CompletableFuture<SaleForParticipant> execute(SaleAction action, UUID id) {
        var service = app.runtime.getTransactions();
        return switch (action) {
            case CONFIRM_COMPLETION -> service.confirmCompletion(id);
            case CANCEL_SALE -> service.cancelSale(id);
            case REQUEST_CANCELLATION -> service.requestCancellation(id);
            case ACCEPT_CANCELLATION -> service.acceptCancellation(id);
            case REJECT_CANCELLATION -> service.rejectCancellation(id);
            case WITHDRAW_CANCELLATION -> service.withdrawCancellation(id);
        };
    }
}
