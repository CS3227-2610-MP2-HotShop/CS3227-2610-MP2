---
  layout: default.md
  title: "Offer service"
  pageNav: 3
---

## Offer Service

`OfferService` manages offers made by buyers on marketplace listings. It is responsible for offer creation and state transitions, access control, offer persistence, and coordinating offer acceptance with listing and transaction state.

Offer data is persisted using SQLite.

### Design

An `Offer` belongs to a buyer and a listing and contains an immutable offered amount.

An offer progresses from a pending state to one of the following terminal states:

* accepted;
* rejected; or
* withdrawn.

Closed offers are retained as historical records rather than deleted.

The model records:

* `createdAt`, representing when the offer was submitted; and
* `closedAt`, representing when a pending offer became accepted, rejected, or withdrawn.

`closedAt` remains empty while the offer is pending.

Persisted offers are reconstructed using `Offer.restore(...)`, which applies the same model validation rules used for newly created offers.

Offer amounts use the same upper price bound as listings through `ListingDetails.MAX_PRICE_CENTS`, preventing duplicated price-limit definitions across the domain model.

### Offer Invariants

The service enforces the following invariants:

* A buyer may have at most one pending offer for a particular listing.
* Offers may only be submitted against available listings.
* A user cannot submit an offer for their own listing.
* Offer amounts are immutable after submission.
* Only pending offers may be withdrawn, accepted, or rejected.
* Closed offers remain persisted for historical purposes.

The one-pending-offer rule is enforced both by `OfferService` and by a database constraint. This protects the invariant even if multiple operations attempt to create offers concurrently.

### Service access and operations

Access it through `ApplicationRuntime.getOffers()`. It shares the worker,
session, and database with the other services, and every operation requires login.

| Operation | Who | Rule |
| --- | --- | --- |
| `submitOffer(listingId, amountCents)` | Buyer | Another seller's available listing; one pending offer per buyer and listing. |
| `submitOffer(listingId, amountCents, message)` | Buyer | As above, with an optional first message (null or blank means none). |
| `withdrawOffer(offerId)` | The offer's buyer | Pending offers only. |
| `getMyOffers()` | Buyer | Own offers in every status, newest first, each with listing and seller. |
| `getOffersForListing(listingId)` | The listing's seller | Every offer; live sale first, then accepted offers whose sale was cancelled, then the rest newest first. |
| `acceptOffer(offerId)` | The listing's seller | Pending offer on an available listing. |
| `rejectOffer(offerId)` | The listing's seller | Pending offers only. |

### Atomic offer acceptance

`acceptOffer` does everything in one database transaction: accept the offer,
reserve the listing, reject the other pending offers through `PendingOffers`, and
save a `Transaction`. It returns `AcceptedOffer` (offer, reserved listing, sale
ID). `OfferWithListing` and `OfferWithBuyer` carry the sale status for accepted
offers, so screens can show "Accepted, sale cancelled" without a new offer status.

### Service Boundaries

`OfferService` owns offer state transitions but does not manage the subsequent sale lifecycle.

Once an offer is accepted:

* `OfferService` creates the initial transaction;
* `TransactionService` manages transaction confirmation and cancellation; and
* `ListingService` remains responsible for the listing model while transaction-related listing transitions are coordinated by the relevant service.

If a transaction is later cancelled, the associated accepted offer remains accepted as historical information. The transaction state records the eventual outcome of the sale rather than changing the offer back to another state.

### Integration With ListingService

Offer state must remain consistent when listings are modified.

When an existing listing is materially edited, all pending offers associated with that listing are rejected within the same database transaction as the listing update.

An update that produces no actual listing changes does not reject pending offers.

Archiving a listing similarly rejects all pending offers atomically.

A listing with any offer history cannot be permanently deleted, regardless of the offers' current states. This preserves referential and historical information associated with marketplace activity.

The shared `PendingOffers` component encapsulates the operation for rejecting all pending offers on a listing. It is reused by:

* offer acceptance;
* listing editing; and
* listing archiving.

This avoids implementing the same cross-service state transition independently in multiple locations.

A **partial unique index** enforces at most one pending offer for each `(buyer, listing)` pair.

The migration also introduces the `transactions` table required when accepting an offer. A partial unique index ensures that a listing has at most one active transaction.

The transaction table does not persist `Transaction`'s derived last-event time. Instead, this value is reconstructed from persisted event timestamps when required, avoiding redundant state.

### Shared support and refusal messages

`ServiceSupport` holds plumbing shared by ListingService and OfferService: the
truncated current time, transaction error mapping, public-profile lookup, status
words, and price formatting (`S$40.00`). `ServiceException` has factories for
its codes. Refusal messages say what is wrong and what to do, using real values,
and never mention SQL. Tests assert the code and key values in selected
messages, not exact wording.

### Conversations and cancelled sales

Submitting an offer also starts the buyer's conversation about the listing, or
reuses it, in the same database transaction (through `Conversations`), so the
seller always has a conversation with everyone who offered.

After TransactionService cancels a sale, the released listing can receive offers
again.

### Error Handling

`OfferService` uses the shared `ServiceException` mechanism.

| Error | Meaning |
| --- | --- |
| `VALIDATION` | The supplied offer data is invalid. |
| `NOT_FOUND` | The requested listing or offer cannot be found. |
| `PERMISSION` | The authenticated user is not permitted to perform the requested operation. |
| `INVALID_STATE` | The relevant listing or offer exists but its current state prevents the operation. |

For example, attempting to operate on another user's offer is a `PERMISSION` error, while attempting to accept an offer that is no longer pending is an `INVALID_STATE` error.

### Testing

`OfferService` is tested using JUnit 5 with temporary SQLite databases.

Tests focus on:

* offer amount boundaries;
* enforcement of one pending offer per buyer and listing;
* ownership and participant permissions;
* valid and invalid offer state transitions;
* atomic offer acceptance across offers, listings, and transactions;
* rollback when any part of acceptance fails;
* offer visibility and access control;
* persistence across application restarts;
* rejection of pending offers following listing modifications;
* prevention of listing deletion when offer history exists; and
* database migration from existing schemas.
