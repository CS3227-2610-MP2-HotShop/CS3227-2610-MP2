# Agent Interaction Log

## User Prompt

> lets do a stocktake on what my friend has done as well so i can have a clear understanding of what im working with in terms of the core models hes done and what is lacking, as well as his progress

## Steps Taken

- Read the Git history and merged pull requests (#1 to #4) on the team repository.
- Read every model class, the account service layer, `Database`,
  `ApplicationRuntime`, both repositories, the account migration, `Main`, and the
  User Guide.
- Ran the unit tests and Checkstyle on the current `main` branch.
- Compared the teammate's models with what the seller features from the
  previous stocktake will need.

## Reasoning Summary

The stocktake is read-only. It judges the code against the seller features
the user owns, so a gap here means "the seller work will need this", not
"the teammate made a mistake". Where a gap sits in shared code (migrations,
the shared exception type, image cleanup), it is flagged for agreement with
the teammate before either person changes it, as HotShop_Architecture.md
section 13 requires for models, schema, and service interfaces.

## Findings

### Progress (all merged to `main`)

| Date | PR | Work |
| --- | --- | --- |
| 2026-09-16 | #1 | Gradle/JavaFX scaffold, Checkstyle, CI, AGENTS.md |
| 2026-09-22 | #2 | Matt Pocock skills, architecture spec |
| 2026-09-22 | #3 | Shared domain models and model tests |
| 2026-09-23 | #4 | SQLite persistence, AccountService, profile images, startup lifecycle |

No UI beyond the welcome screen. No GitHub issues have been opened.

### Models: what works well

- Validation is centralised (`ModelValidation`) and every model rejects bad
  input without leaving partial state.
- `Listing` status transitions match the architecture state diagram.
  `update` returns whether the sale terms actually changed, which is the
  signal a service needs for rejecting pending offers.
- `Offer` refuses self-offers and offers on non-available listings.
- `Transaction` fully enforces two-sided confirmation and the cancellation
  request rules, with immutable request history.

### Models: gaps for the seller features

1. `Listing`, `Offer`, and `Transaction` generate a random ID in the field
   initialiser and have no `restore` factory, so they cannot be loaded from
   the database. `Offer` and `Transaction` also require the live `Listing`
   object in their constructors. Only `User.restore` exists.
2. No timestamps on `Listing` or `Offer`. Browse "sort by newest", the incoming
   offers list, and the sales dashboard all need a creation time.
3. `Transaction.cancel(actorId)` takes no time and records neither who cancelled
   nor when, unlike the other transaction operations. Sales history may need both.
4. No models for meetups, notifications, chat, or counteroffers.

### Shared infrastructure: gaps

1. `Database.migrate` hardcodes `001_accounts.sql` with `SCHEMA_VERSION = 1` and
   only migrates from version 0. The Developer Guide calls it an ordered runner,
   but adding `002_listings.sql` requires changing it to apply each pending version.
   It also splits SQL on `;`, which will break any future trigger definitions.
2. `AccountException` is used for shared concerns: `ServiceWorker` raises it when
   the app has closed and `AuthenticatedSession.requireUserId` raises it for
   "Login is required". Every future service will surface account exceptions for
   session errors unless a shared exception type is agreed.
3. The `image_cleanup` table stores bare filenames with no namespace, and
   `ProfileImages.recover` processes every pending row against the profile folder.
   Listing images are not stored anywhere yet, so nothing is affected today. If
   listing images later reuse this table as-is, profile recovery would drop
   listing cleanup entries without deleting the files. Listing images need a
   namespace column or their own queue.
4. `ProfileImages` (import, save, schedule old file, recover) is profile-specific.
   Listing images need the same pattern for up to ten images, so copying it would
   duplicate logic; generalising it is a shared change.
5. `ApplicationRuntime` wires only AccountService and the profile image folder.
   New services must be added there with the same worker, session, and database.

## Changes Made

- `logs/2026-09-24-teammate-progress-stocktake.md`: this log. No source or
  documentation files changed.

## Verification

- `.\gradlew.bat test checkstyleMain checkstyleTest` on `main` (9cfab24):
  BUILD SUCCESSFUL. 204 tests ran, 0 failed, 0 skipped (counted from
  `build/test-results/test`).
- `check`, `build`, and `shadowJar` were not run because nothing changed.
- The gaps listed above come from reading the source. None were reproduced
  with a failing test.

## Follow-up Questions

> Under shared infrastucture can you explain to me what the limitations are for only one migration can ever run
>
> for shared exceptions do you mean that multiple error paths from different domains throw the same exception, and if so are you suggesting to create more exception types.
>
> for image cleanup trap, can you help me to confirm first, does listing images share the table and what the recommended approach is

- Migrations: explained that a `002` file would be skipped for existing
  version-1 databases, never loaded for fresh ones, and that bumping
  `SCHEMA_VERSION` alone would record version 2 without the new tables.
  Recommended an ordered loop applying each pending version.
- Exceptions: clarified that the issue is shared components (`AuthenticatedSession`,
  `ServiceWorker`) throwing the account-specific type. Recommended one shared
  exception with a code enum rather than more exception types; to be agreed
  with the teammate because it renames a public API.
- Image cleanup: searched all uses of `image_cleanup`, `ImageCleanupRepository`,
  and `ImageStorage`, and read `ImageStorage.delete`. **Correction:** listing
  images do not share the table today, because no listing images are stored
  anywhere. The risk applies only if the table is reused as-is; tracing that
  case confirmed the listing file would be orphaned because deleting an absent
  file is a no-op and the queue row is then removed. Recommended a `namespace`
  column added by a new migration, which depends on the migration fix.
- No files other than this log changed.

> i dont know enough about sqlite to make decisions for 1. so lets dive deeper into how does it work, how does it store data, and is that data transferrable between me and my teammate

- Checked for a local `~/.hotshop` data folder to show real files; none exists
  because the app has not been run on this machine.
- Explained SQLite as an embedded library storing everything in one
  cross-platform file, the four current tables, and that Git shares code and
  migrations but not each person's database or images.
- Advised copying the whole data folder only while HotShop is closed, never
  committing it, and preferring a sample-data script.
- Walked through the concrete upgrade scenario showing why the current runner
  fails for an existing version-1 database, and reduced decision 1 to two
  sub-choices: an explicit migration list (recommended) and one transaction
  per migration (recommended).

> what do the migrations do and how does it work

- Explained migrations as numbered, ordered SQL files that change database
  structure, tracked per database file in `schema_migrations`.
- Walked through the current `Database.migrate()` step by step from the source,
  then showed how a looping runner would bring new, version-1, and version-2
  databases to the same version.
- Listed the safety rules: never edit a shared migration, add rather than
  reset, avoid duplicate version numbers across branches, and run each
  migration in a transaction.

> i see so just to confirm the migrations creates tables for the application, then data gets load in, having a newer migration file than what the app recognises might break because theres differences in the schema.

- Confirmed that migrations build structure and the app creates data afterwards.
- Clarified that the mismatch is between the database file's version and the
  app's version: an older database should be upgraded (the case the current
  runner fails), while a newer database is refused on purpose.

## Final Output and Conclusion

Stocktake presented to the user. Recommended next step: raise the shared
infrastructure gaps with the teammate, then start the ListingService interview.
