# OfferService Design

Status: agreed through a grill-with-docs interview on 2026-09-24 and approved for
implementation through the implement skill. Implemented as described, apart from
the notes under [Implementation notes](#implementation-notes). Screens remain deferred.

## Agreed scope

- Deliver OfferService with SQLite persistence and JUnit 5 tests. Screens follow
  once a login screen exists.
- **This milestone includes the buyer operations** (submit, withdraw, my offers)
  as well as the seller operations (view, accept, reject), subject to the
  teammate's agreement. Buyer screens should call these operations rather than
  implementing them again.
- Accepting an offer saves the new transaction. Completion confirmations,
  cancellation, cancellation requests, and purchase/sales history belong to a
  later TransactionService.
- Notifications are deferred to NotificationService.
- Rules already settled by [Buyer Model Design](BuyerModelDesign.md) and the
  architecture still apply: at most one pending offer per buyer and listing; no
  offers on one's own listing or on a listing that is not available; amounts are
  fixed, so changing one means withdrawing and making a new offer; offers may
  exceed the asking price; closed offers stay in history.

## Model changes

These extend the shared models and must be flagged to the teammate in the PR.

- `Offer` gains `createdAt` and `closedAt`. `closedAt` is empty while pending and
  set when the offer is accepted, rejected, or withdrawn.
- `Offer.restore(...)` rebuilds a persisted offer with the same validation.
- Offer amounts are capped at S$1,000,000, reusing `ListingDetails.MAX_PRICE_CENTS`.

## Service contract

Every operation requires login and acts as the session's current user. Results
include public profiles only, never private profile data.

| Operation | Who | Contract |
| --- | --- | --- |
| Submit offer | Buyer | Listing must exist, be available, and not be the buyer's own. No existing pending offer from this buyer on this listing. |
| Withdraw offer | The offer's buyer | Offer must be pending. |
| My offers | Buyer | All of the current user's offers across listings, every status, newest first, each with its listing and the seller's public profile. |
| Offers on a listing | The listing's seller | All offers on one listing, every status, each with the buyer's public profile. Ordering below. |
| Accept offer | The listing's seller | Offer must be pending and the listing available. In one database transaction: reserve the listing, accept the offer, reject every other pending offer on it, and save the new transaction. Returns the accepted offer, the reserved listing, and the new sale's ID. |
| Reject offer | The listing's seller | Offer must be pending. |

Only the offer's buyer and the listing's seller can see an offer. Other buyers
never see competing offers or amounts.

### Ordering of offers on a listing

1. The accepted offer whose sale is still active or completed.
2. Accepted offers whose sale was cancelled, newest first.
3. All other offers, newest first.

Each accepted offer's result carries its sale status (active, completed, or
cancelled), so screens can show "Accepted, sale cancelled" without a new offer
status. Sales cannot be cancelled until TransactionService exists.

## Changes to ListingService

- An edit that actually changes a listing rejects all of its pending offers in
  the same database transaction. Saving without changes rejects nothing.
- Archiving rejects all pending offers in the same database transaction.
- Screens should warn before either action when pending offers exist.
- Delete is refused once a listing has any offer history, in any status.
- This matches the existing architecture and Buyer Model Design; no earlier
  decision changes.

## Errors

The same `ServiceException` codes as ListingService:

| Situation | Code |
| --- | --- |
| Amount outside S$0.01 to S$1,000,000 | `VALIDATION` |
| Listing or offer does not exist | `NOT_FOUND` |
| Offering on one's own listing; acting on someone else's offer or listing | `PERMISSION` |
| Listing reserved, sold, or archived; offer no longer pending; existing pending offer | `INVALID_STATE` |

Every refusal carries a specific, user-readable message that says what is wrong
and what to do, uses real values where helpful (for example, "You already have a
pending offer of S$40.00 on this listing. Withdraw it before making a new one."),
and never mentions database or internal details. ListingService messages are
brought up to the same standard in this milestone. Tests check the code and the
key values in selected messages, not exact wording.

## Persistence

Migration `003` adds:

- an `offers` table with listing ID, buyer ID, amount, status, `created_at`, and
  `closed_at`, and a partial unique index allowing one pending offer per buyer
  and listing;
- a `transactions` table with the fields in the `Transaction` model, including
  snapshot and confirmation columns for TransactionService, and a partial unique
  index allowing one active transaction per listing.

Cancellation requests get their own table when TransactionService is built.

## Hooks for later services

- NotificationService: add offer accepted and rejected notifications inside the
  accept and reject transactions.
- TransactionService: confirmations, cancellation, and releasing the listing;
  after a cancellation the listing can receive offers again.
- ChatService: make delete refuse listings with conversation history.

## Verification scope

JUnit 5 tests with temporary databases covering: amount boundaries and the cap;
each refusal and its code; one pending offer per buyer and listing; withdraw,
accept, and reject permissions and states; acceptance atomicity (listing
reserved, competing offers rejected, transaction saved, all rolled back on
failure); visibility of offers; ordering; restart persistence; edit and archive
rejecting pending offers; delete refused with offer history; migration `003` on
fresh and version-2 databases.

## Deferred work

Offer screens, TransactionService, notifications, and counteroffers.

## Implementation notes

Decisions made during implementation, within the agreed scope:

- The `transactions` table does not store `Transaction`'s internal last-event
  time; TransactionService can derive it from the saved event times.
- Image validation messages now restate the limits for both profile and listing
  photos, because both go through the shared image namespace code.
- `PendingOffers` holds the "reject every pending offer" step shared by accept,
  edit, and archive; `ServiceSupport` holds time, transaction, profile, and
  price-formatting plumbing shared by ListingService and OfferService.
