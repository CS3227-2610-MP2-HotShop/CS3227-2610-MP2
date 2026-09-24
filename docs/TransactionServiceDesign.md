# TransactionService Design

Status: agreed through a grill-with-docs interview on 2026-09-24 and approved for
implementation through the implement skill. Implemented as described, apart from
the notes under [Implementation notes](#implementation-notes). Screens remain deferred.

## Agreed scope

- Deliver TransactionService with SQLite persistence and JUnit 5 tests. Screens,
  and how they display or further process the lists, follow later.
- **This milestone includes the buyer's "My purchases" list** as well as the
  seller's "My sales" list and dashboard summary. Buyer screens should call these
  operations rather than implementing them again.
- Rules already settled by the `Transaction` model, [Buyer Model Design](BuyerModelDesign.md),
  and the architecture still apply: each participant confirms separately and the
  second confirmation completes the sale; either participant may cancel directly
  before the first confirmation; afterwards cancellation needs a request the other
  participant accepts; one pending request at a time, which blocks further
  confirmations; the requester may withdraw it and the other participant may
  reject it; completed sales are final; only the two participants can see a sale.
- Completing a sale marks its listing sold; cancelling releases it to available.
  Offers rejected when the sale was agreed stay rejected.
- Cancellation requests carry no written reason; chat is the place to discuss.
- Notifications and meetups are deferred to their own services.

## Model changes

These extend the shared models and must be flagged to the teammate in the PR.

- `Transaction` records **who cancelled and when**. `cancel` takes a time. The
  "cancelled by" participant is the one who cancelled directly, or the requester
  whose cancellation request was accepted.
- `Transaction.restore(...)` and `CancellationRequest` restoration rebuild a
  persisted sale with its confirmations, cancellation details, and request
  history, with the same validation as the live model.

## Service contract

Every operation requires login and acts as the session's current user. Only the
sale's buyer and seller may see or act on it. Results include the other
participant's public profile only.

| Operation | Contract |
| --- | --- |
| Confirm completion | Participant; active sale; no pending cancellation request; not already confirmed by this participant. The second confirmation completes the sale and marks the listing sold, in one database transaction. |
| Cancel sale | Participant; active sale with no confirmations yet. Cancels and releases the listing in one database transaction. |
| Request cancellation | Participant; active sale with at least one confirmation and no pending request. |
| Accept cancellation | The other participant; the request must be pending. Cancels and releases the listing in one database transaction. |
| Reject cancellation | The other participant; the request must be pending. Existing confirmations stay. |
| Withdraw cancellation | The requester; the request must be pending. Existing confirmations stay. |
| My sales | Every sale where the current user is the seller. |
| My purchases | Every sale where the current user is the buyer. |
| Sales dashboard | The current user's pending offers across their listings, active sales, completed sales, and total sales value. |

### Lists

- My Sales and My Purchases have **one entry per agreed sale**. A listing appears
  more than once only when an earlier sale of it was cancelled; each entry is a
  separate sale with its own buyer, price, and outcome. My Listings is unchanged
  and still has one entry per listing.
- Order: active sales with a pending cancellation request, then other active
  sales, then completed, then cancelled; each group newest first.
- Each entry carries: the agreed price; the listing snapshot (title, description,
  condition at the time of the sale); the listing ID; the other participant's
  public profile; both confirmation times; cancellation details; the request
  history; the viewer's next step; and the actions available to the viewer.

### Next steps

| Next step | When |
| --- | --- |
| Meet to hand over the item, then confirm completion | Active; the viewer has not confirmed; no pending request |
| Waiting for the other participant to confirm | Active; the viewer has confirmed; no pending request |
| Respond to the other participant's cancellation request | Active; pending request from the other participant |
| Waiting for the other participant to respond to your request | Active; pending request from the viewer |
| None | Completed or cancelled |

"Cancel sale" and "Request cancellation" are listed as available actions when the
rules allow them, not as next steps. When MeetupService exists, the first next
step splits into "Arrange a meetup" and "Meet on (date), then confirm".

### Dashboard

- Pending offers: pending offers on all of the seller's listings.
- Active sales and completed sales: counts of the seller's sales.
- Total sales value: the sum of agreed prices of **completed** sales only.
- Upcoming meetups are added with MeetupService.

## Services, lists, and pages

This design covers data and rules. Which lists share a page is decided with the
screens, but the intended mapping is:

| List | Service | One entry per | Page (intended) |
| --- | --- | --- | --- |
| My Listings | ListingService | Listing, in every status | Seller's listings page; tapping a listing opens it with its offers |
| Offers on a listing | OfferService | Offer | The opened listing |
| My Sales and the dashboard summary | TransactionService | Agreed sale | One seller "Sales dashboard and history" page |
| My Offers | OfferService | Offer | Buyer's offers page |
| My Purchases | TransactionService | Agreed sale | Buyer's purchases page |

My Offers and My Purchases stay separate lists. Every purchase started as an
accepted offer, which My Offers already shows with its sale status; each purchase
carries the ID of its accepted offer so a screen can link the two.

## Changes to ListingService

These change ListingService, already in PR #6, and are flagged in this PR.

- My Listings order: **reserved, then available, then sold, then archived**,
  each newest first. Reserved listings come first because they await a physical
  handover and confirmation.
- Each My Listings entry carries its **number of pending offers**. The offers
  themselves are seen by opening the listing.

## Errors

The shared `ServiceException` codes: `NOT_FOUND` for an unknown sale or request,
`PERMISSION` for a non-participant or the wrong participant (for example, the
requester trying to accept their own request), and `INVALID_STATE` when the sale
or request status does not allow the action. Messages say what is wrong and what
to do, with real values, following the OfferService standard.

## Persistence

Migration `004` adds a `cancellation_requests` table (ID, transaction ID,
requester ID, creation time, status, resolution time) and the cancellation time
and participant columns on `transactions`. Status changes to the sale, its
requests, and its listing are saved in one database transaction.

## Hooks for later services

- MeetupService: cancelling a sale cancels its upcoming meetup and pending
  rescheduling proposals in the same transaction; the next step gains meetup
  details; the dashboard gains upcoming meetups.
- NotificationService: notify the other participant of confirmations,
  cancellations, and cancellation requests and responses.

## Verification scope

JUnit 5 tests with temporary databases covering: each operation's permissions and
states, including the wrong participant; completion marking the listing sold and
cancellation releasing it, both rolled back on failure; confirmations blocked
while a request is pending; rejected and withdrawn requests preserving
confirmations; list membership, ordering, and next steps for each state from both
sides; a listing that was sold after an earlier cancelled sale; dashboard counts
and completed-only total; restart persistence of requests and confirmations;
migration `004` on fresh and version-3 databases.

## Deferred work

Sale screens, meetups, notifications, returns and refunds, and cancellation reasons.

## Implementation notes

Decisions made during implementation, within the agreed scope:

- Sale actions take the sale ID only; the request that accept, reject, and
  withdraw act on is always the sale's one pending request.
- Each entry also carries the viewer's role (buyer or seller), and a null sale ID
  is reported as a validation error.
- Migration 004 also enforces one pending request per sale with a partial unique
  index, and indexes sales by buyer and by seller.
- `Transaction` gained public state queries (`hasConfirmation`, `hasConfirmed`,
  `getPendingCancellation`, `getLastEventAt`) so the service and the next-step
  rules use the same logic as the model.
- `Transaction.restore` takes a `Transaction.Snapshot` record rather than sixteen
  separate parameters.
