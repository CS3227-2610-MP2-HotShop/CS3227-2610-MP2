package hotshop.service;

import java.util.Optional;

/**
 * One of the current user's own listings with its number of pending offers. The count is shown
 * only to the seller; other users never learn how many competing offers a listing has. Reserved
 * listings also carry their active sale's meetup summary.
 */
public record OwnListing(ListingWithSeller listing, int pendingOffers, Optional<MeetupSummary> meetup) {
}
