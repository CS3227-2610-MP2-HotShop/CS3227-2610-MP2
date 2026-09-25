package hotshop.ui;

import java.util.Optional;

import hotshop.service.ListingSearch;
import hotshop.service.ListingSort;

/** Session-local search criteria: editing controls never changes the last submitted search. */
public final class SearchState {
    private ListingSearch draft = ListingSearch.all();
    private ListingSearch submitted;
    private double scrollPosition;
    private DraftText text = new DraftText("", "", "");

    /** Raw text survives navigation even when an unfinished price is not yet parseable. */
    public record DraftText(String query, String minimumPrice, String maximumPrice) {
    }

    public ListingSearch getDraft() {
        return draft;
    }

    public void setDraft(ListingSearch value) {
        draft = value;
        text = new DraftText(value.titleText() == null ? "" : value.titleText(),
                amount(value.minPriceCents()), amount(value.maxPriceCents()));
    }

    public DraftText getDraftText() {
        return text;
    }

    public void setDraftText(DraftText value) {
        text = value;
    }

    public void submit() {
        submitted = draft;
    }

    public Optional<ListingSearch> getSubmitted() {
        return Optional.ofNullable(submitted);
    }

    /** Sort applies to submitted results without submitting pending edits. */
    public void sort(ListingSort sort) {
        draft = withSort(draft, sort);
        if (submitted != null) {
            submitted = withSort(submitted, sort);
        }
    }

    public double getScrollPosition() {
        return scrollPosition;
    }

    public void setScrollPosition(double value) {
        scrollPosition = value;
    }

    public void reset() {
        draft = ListingSearch.all();
        submitted = null;
        scrollPosition = 0;
        text = new DraftText("", "", "");
    }

    private ListingSearch withSort(ListingSearch value, ListingSort sort) {
        return new ListingSearch(value.titleText(), value.category(), value.conditions(),
                value.minPriceCents(), value.maxPriceCents(), sort);
    }

    private String amount(Long cents) {
        return cents == null ? "" : java.math.BigDecimal.valueOf(cents, 2).toPlainString();
    }
}
