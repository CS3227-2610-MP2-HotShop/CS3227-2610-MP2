package hotshop.ui;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.List;
import java.util.stream.Stream;
import java.util.UUID;

import hotshop.model.Meetup;
import hotshop.model.MeetupSlot;
import hotshop.model.MeetupTime;
import hotshop.model.TransactionStatus;
import hotshop.service.ConversationSummary;
import hotshop.service.MeetupService;
import hotshop.service.MeetupSummary;
import hotshop.service.SaleRole;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** The conversation's meetup bar during a sale, and the dialogs for offering, choosing, and moving times. */
final class MeetupPages {
    private static final int START_STEP_MINUTES = 15;
    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final int MINUTES_PER_HOUR = 60;
    private static final LocalTime DEFAULT_START = LocalTime.NOON;
    private static final Duration DEFAULT_LENGTH = Duration.ofMinutes(30);
    private static final List<Duration> LENGTHS = Stream.of(15, 30, 45, 60, 90, 120, 180, 240)
            .map(Duration::ofMinutes).toList();
    private final MarketplaceUi app;

    MeetupPages(MarketplaceUi app) {
        this.app = app;
    }

    /** The bar for an active or completed sale; {@code reload} redisplays the conversation after a change. */
    HBox bar(UiPage page, ConversationSummary conversation, MeetupSummary meetup, TransactionStatus saleStatus,
            Runnable reload) {
        MeetupBar bar = MeetupBar.of(conversation.role(), app.userId(), conversation.otherParticipant().displayName(),
                meetup, saleStatus, Instant.now(), ZoneId.systemDefault());
        return UiControls.bar(bar.text(), "meetup-bar-text", bar.actions().stream()
                .map(action -> action(action, page, conversation, meetup, reload)).toArray(Button[]::new));
    }

    private Button action(MeetupBar.Action action, UiPage page, ConversationSummary conversation,
            MeetupSummary meetup, Runnable reload) {
        MeetupService meetups = app.runtime.getMeetups();
        String pickup = conversation.listing().getDetails().pickupLocation();
        return switch (action) {
            case OFFER_TIME -> UiControls.primary("Offer Time", "meetup-offer-time", () -> timeDialog(page,
                    "Offer Time", defaultTime(pickup), time -> meetups.offerSlot(meetup.saleId(), time.startAt(),
                            time.endAt(), time.location()), reload));
            case VIEW_TIMES -> UiControls.button("View Times", "meetup-view-times", () -> timesDialog(page,
                    "Offered Times", meetup.offeredSlots(), "Withdraw", "meetup-withdraw-time",
                    slot -> meetups.withdrawSlot(slot.id()), reload));
            case CHOOSE_TIME -> UiControls.primary("Choose Time", "meetup-choose-time", () -> timesDialog(page,
                    "Choose a Meetup Time", meetup.offeredSlots(), "Book", "meetup-book",
                    slot -> meetups.bookSlot(slot.id()), reload));
            case PROPOSE_MOVE -> UiControls.button("Propose Move", "meetup-propose-move", () -> timeDialog(page,
                    "Propose Move", booked(meetup).getTime(), time -> meetups.proposeMove(booked(meetup).getId(),
                            time.startAt(), time.endAt(), time.location()), reload));
            case CANCEL_MEETUP -> UiControls.button("Cancel Meetup", "meetup-cancel", () -> {
                if (app.confirm("Cancel Meetup", "The sale stays active and the seller can offer new times.")) {
                    change(page, meetup, meetups::cancelMeetup, reload);
                }
            });
            case ACCEPT_MOVE -> UiControls.primary("Accept Move", "meetup-accept-move", () ->
                    change(page, meetup, meetups::acceptMove, reload));
            case REJECT_MOVE -> UiControls.button("Reject Move", "meetup-reject-move", () ->
                    change(page, meetup, meetups::rejectMove, reload));
            case WITHDRAW_MOVE -> UiControls.button("Withdraw Proposal", "meetup-withdraw-move", () ->
                    change(page, meetup, meetups::withdrawMove, reload));
            case VIEW_SALE -> UiControls.button("View Sale", "meetup-view-sale", () -> app.navigate(() ->
                    app.sales.details(meetup.saleId(), conversation.role() == SaleRole.SELLER)));
        };
    }

