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

    /**
     * Returns the current draft search criteria.
     *
     * @return the current draft search criteria
     */
    public ListingSearch getDraft() {
        return draft;
    }

    /**
     * Replaces the draft criteria and synchronizes their raw text without submitting a search.
     *
     * @param value the replacement draft criteria
     */
    public void setDraft(ListingSearch value) {
        draft = value;
        text = new DraftText(value.titleText() == null ? "" : value.titleText(),
                amount(value.minPriceCents()), amount(value.maxPriceCents()));
    }

    /**
     * Returns the raw query and price text, including unfinished input.
     *
     * @return the raw query and price text, including unfinished input
     */
    public DraftText getDraftText() {
        return text;
    }

    /**
     * Stores raw control text so unfinished input survives navigation.
     *
     * @param value the raw query and price text
     */
    public void setDraftText(DraftText value) {
        text = value;
    }

    /**
     * Captures the current draft as the submitted search criteria.
     */
    public void submit() {
        submitted = draft;
    }

    /**
     * Returns the last submitted search criteria, or empty before the first search.
     *
     * @return the last submitted search criteria, or empty before the first search
     */
    public Optional<ListingSearch> getSubmitted() {
        return Optional.ofNullable(submitted);
    }

    /**
     * Sort applies to submitted results without submitting pending edits.
     *
     * @param sort the requested ordering, or null for newest first
     */
    public void sort(ListingSort sort) {
        draft = withSort(draft, sort);
        if (submitted != null) {
            submitted = withSort(submitted, sort);
        }
    }

    /**
     * Returns the remembered results scroll position.
     *
     * @return the remembered results scroll position
     */
    public double getScrollPosition() {
        return scrollPosition;
    }

    /**
     * Remembers the results scroll position for later navigation.
     *
     * @param value the results scroll position to remember
     */
    public void setScrollPosition(double value) {
        scrollPosition = value;
    }

    /**
     * Clears submitted criteria, draft text, and scroll position for a new session.
     */
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
