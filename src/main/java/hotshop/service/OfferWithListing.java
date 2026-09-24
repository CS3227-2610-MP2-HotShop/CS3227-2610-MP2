package hotshop.service;

import java.util.Optional;

import hotshop.model.Offer;
import hotshop.model.TransactionStatus;

/**
 * A buyer's view of one of their offers: the offer, its listing with the seller's public profile,
 * and, for an accepted offer, the status of the sale it created. Models are detached copies.
 */
public record OfferWithListing(Offer offer, ListingWithSeller listing, Optional<TransactionStatus> saleStatus) {
}