    /** Date, start, length, and place; the service still refuses past, too-distant, or overlapping times. */
    private void timeDialog(UiPage page, String title, MeetupTime initial, Function<MeetupTime,
            CompletableFuture<MeetupSummary>> save, Runnable reload) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime start = LocalDateTime.ofInstant(initial.startAt(), zone);
        UiForm form = new UiForm();
        DatePicker date = new DatePicker(start.toLocalDate());
        date.setId("meetup-date");
        date.setEditable(false);
        LocalDate today = LocalDate.now(zone);
        date.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate day, boolean isEmpty) {
                super.updateItem(day, isEmpty);
                setDisable(isEmpty || day.isBefore(today)
                        || day.isAfter(today.plusDays(MeetupService.MAX_DAYS_AHEAD.toDays())));
            }
        });
        ComboBox<LocalTime> startTime = new ComboBox<>();
        startTime.setId("meetup-start");
        for (int minutes = 0; minutes < MINUTES_PER_DAY; minutes += START_STEP_MINUTES) {
            startTime.getItems().add(LocalTime.MIDNIGHT.plusMinutes(minutes));
        }
        startTime.setConverter(converter(time -> String.format("%02d:%02d", time.getHour(), time.getMinute())));
        startTime.setValue(start.toLocalTime());
        ComboBox<Duration> length = new ComboBox<>();
        length.setId("meetup-length");
        length.getItems().setAll(LENGTHS);
        length.setConverter(converter(MeetupPages::describe));
        length.setValue(initial.length());
        form.field("meetup-date", "Date", date);
        form.field("meetup-start", "Start time", startTime);
        form.field("meetup-length", "Length", length);
        form.text("meetup-place", "Place", initial.location());
        MeetupTime[] chosen = new MeetupTime[1];
        UiDialogs.form(app, page, title, title, form, () -> {
            form.clearErrors();
            if (date.getValue() == null) {
                form.reject("meetup-date", "Choose a date.");
            }
            form.textLength("meetup-place", MeetupTime.MAX_LOCATION_LENGTH, true);
            if (form.isValid()) {
                Instant startsAt = LocalDateTime.of(date.getValue(), startTime.getValue()).atZone(zone).toInstant();
                chosen[0] = new MeetupTime(startsAt, startsAt.plus(length.getValue()), form.value("meetup-place"));
            }
            return form.isValid();
        }, () -> save.apply(chosen[0]), ignored -> reload.run());
    }

    /** The offered times, each with one action (Book for the buyer, Withdraw for the seller). */
    private void timesDialog(UiPage page, String title, List<MeetupSlot> slots, String actionText, String actionId,
            Function<MeetupSlot, CompletableFuture<MeetupSummary>> act, Runnable reload) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(app.stage);
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        VBox rows = new VBox(10);
        rows.getStyleClass().add("form-panel");
        for (MeetupSlot slot : slots) {
            Label when = UiControls.label(MeetupBar.format(slot.time(), ZoneId.systemDefault()), "muted");
            HBox.setHgrow(when, Priority.ALWAYS);
            when.setMaxWidth(Double.MAX_VALUE);
            Button button = UiControls.button(actionText, actionId, () -> {
                dialog.close();
                page.perform(() -> act.apply(slot), ignored -> reload.run());
            });
            HBox row = new HBox(10, when, button);
            row.setAlignment(Pos.CENTER_LEFT);
            rows.getChildren().add(row);
        }
        dialog.getDialogPane().setContent(rows);
        dialog.show();
    }

    /** Applies one change to the booked meetup, then redisplays the conversation. */
    private static void change(UiPage page, MeetupSummary meetup,
            Function<UUID, CompletableFuture<MeetupSummary>> operation, Runnable reload) {
        page.perform(() -> operation.apply(booked(meetup).getId()), ignored -> reload.run());
    }

    private static MeetupTime defaultTime(String pickup) {
        ZoneId zone = ZoneId.systemDefault();
        Instant start = LocalDate.now(zone).plusDays(1).atTime(DEFAULT_START).atZone(zone).toInstant();
        return new MeetupTime(start, start.plus(DEFAULT_LENGTH), pickup);
    }

    private static Meetup booked(MeetupSummary meetup) {
        return meetup.meetup().orElseThrow();
    }

    private static String describe(Duration length) {
        long minutes = length.toMinutes();
        if (minutes < MINUTES_PER_HOUR) {
            return minutes + " min";
        }
        return minutes % MINUTES_PER_HOUR == 0 ? length.toHours() + " h"
                : minutes / (double) MINUTES_PER_HOUR + " h";
    }

    private static <T> StringConverter<T> converter(Function<T, String> text) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : text.apply(value);
            }

            @Override
            public T fromString(String value) {
                throw new UnsupportedOperationException("Choose from the list");
            }
        };
    }
}
