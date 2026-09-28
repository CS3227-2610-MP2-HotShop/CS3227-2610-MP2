---
  layout: default.md
  title: "Listing service"
  pageNav: 3
---

## Listing Service

`ListingService` manages the lifecycle and persistence of marketplace listings. It is responsible for listing creation and modification, listing retrieval and search, listing image management, ownership enforcement, and selected listing lifecycle transitions.

Listing data is persisted using SQLite, while listing images are managed separately on the filesystem.

### Design

Every listing belongs to a seller identified using the application's current authenticated session. Service operations do not accept a seller ID from presentation code, preventing callers from performing operations on behalf of another user.

The `Listing` model maintains two timestamps:

* `createdAt`, which is assigned when the listing is created and never changes; and
* `updatedAt`, which changes only when the listing's sale details or images actually change.

Status transitions and updates containing no effective changes do not modify `updatedAt`.

Persisted listings are reconstructed using `Listing.restore(...)`. Restoration preserves the listing ID, status, and timestamps while applying the same model validation rules used when creating listings.

Listing prices are represented in cents and are restricted to the range supported by `ListingDetails`, with an upper bound of S$1,000,000.

### Service access and operations

Access it through `ApplicationRuntime.getListings()`. It shares the worker,
session, and database with AccountService, and every operation requires login.

| Operation | Rule |
| --- | --- |
| `createListing(draft, photos)` | Saves an available listing owned by the current user. |
| `updateListing(id, draft, photos)` | Owner only; available listings only. |
| `archiveListing(id)` | Owner only; available or sold listings. |
| `deleteListing(id)` | Owner only; available or archived listings; removes photos. |
| `getMyListings()` | Current user's listings as `OwnListing` (listing plus pending offer count): reserved, available, sold, archived, each newest first. |
| `getListing(id)` | Any existing listing in any status. |
| `searchListings(search)` | Other sellers' available listings only. |

### Service Boundaries

`ListingService` does not manage listing status transitions associated with marketplace transactions.

`OfferService` and `TransactionService` are responsible for transitions such as:

* reserving a listing;
* releasing a reserved listing; and
* marking a listing as sold.

This keeps transaction-specific business rules outside `ListingService`.

`OfferService` also coordinates listing modifications with offer state. When a listing is materially edited or archived, affected pending offers are rejected within the same database transaction.

Deletion similarly respects historical data managed by other services. Listings with offer or transaction history cannot be deleted. Enquiry-only conversations associated with a deletable listing are removed as part of the deletion operation.

### Draft validation and photo storage

Screens pass a `ListingDraft` of raw form values; invalid values become
`VALIDATION` failures rather than exceptions from the model. Photos are a complete
ordered `List<ListingPhoto>` of `ListingPhoto.keep(filename)` and
`ListingPhoto.add(path)` entries (0 to 10), validated against
`ImageStorage.LISTING_LIMITS` (JPEG/PNG, 10 MiB, 4096 px per side). Imports happen
before the database write; on any failure, recovery removes the unsaved copies.
Results are `ListingWithSeller`: a detached `Listing` plus the seller's
`PublicProfile`. Non-owners get `PERMISSION`; a status that forbids the action
gets `INVALID_STATE`. Both codes are part of the shared `ServiceException`.

### Persistence and Transactions

Listing data is persisted using SQLite.

Migration `002` introduces:

* the `listings` table containing listing data, timestamps, and status constraints;
* the `listing_images` table containing image filenames and display ordering; and
* namespaced image-cleanup records shared by profile and listing image management.

Repository operations use the database connection supplied by the calling service. This allows a business operation involving multiple repositories to execute within a single database transaction rather than each repository independently committing changes.

This is particularly important for operations spanning listings and other marketplace entities, such as rejecting offers while modifying a listing.

Filesystem operations cannot participate directly in a SQLite transaction. Image handling therefore follows a staged approach:

1. Import new files.
2. Perform the database transaction.
3. Remove newly imported files if the transaction fails.
4. After a successful commit, queue obsolete files for cleanup.
5. Retry pending cleanup when the application starts.

This design provides consistency between database state and filesystem-managed images despite the lack of a shared transaction mechanism between them.

### Search filtering and sorting

`ListingSearch` holds optional filters (title text, category, conditions, price
bounds of 0 to the price cap) and a `ListingSort`. SQL selects other sellers'
available listings; `ListingSearch` then filters and sorts them in Java because
SQLite's case-insensitive matching covers ASCII letters only. There is no
pagination.

### Deterministic service checks

`ApplicationRuntime.open(Path, Clock)` lets tests fix the time; timestamps are
truncated to milliseconds to match what SQLite stores. Some listing tests set a
listing's status in SQL to reach reserved or sold states directly; offer and sale
tests use the real services.

### Error Handling

`ListingService` uses the shared `ServiceException` mechanism.

| Error | Meaning |
| --- | --- |
| `VALIDATION` | Supplied listing data, image data, or search parameters are invalid. |
| `AUTHENTICATION` | Authentication failed. |
| `SESSION` | The required authenticated session is unavailable. |
| `NOT_FOUND` | The requested listing does not exist or has been deleted. |
| `STORAGE` | A database or filesystem operation fails. |
| `PERMISSION` | The authenticated user does not own a listing required by the operation. |
| `INVALID_STATE` | The listing exists but its current state does not permit the requested operation. |

The distinction between `PERMISSION` and `INVALID_STATE` allows callers and tests to distinguish ownership violations from invalid lifecycle transitions.

### Testability

`ApplicationRuntime.open(Path, Clock)` allows tests to provide a fixed `Clock`. This makes timestamp-dependent listing behaviour deterministic without depending on the system clock.

`ListingService` is tested using JUnit 5 with temporary SQLite databases and temporary image directories.

Tests focus on:

* domain validation and price boundaries;
* ownership enforcement;
* listing lifecycle restrictions;
* deletion constraints involving historical data;
* image validation, ordering, rollback, and cleanup;
* search filtering and ordering;
* persistence and restoration of listing state; and
* database migration from existing schemas.## Listing Service
