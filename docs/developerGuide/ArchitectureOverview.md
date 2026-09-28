---
  layout: default.md
  title: "Architecture Overview"
  pageNav: 3
---

## Structure

### Repository layout

- `src/main/java/hotshop/Launcher.java`: executable JAR entry point.
- `src/main/java/hotshop/Main.java`: JavaFX lifecycle and marketplace startup.
- `src/main/java/hotshop/ui/`: application shell, feature screens, and shared presentation controls.
- `src/main/resources/hotshop/`: application stylesheet.
- `src/main/java/hotshop/model/`: shared buyer/seller domain models and supporting types.
- `src/test/java/hotshop/`: JUnit 5 model behaviour and boundary tests.
- `config/checkstyle/checkstyle.xml`: executable style checks.
- `docs/`: guides and GitHub Pages source.
- `logs/`: agent interaction records.
- `release/HotShop.jar`: generated distribution, ignored by Git.

### Build structure and implementation scope

This is a single-project, non-modular build. Launcher is separate from the
Application subclass so the bundled JAR can launch JavaFX from the classpath.
The shared model layer, AccountService, ListingService, OfferService,
TransactionService, MeetupService, and ChatService are implemented, including
SQLite persistence, authentication, profile and listing images, buyer listing
search, offers, sale completion and cancellation, sales and purchase history,
meetup slots and bookings, conversations and messages, the sales dashboard
summary, and lifecycle initialization. The account, profile, listing/search,
offer, sale, seller-dashboard, conversation, and meetup screens now call those
services. Wishlists remain deferred; their UI entry points are disabled.
Notifications were dropped from this release on 2026-09-25, so there is no
NotificationService and no Notifications sidebar entry. Users learn about
events from next steps, list ordering, pending-offer counts, and conversation
unread counts instead.

## Architecture overview

### Application entry points

HotShop runs locally in one process. `Main.init` opens `ApplicationRuntime` before
`Main.start` creates `MarketplaceUi`. Feature page classes build JavaFX controls
and handle their actions; there is no separate controller package in the current
implementation.

[![HotShop architecture: JavaFX UI, services, repositories, SQLite, and image storage](../diagrams/architecture_uml.png)](../diagrams/architecture_uml.png)

### Layers and responsibilities

| Layer | Current components | Responsibility |
| --- | --- | --- |
| UI | `MarketplaceUi`, feature pages, `UiPage` | Navigation, forms, presentation validation, and asynchronous feedback. |
| Service | `AccountService`, `ListingService`, `OfferService`, `TransactionService`, `MeetupService`, `ChatService` | Authentication, permissions, business rules, and atomic operations. |
| Repository | `UserRepository`, `ListingRepository`, `OfferRepository`, `TransactionRepository`, `MeetupRepository`, `ChatRepository` | Execute SQL using a supplied connection and map records to models. |
| Database | `Database`, SQLite JDBC, `marketplace.db` | Connections, migrations, commit/rollback, and local persistence. |

Services also use Java models to enforce domain invariants. `ImageStorage` manages
files separately from SQLite; profile and listing image helpers coordinate file
imports and cleanup with saved references. UI image-path resolution goes through
the runtime rather than repositories.

### Runtime initialization and shutdown

`ApplicationRuntime.open` acquires the data-directory lock, migrates the database,
creates separate profile/listing image stores, and constructs the repositories
and six services. All services share one `Database`, `ServiceWorker`, and
`AuthenticatedSession`; time-dependent services share the supplied clock. Startup
recovers managed images before returning. Closing the runtime drains accepted
worker operations before releasing the lock. The session is in memory and starts
logged out on each launch.

