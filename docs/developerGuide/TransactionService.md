---
  layout: default.md
  title: "Transaction service"
  pageNav: 3
---

## Transaction Service

`TransactionService` manages the lifecycle of an agreed sale between a buyer and seller. It handles sale completion, cancellation, cancellation requests, sales and purchase histories, and seller dashboard information.

Transaction data is persisted using SQLite.

### Transaction Lifecycle

A transaction represents an agreed sale between a buyer and seller. Only the buyer and seller participating in the transaction may view or modify it.

A transaction follows these rules:

* Each participant confirms completion independently.
* The transaction is completed when both participants have confirmed completion.
* Completing a transaction marks its associated listing as **sold**.
* Before either participant has confirmed completion, either participant may cancel the transaction directly.
* Cancelling a transaction releases its associated listing and makes it **available** again.
* Once either participant has confirmed completion, cancellation requires a cancellation request that must be accepted by the other participant.
* Only one cancellation request may be pending for a transaction at a time.
* While a cancellation request is pending, further completion confirmations are blocked.
* The participant who created a cancellation request may withdraw it.
* The other participant may accept or reject the request.
* Rejecting or withdrawing a cancellation request preserves any existing completion confirmations.
* Completed transactions are final.

Offers that were rejected when the sale was originally agreed remain rejected if the transaction is later cancelled.

### Service access and operations

Access it through `ApplicationRuntime.getTransactions()`. Every operation
requires login, and only the sale's buyer and seller may act on it. Actions take
only the sale ID; the request being accepted, rejected, or withdrawn is always
the sale's one pending request.

| Operation | Rule |
| --- | --- |
| `confirmCompletion(saleId)` | Active sale, no pending request, not yet confirmed by you. The second confirmation completes the sale and marks the listing sold. |
| `cancelSale(saleId)` | Active sale that nobody has confirmed. Releases the listing. |
| `requestCancellation(saleId)` | Active sale with a confirmation and no pending request. |
| `acceptCancellation` / `rejectCancellation(saleId)` | The participant who did not make the pending request. Accepting cancels and releases the listing. |
| `withdrawCancellation(saleId)` | The participant who made the pending request. |
| `getMySales()` / `getMyPurchases()` | One `SaleForParticipant` per agreed sale: pending request first, other active, completed, cancelled, each newest first. |
| `getSalesDashboard()` | `SalesDashboard`: pending offers across your listings, active and completed sale counts, the total of completed sales, and upcoming meetups. |

### Transaction Model

`Transaction` records the state of a sale, including:

* Buyer and seller
* Agreed price
* Completion confirmations from both participants
* Cancellation information
* Cancellation request history
* The participant who cancelled the transaction
* The time at which the transaction was cancelled

For a direct cancellation, the participant performing the cancellation is recorded as the cancelling participant. If a cancellation request is accepted, the participant who originally requested cancellation is recorded as the cancelling participant.

Persisted transactions are reconstructed using `Transaction.restore(...)`. Restoration rebuilds confirmations, cancellation details, and cancellation request history while applying the same validation rules as the live model.

`Transaction.restore` accepts a `Transaction.Snapshot` record containing the persisted state rather than a large collection of individual parameters.

The model exposes state queries including:

* `hasConfirmation`
* `hasConfirmed`
* `getPendingCancellation`
* `getLastEventAt`

These allow services and presentation logic to derive transaction state using the same rules as the domain model.

### Sales and Purchase Lists

`TransactionService` provides the **My Sales** and **My Purchases** lists.

Each entry represents an individual agreed sale rather than a listing. Consequently, the same listing can appear multiple times if an earlier transaction involving that listing was cancelled and another sale was subsequently agreed.

Entries are ordered as follows:

1. Active transactions with a pending cancellation request
2. Other active transactions
3. Completed transactions
4. Cancelled transactions

Within each group, the newest transactions appear first.

Each entry contains:

* Agreed price
* Listing snapshot containing the title, description, and condition at the time of sale
* Listing ID
* Other participant's public profile
* Buyer's and seller's confirmation times
* Cancellation details
* Cancellation request history
* Viewer's role (buyer or seller)
* Viewer's next step
* Actions currently available to the viewer

