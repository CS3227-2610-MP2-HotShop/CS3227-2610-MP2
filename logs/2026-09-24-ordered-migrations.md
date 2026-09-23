# Agent Interaction Log

## User Prompt

> ok lets add the loop you recommended

This follows the stocktake discussion in
[2026-09-24-teammate-progress-stocktake.md](2026-09-24-teammate-progress-stocktake.md),
where the recommended runner used an explicit ordered migration list and one
transaction per migration. The user accepted both recommendations.

When asked to confirm the test seam, the user chose:

> Yes, these five (Recommended)

## Steps Taken

- Loaded the `tdd` skill and read `Database`, `DatabaseTest`, and
  `ApplicationRuntimeTest`.
- Created branch `ordered-migrations` from `main`; at the user's request it was
  later renamed to `Better-Database-Migration` and pushed.
- Proposed `Database.migrate()` as the test seam, with five tests; the user approved.
- Slice 1: wrote `migrate_newDatabase_appliesEveryMigrationInOrder` with test-only
  migration files. It failed to compile because the injectable constructor did
  not exist (red), then passed after implementing the loop (green).
- Added the remaining four tests. They passed on their first run because the
  slice 1 implementation already required the general loop, so they were not
  strictly red first.
- To show the tests can detect a regression, temporarily changed the loop to
  restart at version 1. `migrate_currentDatabase_changesNothing` and
  `migrate_olderDatabaseWithData_appliesOnlyPendingAndKeepsData` failed; the
  change was then reverted.
- Reviewed the diff and updated the Developer Guide.

## Reasoning Summary

- **Explicit list, not folder scanning.** Listing resources inside a JAR is
  unreliable, and a one-line append per migration is easy to review.
- **Version equals list position.** This removes a second source of truth
  (a separate constant or a parsed filename) that could disagree with the list.
- **One transaction per migration.** A failure in a later migration keeps the
  earlier ones, and the next startup retries only what is missing.
- **Read every script before applying any.** A missing resource fails before
  the database changes at all.
- **Package-private constructor for tests.** Runner tests use tiny SQL files and
  stay independent of the released account schema, while production code keeps
  the existing public constructor and behaviour.
- **Out of scope:** splitting SQL on `;` was left as is and documented as a
  constraint rather than changed, because no migration needs triggers yet.

## Changes Made

- `src/main/java/hotshop/database/Database.java`: replaced the single hardcoded
  migration and `SCHEMA_VERSION` with an ordered `MIGRATIONS` list, a loop over
  pending versions, and a package-private constructor accepting a migration list.
- `src/test/java/hotshop/database/DatabaseTest.java`: added five migration tests
  and helper queries.
- `src/test/resources/db/test-migration/001_first.sql`, `002_second.sql`,
  `002_broken.sql`: test-only migrations.
- `docs/DeveloperGuide.md`: added a Schema migrations section describing the
  runner and how to add a migration safely.
- The User Guide was not changed because the change is not visible to users.

## Verification

- `.\gradlew.bat test --tests hotshop.database.DatabaseTest` before
  implementation: compilation failed as expected (red).
- `.\gradlew.bat test --tests hotshop.database.DatabaseTest --tests hotshop.ApplicationRuntimeTest`
  after implementation: passed; DatabaseTest ran 7 tests with 0 failures.
- The same DatabaseTest run with the loop deliberately broken: 2 expected failures.
- `.\gradlew.bat test checkstyleMain checkstyleTest check build`: passed;
  209 tests, 0 failures.
- `shadowJar` was not run because packaging is unaffected.

## Final Output and Conclusion

The migration runner now upgrades existing databases one version at a time,
which resolves shared-infrastructure gap 1 from the stocktake.

> sorry im supposed to branch out and i forgot to do that can we create a branch called Better-Database-Migration for these changes and push to that branch

The work was already on a branch off `main`, so the branch was renamed to
`Better-Database-Migration`, committed as two commits (stocktake documentation,
then the migration runner), and pushed to `origin`. This changes shared code, so the
teammate should review it before merging. The `;` splitting limitation and
gaps 2 (shared exception) and 3 (image cleanup namespace) remain open.
