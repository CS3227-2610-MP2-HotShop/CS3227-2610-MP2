# HotShop Developer Guide

## Setup

Use JDK 25 and the included Gradle 9.1.0 Wrapper. Set JAVA_HOME to your JDK if
necessary. Import the root directory as a Gradle project in your IDE.

```powershell
.\gradlew.bat run
.\gradlew.bat test checkstyleMain checkstyleTest
.\gradlew.bat check build shadowJar
```

Use `./gradlew` on macOS/Linux. Initial dependency resolution requires network access.

## Structure

- `src/main/java/hotshop/Launcher.java`: executable JAR entry point.
- `src/main/java/hotshop/Main.java`: JavaFX lifecycle and marketplace startup.
- `src/main/java/hotshop/ui/`: application shell, feature screens, and shared presentation controls.
- `src/main/resources/hotshop/`: application stylesheet.
- `src/main/java/hotshop/model/`: shared buyer/seller domain models and supporting types.
- `src/test/java/hotshop/model/`: JUnit 5 model behaviour and boundary tests.
- `config/checkstyle/checkstyle.xml`: executable style checks.
- `docs/`: guides and GitHub Pages source.
- `logs/`: agent interaction records.
- `release/HotShop.jar`: generated distribution, ignored by Git.

This is a single-project, non-modular build. Launcher is separate from the
Application subclass so the bundled JAR can launch JavaFX from the classpath.
The shared model layer, AccountService, ListingService, OfferService,
TransactionService, MeetupService, and ChatService are implemented, including
SQLite persistence, authentication, profile and listing images, buyer listing
search, offers, sale completion and cancellation, sales and purchase history,
meetup slots and bookings, conversations and messages, the sales dashboard
summary, and lifecycle initialization. The account, profile, listing/search,
offer, sale, seller-dashboard, and conversation screens now call those services.
Meetup screens are not built yet. Wishlists and notifications remain deferred;
their UI entry points, and the meetup ones, are disabled.

## Dependencies and checks

JavaFX 25.0.2 uses controls and FXML through OpenJFX Gradle plugin 0.1.0.
JUnit Jupiter 5.13.4 is configured with the JUnit Platform launcher.
Checkstyle 12.3.1 enforces mechanical SE-EDU conventions; semantic naming and
clarity still require review. Shadow 9.2.2 bundles runtime dependencies.
Native access is enabled in Gradle launch scripts and the JAR manifest for JavaFX.

Model tests cover validation boundaries, lifecycle transitions, immutable
snapshots, and cancellation permissions/history. For a targeted run, use
`.\gradlew.bat test --tests hotshop.model.TransactionTest`.

## JavaFX UI

[UI Design Scope](UiDesignScope.md) records the confirmed screen and interaction
specification. `MarketplaceUi` installs the scene, grouped sidebar, navigation
history, session reset, and unsaved-change guards. Feature page classes construct
JavaFX controls programmatically; the old welcome-only FXML resource was removed.
No new library is required.

`UiPage` owns loading, duplicate-submission protection, retry, and safe error
display. It uses service futures and `Platform.runLater`; it never blocks the FX
thread on a service future. Page callbacks verify that their page is still current.
All database operations continue through the shared service worker. Screens use
services exclusively, and service permissions remain authoritative. Confirmation
dialogs are closed before business operations start; no database transaction waits
for user input.

`SearchState` separates submitted criteria from draft controls and retains raw
unfinished price text and scroll position across navigation. Sort changes apply
to submitted results; refresh does not submit draft edits. Session changes clear
search and navigation history. A collapsible Filters panel keeps results reachable
at the minimum window size.

### Chat screens

