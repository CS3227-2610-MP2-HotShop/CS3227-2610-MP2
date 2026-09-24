# ListingService Design

Status: agreed through a grill-with-docs interview on 2026-09-24 and approved for
implementation through the implement skill. Implemented as described, apart from
the notes under [Implementation notes](#implementation-notes). Screens remain deferred.

## Agreed scope

- Deliver ListingService with SQLite persistence, managed listing images, and
  JUnit 5 tests. Seller and buyer listing screens follow once a login screen
  exists; the application keeps its welcome screen for now.
- **This milestone includes buyer browse and search.** ListingService provides
  both the seller operations and the buyer search operation. Buyer screens should
  call these operations rather than implementing a second search.
- Offers, transactions, and conversations do not exist yet. This milestone does
  not reject pending offers and does not check offer or conversation history;
  see [Hooks for later services](#hooks-for-later-services).
- The model rules already in `Listing` stay as they are: only available
  listings can be edited, reserved listings are frozen, and sold or archived
  listings cannot be reopened.

## Model changes

These extend the shared models and must be flagged to the teammate in the PR.

- `Listing` gains `createdAt` (set at creation, never changes) and `updatedAt`.
- `updatedAt` starts equal to `createdAt` and changes only when sale details
  or images actually change, that is, when `Listing.update` returns true.
  Status changes and unchanged saves leave it alone.
- `Listing.restore(...)` rebuilds a persisted listing, including its ID,
  status, and timestamps, with the same validation as creation. The creation
  constructor still generates a new ID.
- `ListingDetails` rejects prices above S$1,000,000 (100,000,000 cents). The
  minimum stays at 1 cent.

## Service contract

Names describe operations; final Java signatures follow repository conventions.
Every operation requires login and uses the session's current user, never a
user ID supplied by a screen. Results that show a listing include the seller's
`PublicProfile` (ID, display name, optional image), never private profile data.

| Operation | Contract |
| --- | --- |
| Create listing | Validate details and 0 to 10 new photos; save the listing as available with the current user as seller. |
| Edit listing | Owner only; listing must be available. Replace details and the complete ordered photo list together. |
| Archive listing | Owner only; listing must be available or sold. |
| Delete listing | Owner only; listing must be available or archived and have no offer, transaction, or conversation history. Removes the listing and queues its photos for cleanup. |
| My listings | The current user's listings in every status, newest first by creation time. |
| Get listing by ID | Any logged-in user; any status except deleted. Unknown or deleted IDs are not found. |
| Search listings | Available listings only, excluding the current user's own listings. Filters and sorting below. |

Permission checks happen in the service even though the screens will never
offer these actions to non-owners. For example, a stale edit form submitted
after a different user logs in must be rejected.

### Search rules

- Title text: case-insensitive "contains" match on the title only. Blank means
  no text filter.
- Category: optional, at most one.
- Condition: optional, one or more.
- Price: optional minimum and maximum, inclusive, in cents, each at most the
  listing price cap. A minimum above the maximum is a validation error.
- Sort: newest first (default), price low to high, or price high to low. Ties
  are ordered newest first.
- No pagination; all matches are returned.

## Listing photos

- JPEG or PNG only, at most 10 MiB (10 * 1024 * 1024 bytes) and 4096 pixels
  wide and high. Rectangular photos are allowed; photos are not resized.
- Stored under `images/listings/` with generated filenames, separate from
  profile images.
- Creating or editing takes the complete ordered photo list. Each entry either
  keeps an existing photo of that listing or imports a new file.
- All-or-nothing: if any new photo is invalid or saving fails, nothing changes
  and newly copied files are cleaned up.
- Photos no longer used are queued for cleanup only after the save succeeds.

## Errors

Rename `AccountException` to a shared `ServiceException` used by both services,
keeping the existing codes (`VALIDATION`, `AUTHENTICATION`, `SESSION`,
`USERNAME_UNAVAILABLE`, `NOT_FOUND`, `STORAGE`) and adding:

- `PERMISSION`: the current user does not own the listing. Mainly a safeguard
  that tests must cover; the UI never offers these actions.
- `INVALID_STATE`: the listing's status does not allow the action, for example
  editing a reserved listing or deleting a sold one.

The rename changes the teammate's public API and tests, so it needs their
agreement before merging.

## Persistence

Migration `002` adds:

- a `listings` table with the listing fields, timestamps, and status checks
  matching the Java enums;
- a `listing_images` table with listing ID, filename, and display order;
- a namespace for each `image_cleanup` row, with existing rows tagged as
  profile images, so profile and listing cleanup only process their own files.

Repositories use the caller's connection so one business operation stays in one
transaction. Filesystem changes follow the existing profile-image pattern:
import before saving, clean up failed imports, and retire old files only after
commit, with startup retrying pending cleanup.

## Hooks for later services

- OfferService must reject pending offers inside the same transaction when an
  edit actually changes a listing or when a listing is archived.
- OfferService and ChatService must make delete refuse listings with offer or
  conversation history.
- OfferService and TransactionService reserve, release, and mark listings sold;
  ListingService does not expose those transitions.

## Verification scope

JUnit 5 tests with temporary databases and image folders covering: validation
and price boundaries; owner and non-owner edit, archive, and delete; each status
for each action; delete history rules; photo limits, ordering, all-or-nothing
failures, and cleanup; search filters, boundaries, sorting, and exclusion of own
and non-available listings; restore round trips; migration `002` on fresh and
version-1 databases with existing profile cleanup rows.

## Deferred work

Seller and buyer listing screens, offers, pending-offer rejection, history
checks for delete, listing templates, and pagination. No ADR was needed: the
decisions are either recorded in `CONTEXT.md` or easy to revisit.

## Implementation notes

Decisions made during implementation, within the agreed scope:

- Search price bounds accept 0 to the price cap, so a filter starting at 0 is
  valid; negative bounds are rejected.
- Search text is trimmed before matching, like every other text field.
- Screens submit a `ListingDraft` of raw values; `ListingPhoto.keep` and
  `ListingPhoto.add` express the ordered photo list.
- Profile image cleanup was generalised into a reusable namespace class so
  profile and listing photos share one implementation.
- `ApplicationRuntime.open(Path, Clock)` exists so tests can fix the time.