Only public profile information about the other participant is exposed.

### Transaction Next Steps

The service derives a next step for each transaction so that presentation code does not need to reproduce transaction-state rules.

| Transaction state | Next step |
| --- | --- |
| Active, viewer has not confirmed, no pending cancellation | Meet to hand over the item, then confirm completion |
| Active, viewer has confirmed, no pending cancellation | Wait for the other participant to confirm |
| Pending cancellation requested by the other participant | Respond to the cancellation request |
| Pending cancellation requested by the viewer | Wait for the other participant to respond |
| Completed or cancelled | None |

`Cancel sale` and `Request cancellation` are exposed as available actions when permitted rather than as next steps.

### Sales Dashboard

The seller dashboard aggregates transaction-related information:

* **Pending offers** — pending offers across all listings belonging to the seller.
* **Active sales** — number of active transactions where the current user is the seller.
* **Completed sales** — number of completed transactions where the current user is the seller.
* **Total sales value** — sum of the agreed prices of completed sales only.

### Relationship With Other Services

Responsibilities are divided between the marketplace services as follows:

| Data | Service | Entry represents |
| --- | --- | --- |
| My Listings | `ListingService` | Listing |
| Offers on a listing | `OfferService` | Offer |
| My Sales | `TransactionService` | Agreed sale |
| My Offers | `OfferService` | Offer |
| My Purchases | `TransactionService` | Agreed sale |

`My Offers` and `My Purchases` intentionally represent different concepts. Every purchase originates from an accepted offer, but the purchase represents the resulting transaction. Each purchase therefore retains the ID of its accepted offer so that presentation code can link the two.

### Persistence

Transaction data is persisted in SQLite.

Database migration `004`:

* Adds the `cancellation_requests` table.
* Adds cancellation time and cancelling-participant columns to `transactions`.
* Enforces at most one pending cancellation request per transaction using a partial unique index.
* Adds indexes for transactions by buyer and seller.

The `cancellation_requests` table stores:

* Cancellation request ID
* Transaction ID
* Requester ID
* Creation time
* Request status
* Resolution time

Operations that affect multiple entities are atomic. In particular, completing a sale updates both the transaction and its listing within one database transaction. Similarly, cancelling a sale updates the transaction, cancellation request where applicable, and listing within one database transaction.

This prevents a failure from leaving the transaction and listing in inconsistent states.

### Atomic changes and sale progress

Every change loads the sale, checks the participant and status, applies it
through the `Transaction` model, and saves the sale and any listing change in
one database transaction (`applyToActiveSale`). `SaleProgress` turns a sale's
state into the viewer's `NextStep` (with display text), `SaleAction`s, and list
position, using the same model queries the rules use, so screens never offer an
action that would be refused. Completing a sale completes its scheduled meetup,
and cancelling it cancels the meetup, in the same transaction.


### Error Handling

`TransactionService` uses the shared `ServiceException` error codes.

| Error | Usage |
| --- | --- |
| `NOT_FOUND` | The requested transaction or cancellation request does not exist. |
| `PERMISSION` | The current user is not a participant or is not permitted to perform the requested action. |
| `INVALID_STATE` | The transaction or cancellation request is not in a state that permits the requested operation. |

For example, a requester attempting to accept their own cancellation request results in a `PERMISSION` error, while attempting to confirm a completed transaction results in an `INVALID_STATE` error.

A null transaction ID is treated as a validation error.

### Testing

`TransactionService` is tested using JUnit 5 with temporary SQLite databases. Tests cover:

* Permissions and valid states for each transaction operation
* Attempts by incorrect participants to perform restricted operations
* Completion marking listings as sold
* Cancellation releasing listings
* Database rollback when multi-entity operations fail
* Blocking confirmations while cancellation requests are pending
* Preservation of confirmations after rejected or withdrawn cancellation requests
* Sales and purchase list membership and ordering
* Next-step calculation from both buyer and seller perspectives
* Listings involved in new sales after earlier transactions were cancelled
* Seller dashboard counts and completed-sale totals
* Persistence of confirmations and cancellation requests across application restarts
* Migration `004` against both fresh and version-3 databases
