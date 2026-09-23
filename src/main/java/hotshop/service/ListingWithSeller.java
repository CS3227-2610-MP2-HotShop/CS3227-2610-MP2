package hotshop.service;

import hotshop.model.Listing;

/**
 * A listing together with its seller's public profile, never private profile data. The listing
 * is a detached copy; changing it has no effect unless passed back through ListingService.
 */
public record ListingWithSeller(Listing listing, PublicProfile seller) {
}
