package hotshop.service;

import java.util.UUID;

import hotshop.model.Listing;
import hotshop.model.Offer;

/**
 * The result of accepting an offer: the accepted offer, the now-reserved listing, and the ID of the
 * new sale. The sale itself is managed by the future TransactionService. Models are detached copies.
 */
public record AcceptedOffer(Offer offer, Listing listing, UUID transactionId) {
}
