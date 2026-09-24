package hotshop.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import hotshop.service.ListingSearch;
import hotshop.service.ListingSort;

class SearchStateTest {
    @Test
    void sort_draftDiffersFromSubmission_changesOnlySubmittedSortAndKeepsScroll() {
        SearchState state = new SearchState();
        state.setDraft(new ListingSearch("chairs", null, null, null, null, ListingSort.NEWEST));
        state.submit();
        state.setDraft(new ListingSearch("books", null, null, null, null, ListingSort.NEWEST));
        state.setScrollPosition(0.6);
        state.sort(ListingSort.PRICE_LOW_TO_HIGH);
        assertEquals("chairs", state.getSubmitted().orElseThrow().titleText());
        assertEquals(ListingSort.PRICE_LOW_TO_HIGH, state.getSubmitted().orElseThrow().sort());
        assertEquals("books", state.getDraft().titleText());
        assertEquals(0.6, state.getScrollPosition());
    }

    @Test
    void reset_newSession_removesSubmittedSearchAndScroll() {
        SearchState state = new SearchState();
        state.submit();
        state.setScrollPosition(0.8);
        state.reset();
        assertTrue(state.getSubmitted().isEmpty());
        assertEquals(ListingSearch.all(), state.getDraft());
        assertEquals(0, state.getScrollPosition());
    }

    @Test
    void setDraft_unsubmittedEdits_preservesSubmittedCriteriaForRefresh() {
        SearchState state = new SearchState();
        assertTrue(state.getSubmitted().isEmpty());
        state.setDraft(new ListingSearch("chairs", null, null, null, null, ListingSort.NEWEST));
        state.submit();
        state.setDraft(new ListingSearch("books", null, null, null, null, ListingSort.NEWEST));
        assertEquals("chairs", state.getSubmitted().orElseThrow().titleText());
        assertEquals("books", state.getDraft().titleText());
    }
}