[Chat Screens Design](ChatScreensDesign.md) records the agreed layout and
behaviour. `ChatPages` holds the Conversations list and the entry points
(`withSeller`, `withBuyer`, and opening from the list). `ConversationPage` is one
conversation: header, offer bar, messages, and send box. It keeps its `TextArea`
across reloads, so a draft survives offer actions, and marks itself dirty while
the draft is not blank. It is the only page built with `MarketplaceUi.fixedPage`,
which is not wrapped in the page scroll pane: `UiPage.fillHeight` lets the body
take the remaining height, and only the message list scrolls, with a 200 px
minimum. `UiPage.setHeadingExtras` places controls on the title's row.

`OfferBar` is a pure record that maps the viewer's role, the latest offer, the
listing status, and the sale status to the bar's text and `OfferBar.Action`s, so
its rules are tested without JavaFX (`OfferBarTest`). The actions call
OfferService through `OfferPages.makeOffer` and `OfferPages.accept`, which the
listing page also uses. `ConversationSummary` carries only an active sale's ID,
so for an accepted offer without one the page finds the sale's status in
`getMySales` or `getMyPurchases`, as `SalePages.forOffer` does. The list's two
groups use `ConversationSummary.isAboutOfferOrSale`, which ChatService also
orders by, so the screen never restates that rule.

`UiPage.load` ignores a call while another load runs, so a page that needs two
loads chains the second inside the first's success callback. Listing details
does this when "Chat with seller" must check `getConversations` for a closed
listing; it deliberately avoids `openChatWithSeller`, which marks a conversation
read. `MarketplaceUi` refreshes the sidebar's unread total after every
navigation. The single service worker runs that count after the page load just
queued, so a conversation the new page opens is already counted as read.

`ListingService.getPublicListings(UUID)` requires login and returns only the
selected user's available listings, newest first, with restricted public-profile
data. Missing users return NOT_FOUND; null IDs return VALIDATION. It reuses the
existing seller query, with no schema changes. `ApplicationRuntime` exposes bounded
image-path resolution for presentation; `ImageStorage.validateImage` lets the
picker validate without importing, while saving revalidates and imports as before.

UI tests use JUnit 5 and actual JavaFX controls backed by temporary SQLite data.
They require a graphical desktop; on headless Linux, install Xvfb and JavaFX's GTK
runtime libraries and run `xvfb-run -a ./gradlew test`. CI uses Xvfb. Run the screen
journeys alone with `.\gradlew.bat test --tests hotshop.ui.MarketplaceUiTest`, or
the offer bar rules with `--tests hotshop.ui.OfferBarTest`.
The tests also write scene snapshots to ignored `build/ui-checks/` for visual
inspection. Window defaults are 1100 x 750, minimum 960 x 640, in JavaFX units.

## Shared models

[AccountService Design](AccountServiceDesign.md) records the approved persistent
account requirements and implementation scope.

[Buyer Model Design](BuyerModelDesign.md) records the approved requirements;
[CONTEXT.md](../CONTEXT.md) defines the domain vocabulary.

- `User` is an immutable identity/profile with no credentials or assigned roles.
  Optional profile image and preferred location are null at construction and
  exposed through `Optional`. Username spelling is preserved; use
  `getNormalizedUsername()` for case-insensitive uniqueness checks.
- `ListingDetails` is an immutable value containing the sale terms. `Listing`
  validates and replaces details/images together. `update` returns true only
  for actual changes, signalling that a service must reject pending offers.
- `ListingImage` belongs to its containing listing; it has a relative filename
  and display order. Its containing listing supplies the listing ID for future
  persistence. Supply images in contiguous zero-based display order. Lists are
  defensively copied and read-only. Relative names use forward slashes and
  exclude empty, dot, traversal, drive, backslash, and control-character segments.
  Models do not read image files or verify their existence.
- `Offer` validates availability and ownership at creation, then stores IDs.
  Amounts are fixed; `accept`, `reject`, and `withdraw` close a pending offer.
- `Transaction` is created from an accepted offer and its reserved listing.
  It copies the agreed amount and listing title, description, and condition.
  It stores IDs rather than retaining the mutable listing/offer. It manages
  confirmations, direct cancellation, and cancellation requests.
