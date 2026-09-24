package hotshop.service;

/**
 * One of the current user's own listings with its number of pending offers. The count is shown
 * only to the seller; other users never learn how many competing offers a listing has.
 */
public record OwnListing(ListingWithSeller listing, int pendingOffers) {
}
