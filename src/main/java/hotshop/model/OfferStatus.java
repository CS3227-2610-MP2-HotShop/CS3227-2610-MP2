package hotshop.model;

/** A formal offer can leave PENDING only once. */
public enum OfferStatus {
    PENDING, ACCEPTED, REJECTED, WITHDRAWN
}