- `CancellationRequest` is an immutable snapshot managed through its owning
  transaction. Request IDs are required when resolving requests, so a stale
  response cannot accidentally resolve a newer request. Returned history is
  immutable and retains all resolved requests.

IDs are UUIDs generated at creation. `User.restore` provides validated restoration
and immutable profile replacement with the original UUID; `Listing.restore` does
the same for listings, including status and timestamps, and `Offer.restore` for
offers (`closedAt` is present exactly when the offer is no longer pending).
`Transaction.restore` takes a `Transaction.Snapshot` (one component per saved
column plus the request history) and rejects histories the live model could
never produce; `CancellationRequest.restore` restores one request.
`Transaction.cancel` records who cancelled and when; an accepted request records
its requester as the canceller. `hasConfirmation`, `hasConfirmed`, and
`getPendingCancellation` answer the state questions services and screens ask.
Offer amounts share the listing price cap. Amounts are
positive `long` values in SGD cents. Text is stripped of surrounding whitespace;
length limits count Unicode code points. Missing required references throw
`NullPointerException`, invalid values/actors throw `IllegalArgumentException`,
and forbidden lifecycle operations throw `IllegalStateException`. Failed
operations leave model state unchanged.

`Listing` has a fixed `createdAt` and an `updatedAt` that advances only when
`update` reports an actual change; status changes never touch it. Listing prices
are capped at `ListingDetails.MAX_PRICE_CENTS` (S$1,000,000). `isDeletable` is
true only for available or archived listings.

Listing creation and updates, offer creation and closing (`accept`, `reject`,
`withdraw`), transaction creation, and timestamped operations take an explicit `Instant`.
Pass times from the service clock; operations must not precede the transaction's
last recorded event. Tests use fixed times without sleeping.

### Service integration responsibilities

These mutable models are intended for serial access, consistent with the
architecture's single background worker. They do not authenticate callers,
query other records, persist changes, or provide database transactions.

Future services must obtain actor IDs from the authenticated session and:

1. Authorize listing and offer operations and recheck listing availability.
2. Enforce username uniqueness and at most one pending offer per buyer/listing.
3. Accept an offer, reserve its listing, reject competing offers, and create a
   transaction atomically. Construct `Transaction` after acceptance/reservation.
4. Reject pending offers after an actual listing edit or archival. Done by
   ListingService through `PendingOffers`; `deleteListing` refuses listings with
   offer history or any conversation.
5. Mark the listing sold after transaction completion, or release it after
   direct/mutually agreed cancellation, in the same persistence transaction.
   Done by TransactionService, which also closes the sale's meetup through `SaleMeetups`.
6. Preserve listing/offer/request history and exclude archived listings from browsing.

`Transaction` itself enforces participant membership, one pending cancellation
request, which participant may resolve it, and blocked completion while pending.
An actor ID passed by a model caller is still not proof of authentication.

## Account service and local persistence

`ApplicationRuntime.open(Path)` owns the application data-directory lock, schema
migration, image recovery, shared service worker, and AccountService. Close it to
drain queued work before releasing the lock. `Main.init` opens the runtime off the
JavaFX thread; `Main.stop` closes it. Initialization failures show an error instead
of resetting storage. Runtime data defaults to `${user.home}/.hotshop`; override it
for development with `-Dhotshop.dataDir=/absolute/path` before `-jar`.

SQLite JDBC 3.53.4.0 is the only new library. The bundled driver supplies SQLite;
no server or separately installed SQLite executable is needed. Tests enable native
access just like the launcher. The runtime directory contains `marketplace.db`,
`application.lock`, `images/profiles/`, and `images/listings/`.

