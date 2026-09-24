package hotshop.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import hotshop.model.Category;
import hotshop.model.Condition;
import hotshop.model.ListingDetails;
import hotshop.service.ListingSearch;
import hotshop.service.ListingSort;
import hotshop.service.ListingWithSeller;
import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Explicit search submission with independent draft controls and last-submitted results. */
final class SearchPage {
    private final MarketplaceUi app;
    private final UiPage page;
    private final UiForm form = new UiForm();
    private final TextField query;
    private final ComboBox<Category> category = choices(Category.values());
    private final ComboBox<ListingSort> sort = choices(ListingSort.values());
    private final List<CheckBox> conditions = new ArrayList<>();
    private final VBox results = new VBox(16);
    private List<ListingWithSeller> loaded = List.of();
    private boolean hasLoaded;

    SearchPage(MarketplaceUi app) {
        this.app = app;
        page = app.page("Search");
        ListingSearch draft = app.searchState.getDraft();
        var text = app.searchState.getDraftText();
        query = form.text("search-query", "Search listing titles", text.query());
        category.setId("search-category");
        category.setPromptText("All categories");
        category.setValue(draft.category());
        form.field("Category", category);
        FlowPane conditionControls = new FlowPane(12, 8);
        for (Condition value : Condition.values()) {
            CheckBox box = new CheckBox(UiControls.title(value));
            box.setUserData(value);
            box.setSelected(draft.conditions() != null && draft.conditions().contains(value));
            conditions.add(box);
            conditionControls.getChildren().add(box);
        }
        form.field("Conditions (none selected means all)", conditionControls);
        form.text("minimum-price", "Minimum price (SGD)", text.minimumPrice());
        form.text("maximum-price", "Maximum price (SGD)", text.maximumPrice());
        VBox filterFields = new VBox(12);
        var filterNodes = new ArrayList<>(form.getChildren().subList(1, form.getChildren().size()));
        form.getChildren().removeAll(filterNodes);
        filterFields.getChildren().addAll(filterNodes);
        TitledPane filters = new TitledPane("Filters", filterFields);
        filters.setId("search-filters");
        filters.setAnimated(false);
        filters.setExpanded(false);
        form.getChildren().add(filters);
        sort.setId("search-sort");
        sort.setValue(draft.sort() == null ? ListingSort.NEWEST : draft.sort());
        form.field("Sort", sort);
        sort.setOnAction(event -> {
            app.searchState.sort(sort.getValue());
            renderResults();
        });
        var submit = UiControls.primary("Search", "search-submit", this::submit);
        query.setOnAction(event -> submit.fire());
        form.getChildren().add(UiControls.actions(submit,
                UiControls.button("Clear Filters", "search-clear", this::clearFilters),
                UiControls.button("Refresh", "search-refresh", this::refresh)));
        page.body.getChildren().addAll(form, results);
        page.setLeaving(() -> {
            saveDraft();
            app.searchState.setScrollPosition(app.scroll().getVvalue());
        });
        if (app.searchState.getSubmitted().isPresent()) {
            refresh();
        } else {
            Label guidance = UiControls.label("Search for an item, or press Search to browse all listings.", "muted");
            guidance.setId("search-guidance");
            results.getChildren().add(guidance);
        }
    }

    static <T extends Enum<T>> ComboBox<T> choices(T[] values) {
        ComboBox<T> choice = new ComboBox<>();
        choice.getItems().addAll(values);
        choice.setMaxWidth(Double.MAX_VALUE);
        choice.setConverter(new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : UiControls.title(value);
            }

            @Override
            public T fromString(String text) {
                throw new UnsupportedOperationException("Choose an existing option");
            }
        });
        return choice;
    }

    private boolean saveDraft() {
        form.clearErrors();
        Long minimum = form.cents("minimum-price", true);
        Long maximum = form.cents("maximum-price", true);
        if (minimum != null && minimum > ListingDetails.MAX_PRICE_CENTS) {
            form.reject("minimum-price", "Maximum permitted amount is S$1,000,000.00.");
        }
        if (maximum != null && maximum > ListingDetails.MAX_PRICE_CENTS) {
            form.reject("maximum-price", "Maximum permitted amount is S$1,000,000.00.");
        }
        if (minimum != null && maximum != null && minimum > maximum) {
            form.reject("maximum-price", "Maximum must be at least the minimum.");
        }
        Set<Condition> selected = conditions.stream().filter(CheckBox::isSelected)
                .map(box -> (Condition) box.getUserData()).collect(Collectors.toSet());
        app.searchState.setDraft(new ListingSearch(query.getText(), category.getValue(), selected,
                minimum, maximum, sort.getValue()));
        app.searchState.setDraftText(new SearchState.DraftText(query.getText(),
                form.value("minimum-price"), form.value("maximum-price")));
        return form.isValid();
    }

    private void submit() {
        if (saveDraft()) {
            app.searchState.submit();
            app.searchState.setScrollPosition(0);
            refresh();
        } else {
            ((TitledPane) form.lookup("#search-filters")).setExpanded(true);
        }
    }

    private void clearFilters() {
        category.setValue(null);
        conditions.forEach(box -> box.setSelected(false));
        form.clear("minimum-price");
        form.clear("maximum-price");
        form.clearErrors();
    }

    private void refresh() {
        app.searchState.getSubmitted().ifPresent(criteria -> {
            results.getChildren().clear();
            hasLoaded = false;
            page.load(() -> app.runtime.getListings().searchListings(criteria), values -> {
                loaded = values;
                hasLoaded = true;
                renderResults();
                Platform.runLater(() -> {
                    if (app.isCurrent(page)) {
                        app.scroll().applyCss();
                        app.scroll().layout();
                        app.scroll().setVvalue(app.searchState.getScrollPosition());
                    }
                });
            });
        });
    }

    private void renderResults() {
        if (app.searchState.getSubmitted().isEmpty() || !hasLoaded) {
            return;
        }
        List<ListingWithSeller> ordered = new ArrayList<>(loaded);
        ordered.sort(Comparator.comparing(ListingWithSeller::listing,
                app.searchState.getSubmitted().orElseThrow().order()));
        Label count = UiControls.label(ordered.size() + (ordered.size() == 1 ? " listing" : " listings"), "muted");
        count.setId("results-count");
        results.getChildren().setAll(count);
        if (ordered.isEmpty()) {
            results.getChildren().add(UiControls.label("No listings match your search.", "muted"));
        } else {
            var grid = ListingCards.grid();
            ordered.forEach(value -> grid.getChildren().add(ListingCards.card(app, value, null)));
            results.getChildren().add(grid);
        }
    }
}
