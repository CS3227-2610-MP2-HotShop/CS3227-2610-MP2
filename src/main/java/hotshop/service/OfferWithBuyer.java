package hotshop.service;

import java.util.Optional;

import hotshop.model.Offer;
import hotshop.model.TransactionStatus;

/**
 * A seller's view of one offer on their listing: the offer, the buyer's public profile, and, for an
 * accepted offer, the status of the sale it created. The offer is a detached copy.
 */
public record OfferWithBuyer(Offer offer, PublicProfile buyer, Optional<TransactionStatus> saleStatus) {
}