`Database.executeTransaction` opens a connection with foreign keys and a 5000 ms busy
timeout, then commits or rolls back the callback. Pass that same connection to
every repository participating in a business operation. `UserRepository` maps
profiles and separate `PasswordHash` records; it does not authorize callers.
Schema version 1 lives in `src/main/resources/db/migration/001_accounts.sql`.
Version 2 (`002_listings.sql`) adds `listings` and `listing_images` and rebuilds
`image_cleanup` with a `namespace` column, tagging existing rows as `profiles`.
Version 3 (`003_offers.sql`) adds `offers`, with a partial unique index allowing
one pending offer per buyer and listing, and `transactions`, with a partial
unique index allowing one active sale per listing. Version 4
(`004_sale_completion.sql`) adds `cancelled_at` and `cancelled_by` to
`transactions` and a `cancellation_requests` table with a partial unique index
allowing one pending request per sale. `Transaction`'s internal `lastEventAt` is
not stored; `restore` derives it from the saved times. Requests are saved with an
upsert and read back in the order they were made, so the latest is always last.
Version 5 (`005_meetups.sql`) adds `meetup_slots`, `meetups`, and
`meetup_reschedule_proposals`, with partial unique indexes allowing one scheduled
meetup per sale and one pending move proposal per meetup.
Version 6 (`006_conversations.sql`) adds `conversations` (unique per listing and
buyer, with each participant's read position and last-opened time) and
`messages` (unique per conversation and sequence number). It also creates a
conversation for every buyer and listing that already had offers, marked as
opened by both at the latest offer event so old offers don't show as unread.
Before the Chat-Service branch was rebased onto MeetupService, this migration
ran as version 5; delete any local database created from that branch then.

### Schema migrations

`Database.migrate` runs at every startup. It reads the database's highest
recorded version from `schema_migrations`, then applies each later migration
in order, each in its own transaction together with its version record. A
failure therefore leaves the database at the last fully applied version, and
the next startup retries from there. A database whose version is higher than
the application knows is refused rather than downgraded.

To change the schema:

1. Add `src/main/resources/db/migration/NNN_description.sql`, numbered one
   above the latest file.
2. Append its resource path to `Database.MIGRATIONS`. A migration's version is
   its one-based position in that list, so only ever append.
3. Add tests that open a database created at the previous version and check
   that existing data survives the upgrade.

Never edit or reorder a migration that has been merged, and never reset an
existing database; change the schema with a new migration instead. If both
teammates add a migration on separate branches, whoever merges second
renumbers theirs. Statements are split on `;`, so migrations must not contain
triggers or semicolons inside string literals or comments.

`DatabaseTest` supplies its own migration files from
`src/test/resources/db/test-migration/` through a package-private constructor,
so runner tests do not depend on the released schema.

AccountService returns `CompletableFuture` results. Its public operations are
`register`, `login`, `logout`, `getCurrentUserId`, `getOwnProfile`, `getPublicProfile`,
`updateProfile`, `changePassword`, `replaceProfileImage`, and `removeProfileImage`.
`recoverImages` is a lifecycle maintenance operation. Access the service through
`ApplicationRuntime.getAccounts()`. `ServiceException.getCode()` distinguishes
validation, authentication, session, duplicate username, not-found, storage,
permission, and invalid-state failures for every service; a joined future wraps
the exception in `CompletionException`.

Future services must share the same `ServiceWorker`, `AuthenticatedSession`, and
`Database` when wired into ApplicationRuntime. Resolve acting identity inside the
queued operation, not when a controller submits it. The session contains only
the UUID; profile reads use the latest persisted record. PublicProfile includes
only ID, display name, and optional image filename. Private pickup preferences
and credential records must never be passed to another user's profile screen.
Callbacks may execute on the service worker; marshal UI updates using
`Platform.runLater`. Do not block that worker by joining another service call or
closing the runtime from a completion callback. Closing belongs to the lifecycle
owner. Cancelling a returned future does not cancel an already queued mutation.

Passwords use PBKDF2-HMAC-SHA256, a fresh 16-byte salt, 600,000 iterations, and a
256-bit derived key, with algorithm/work-factor metadata persisted separately.
Password strings are not normalized or stripped. No password/hash is returned
through a profile. A local database is not protection against someone who can
modify the application's data files; there is no remote authentication server.

ImageStorage accepts per-feature limits and validates actual JPEG/PNG contents,
dimensions, and bounded bytes before writing a generated filename. `ManagedImages`
owns one image namespace (a folder, its limits, a "still referenced" query, and
its rows in the durable `image_cleanup` queue). It imports files, queues retired
files inside the caller's transaction, and recovers orphans. `ProfileImages` and
`ListingService` each configure one (`profiles` and `listings`), so recovery in
one namespace never processes or deletes the other's files. Cleanup rechecks
database references and retries failed removals at startup. A new image feature
should add a namespace to the `image_cleanup` CHECK constraint in a migration
and construct its own `ManagedImages`. SQL fixtures/triggers
in tests inject persistence failures at the external database boundary; assertions
check service-visible results and managed-file lifecycle.

## Listing service

[ListingService Design](ListingServiceDesign.md) records the approved requirements.
**ListingService includes buyer search.** Buyer screens should call
`searchListings` and `getListing` rather than implementing a second search.

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

Screens pass a `ListingDraft` of raw form values; invalid values become
`VALIDATION` failures rather than exceptions from the model. Photos are a complete
ordered `List<ListingPhoto>` of `ListingPhoto.keep(filename)` and
`ListingPhoto.add(path)` entries (0 to 10), validated against
`ImageStorage.LISTING_LIMITS` (JPEG/PNG, 10 MiB, 4096 px per side). Imports happen
before the database write; on any failure, recovery removes the unsaved copies.
Results are `ListingWithSeller`: a detached `Listing` plus the seller's
`PublicProfile`. Non-owners get `PERMISSION`; a status that forbids the action
gets `INVALID_STATE`. Both codes are part of the shared `ServiceException`.

`ListingSearch` holds optional filters (title text, category, conditions, price
bounds of 0 to the price cap) and a `ListingSort`. SQL selects other sellers'
available listings; `ListingSearch` then filters and sorts them in Java because
SQLite's case-insensitive matching covers ASCII letters only. There is no
pagination.

`ApplicationRuntime.open(Path, Clock)` lets tests fix the time; timestamps are
truncated to milliseconds to match what SQLite stores. Some listing tests set a
listing's status in SQL to reach reserved or sold states directly; offer and sale
tests use the real services.

## Offer service

[OfferService Design](OfferServiceDesign.md) records the approved requirements.
**OfferService includes the buyer operations** (submit, withdraw, my offers).
Buyer screens should call them rather than implementing them again.

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

`acceptOffer` does everything in one database transaction: accept the offer,
reserve the listing, reject the other pending offers through `PendingOffers`, and
save a `Transaction`. It returns `AcceptedOffer` (offer, reserved listing, sale
ID). `OfferWithListing` and `OfferWithBuyer` carry the sale status for accepted
offers, so screens can show "Accepted, sale cancelled" without a new offer status.

`ServiceSupport` holds plumbing shared by ListingService and OfferService: the
truncated current time, transaction error mapping, public-profile lookup, status
words, and price formatting (`S$40.00`). `ServiceException` has factories for
its codes. Refusal messages say what is wrong and what to do, using real values,
and never mention SQL. Tests assert the code and key values in selected
messages, not exact wording.

Submitting an offer also starts the buyer's conversation about the listing, or
reuses it, in the same database transaction (through `Conversations`), so the
seller always has a conversation with everyone who offered.

Hooks for later services: NotificationService adds notifications inside the
accept and reject transactions. After TransactionService cancels a sale, the
released listing can receive offers again.

## Chat service

[ChatService Design](ChatServiceDesign.md) records the approved requirements.
**ChatService covers both participants.** Buyer and seller chat screens should
call it rather than implementing the rules again.

Access it through `ApplicationRuntime.getChats()`. Every operation requires
login. Only the buyer starts a conversation, by messaging the seller or by
making an offer; only the buyer and seller can read it.

| Operation | Who | Rule |
| --- | --- | --- |
| `messageSeller(listingId, text)` | Buyer | Another seller's available or reserved listing. Starts the conversation or adds to it. |
| `sendMessage(conversationId, text)` | Either participant | While the listing is available or reserved; sold and archived listings are read-only. |
| `openConversation(conversationId)` | Either participant | Returns every message and marks them read. |
| `openChatWithSeller(listingId)` | Buyer | The existing conversation, opened, or empty when there is none yet. |
| `openChatWithBuyer(listingId, buyerId)` | The listing's seller | An existing conversation only; sellers never start one. |
| `getConversations()` | Anyone | `ConversationSummary` list: pending offer or active sale first, then the rest; unread first, then latest activity. |
| `getUnreadCount()` | Anyone | Total unread items, for the sidebar. |

Messages are 1-1,000 characters after trimming. A conversation's unread count
is the other participant's messages after the viewer's read position plus
offer events since the viewer last opened it: new and withdrawn offers for the
seller, accepted and rejected ones for the buyer, each offer counted once. These
are worked out from the offers' own times, so no event table is needed.
`ConversationSummary` also carries the buyer's latest offer, the active sale
between the two (so the screen can ask MeetupService for the meetup), a preview,
and whether sending is allowed. `Conversations` is the package-private helper
that ChatService and OfferService use to start conversations and add messages
inside their own transactions. There are no automatic messages.

## Transaction service

[TransactionService Design](TransactionServiceDesign.md) records the approved
requirements, including which lists are intended for which page.
**TransactionService includes the buyer's purchase list.** Buyer screens should
call `getMyPurchases` rather than implementing it again.

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

Every change loads the sale, checks the participant and status, applies it
through the `Transaction` model, and saves the sale and any listing change in
one database transaction (`applyToActiveSale`). `SaleProgress` turns a sale's
state into the viewer's `NextStep` (with display text), `SaleAction`s, and list
position, using the same model queries the rules use, so screens never offer an
action that would be refused. Completing a sale completes its scheduled meetup,
and cancelling it cancels the meetup, in the same transaction. Hook for later:
NotificationService notifies the other participant inside these transactions.

## Meetup service

[MeetupService Design](MeetupServiceDesign.md) records the approved requirements.
**MeetupService includes the buyer's operations** (booking, moving, cancelling).
Buyer screens should call them rather than implementing them again.

Access it through `ApplicationRuntime.getMeetups()`. Every operation requires
login and only the sale's participants may act. Times are `MeetupTime` values:
15 minutes to 4 hours long, a 1-200 character location, starting in the future
and at most 60 days ahead.

| Operation | Rule |
| --- | --- |
| `offerSlot(saleId, start, end, location)` | Seller of an active sale with no booked meetup. At most 3 future slots; they cannot overlap each other or the seller's scheduled meetups. |
| `withdrawSlot(slotId)` | Seller of the slot's sale. |
| `bookSlot(slotId)` | Buyer of the sale, for a future slot. Neither participant may have another scheduled meetup at an overlapping time, in any role. The sale's other slots are deleted. |
| `proposeMove(meetupId, start, end, location)` | Either participant, when no move is pending. Overlaps are checked as for booking. |
| `acceptMove` / `rejectMove(meetupId)` | The participant who did not propose. Accepting rechecks overlaps and moves the meetup. |
| `withdrawMove(meetupId)` | The participant who proposed. |
| `cancelMeetup(meetupId)` | Either participant. The sale stays active and the seller can offer new slots. |
| `getMeetupSummary(saleId)` | `MeetupSummary`: future offered slots and the current meetup (scheduled, else the completed one). Cancelled meetups are kept in the database as history but not returned. |

The same summary is carried by `SaleForParticipant`, by reserved entries in
`OwnListing`, and counted in `SalesDashboard.upcomingMeetups` (the seller's
scheduled meetups that have not started). `SaleMeetups` is the package-private
helper that loads summaries and closes a sale's meetup for TransactionService.
`SaleProgress` puts cancellation requests and the viewer's own confirmation ahead
of meetup steps; a meetup counts as past once its end time has passed.
Hook for later: NotificationService notifies the other participant of offered
slots, bookings, moves, and cancellations.

The full test suite takes more than ten minutes on a typical laptop, mostly
because each test account's password is hashed with 600,000 PBKDF2 iterations.
Run the targeted checks below while developing and the full suite before
committing.

Targeted development checks:

```powershell
.\gradlew.bat test --tests hotshop.service.AccountServiceTest
.\gradlew.bat test --tests hotshop.service.ProfileImageTest
.\gradlew.bat test --tests "hotshop.service.Listing*"
.\gradlew.bat test --tests hotshop.service.OfferServiceTest
.\gradlew.bat test --tests hotshop.service.TransactionServiceTest
.\gradlew.bat test --tests hotshop.service.MeetupServiceTest --tests "hotshop.model.Meetup*"
.\gradlew.bat test --tests hotshop.service.ChatServiceTest --tests hotshop.model.ConversationTest --tests hotshop.model.MessageTest
.\gradlew.bat test --tests hotshop.storage.ImageStorageTest
.\gradlew.bat test --tests hotshop.ApplicationRuntimeTest --tests hotshop.database.DatabaseTest
```

## Packaging and CI

`shadowJar` writes `release/HotShop.jar`. Build separately for each target OS and
architecture because JavaFX native libraries are platform-specific.
The JAR requires a separately installed Java 25 runtime.

GitHub Actions runs tests, Checkstyle, check, build, and shadowJar on pull
requests and pushes to main/master, and uploads a Linux JAR artifact.
The workflow also supports manual dispatch.

## GitHub Pages

After pushing, open repository Settings > Pages, select **Deploy from a branch**,
select the branch containing these files and **/docs**, and save.
GitHub publishes the site after its Pages build completes.
Hosting has not been enabled by this local setup.

## Engineering skills

Agent skill configuration lives in [docs/agents](agents/). It defines the
[team GitHub issue tracker](agents/issue-tracker.md),
[triage labels](agents/triage-labels.md), and
[domain documentation rules](agents/domain.md). `AGENTS.md` directs agents
to read these files when needed.

Edit these configuration files directly to adjust the workflow. Re-run
`setup-matt-pocock-skills` when switching trackers or restarting setup.
Domain documentation uses a root `CONTEXT.md` and `docs/adr/`, created by
`domain-modeling` as terminology and decisions are resolved.

The skills are installed once in `.agents/skills/`, where Codex reads them.
Claude Code reads `.claude/skills/` instead, so link that path to the same
folder rather than copying it. On Windows (no administrator rights needed):

```powershell
New-Item -ItemType Directory -Force .claude
cmd /c mklink /J .claude\skills .agents\skills
Add-Content .git\info\exclude ".claude/skills"
```

On macOS/Linux, use `ln -s ../.agents/skills .claude/skills`. The link is
excluded locally rather than committed because the repository does not enable
Git symlinks. Restart Claude Code if the skills do not appear.

## Acknowledgements

- Matt Pocock's engineering skills: agent configuration adapted from the
  installed `setup-matt-pocock-skills` templates in
  `.agents/skills/setup-matt-pocock-skills/`.

- [OpenJFX Gradle plugin](https://github.com/openjfx/javafx-gradle-plugin): dependency configuration.
- [SE-EDU Java conventions](https://se-education.org/guides/conventions/java/intermediate.html):
  basis for the Checkstyle rules.
- [Gradle](https://docs.gradle.org/9.1.0/release-notes.html): wrapper and Java 25 build support.
- [Shadow](https://gradleup.com/shadow/): executable dependency bundling.
- [Xerial SQLite JDBC](https://github.com/xerial/sqlite-jdbc): bundled SQLite driver.
- [OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
  and [Java security providers](https://docs.oracle.com/en/java/javase/25/security/oracle-providers.html):
  password-storage implementation guidance. The user-selected composition policy
  is a project requirement, not a claim of NIST compliance.
