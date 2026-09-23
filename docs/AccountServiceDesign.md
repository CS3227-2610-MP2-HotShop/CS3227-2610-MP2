# AccountService Design

Status: approved by the user through the implement skill. AccountService and its
persistence, image-storage, and startup infrastructure are implemented. Account
screens remain deferred. Verification results are recorded in the implementation log.

## Agreed scope

- Deliver a working AccountService with SQLite persistence and tests. JavaFX
  screens follow separately.
- SQLite will eventually store relevant persistent data for other domain objects
  too. Persistence infrastructure must support that expansion.
- One application instance has at most one logged-in user. Application restart
  begins logged out; switching accounts requires logout first.
- Users can edit their own display name, profile image, and preferred pickup
  location. Usernames are immutable.
- Users can view other users' display names and profile images. Preferred pickup
  location remains private to its owner as a default for their workflows.
- Credentials remain separate from the shared User model, as required by the
  existing architecture.
- ListingService owns seller-listing queries. Implement them in its milestone;
  decide public visibility of reserved and sold listings then. Archived listings
  remain excluded from browsing under the existing architecture.
- Registration saves the account but does not log the new user in. Reject
  registration while a user is logged in.
- Logout succeeds harmlessly when already logged out; cover this at service-test
  level. The eventual UI must not expose logout while nobody is logged in.
- Include changing passwords with verification of the current password.
  Defer forgotten-password recovery and account deletion.
- Support importing, replacing, and removing profile images, without image
  editing or cropping. Copy validated images into managed application storage
  using generated filenames; SQLite stores relative references. Preserve the
  old image until replacement succeeds and clean up unsuccessful imports.
- Reuse image-storage infrastructure for future listing images. File-picker and
  avatar presentation remain deferred with the UI.
- Require login to view profiles. Other-user profile retrieval returns an
  immutable restricted result containing user ID, display name, and optional
  image, without preferred pickup location or credentials. Own-profile retrieval
  includes the owner's preferred pickup location.
- Passwords require at least eight characters, including a symbol, a number,
  an uppercase letter, and a lowercase letter. This replaces the earlier proposed
  15-character minimum without composition requirements.
- Successful password changes keep the current user logged in. Failed changes
  preserve credentials and session.
- Profile images must not exceed 512 pixels in either dimension. There is no
  automatic resizing or cropping in this milestone.
- Registration requires username, password, and display name only. Users add
  optional images and pickup locations through profile editing afterward.
  Preserve existing username validation and enforce case-insensitive uniqueness.
- Unknown usernames and wrong passwords share an invalid-credentials error.
  Duplicate registration reports that the username is unavailable. Storage
  failures are distinct and preserve the previous saved profile and session.
- Failure to delete an old image after a successful replacement does not undo
  the update. Record the cleanup problem for retry.
- Save display name and preferred pickup location together after validating
  both; allow clearing the optional location. Image changes are separate actions.
- Passwords contain 8-128 Unicode code points and require at least one ASCII
  uppercase letter, lowercase letter, digit, and punctuation character. Other
  characters and spaces are allowed; spaces do not satisfy the punctuation rule.
  Preserve passwords exactly, without trimming or Unicode normalization.
- Accept actual JPEG and PNG images, at most 5 MiB (5 * 1024 * 1024 bytes),
  with width and height each at most 512 pixels. Rectangular images are allowed.
- Wire persistence into application startup: initialize or migrate the database,
  initialize managed image storage and a logged-out session, and release resources
  on shutdown. Retain the welcome screen until account UI work.

## Implementation direction

Use shared database connection, migration, and transaction infrastructure with
feature-specific repositories. Begin with account tables and UserRepository;
future repositories should use the same database infrastructure and be able to
participate in a shared transaction. Do not create unused repositories or tables
for every model in this milestone.

User restoration and profile replacement preserve UUID identity through validated
User.restore; the creation constructor continues to generate new UUIDs.

## Service contract

Names below describe operations; final Java signatures will follow repository
conventions. All mutations of an existing account use authenticated session
identity, never an arbitrary acting-user ID provided by a controller.

| Operation | Contract |
| --- | --- |
| Register | Logged out only; atomically save identity/profile and credentials; return the created User and remain logged out. |
| Login | Logged out only; verify username/password and establish the session only on success. |
| Logout | Clear session identity; already logged out is a successful no-op. |
| Current-user identity | Return an optional UUID; absent when logged out. Other services use a required-identity check for protected actions. |
| Own profile | Require login; return the current persisted User, including private preferred pickup location. |
| Public profile by user ID | Require login; return only ID, display name, and optional image. Unknown IDs produce a not-found error. |
| Update own profile | Require login; validate and save display name and optional location together, preserving UUID and username. |
| Change password | Require login and correct current password; validate and save the new password hash; retain session. |
| Replace own profile image | Require login; validate/copy the image and commit its reference before cleaning the prior image. |
| Remove own profile image | Require login; clear the saved reference before deleting the managed file. No image is a successful no-op. |

Use distinct service failures for validation, authentication, session state,
unavailable usernames, missing profiles, and storage problems. Do not expose SQL
or password material through user-facing errors. No new password-history policy
is introduced: setting the same valid password is allowed after verification.

## Infrastructure and consistency

- Introduce a shared application lifecycle owner, database connection/transaction
  support, versioned SQL migrations, UserRepository, separate credential storage
  representation, an authenticated session, and managed image storage. Keep
  responsibilities small without a generic repository framework.
- Use Xerial SQLite JDBC as specified by the architecture. Select and verify a
  compatible version during implementation; do not alter Java or Gradle versions.
- Keep one application data directory and acquire the architecture's exclusive
  application lock. Enable SQLite foreign keys and bounded lock waits on each
  connection. Preserve existing databases and fail visibly on startup errors.
- Serialize account operations through the shared background worker, including
  session changes. UI updates stay on the JavaFX thread. A queued operation must
  not resolve identity outside this serialization boundary.
- Store session identity rather than a stale cached User. Preserve immutable
  User identity through validated restoration and profile replacement.
- Password storage: Java's built-in PBKDF2-HMAC-SHA256 with a unique
  random salt, 600,000 iterations, and stored algorithm/work-factor metadata.
  This avoids a separate hashing dependency; Argon2id remains an alternative
  requiring a library. Never persist plaintext passwords.
- Repositories participating in one business operation share a connection and
  transaction. Unique normalized usernames are enforced by SQLite as well as
  service behavior; credentials/profile registration succeeds or fails together.
- Inspect image contents and dimensions before full decoding where possible.
  Generated storage names prevent filename collisions and source-path reuse.
  Image-format and size limits are supplied for profile use so future listing
  images can use their own policy.
- Files and SQLite cannot commit atomically. On failed imports, remove new
  unreferenced files; on committed replacement/removal, retire the old file.
  Persist pending cleanup work for retry at startup. Recover crash-created
  orphans only within the managed profile-image area and never delete referenced
  files or original user-selected source files.

Password-storage references consulted during the interview:
[OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
and [Java 25 security providers](https://docs.oracle.com/en/java/javase/25/security/oracle-providers.html).

## Verification and documentation scope

Add JUnit 5 tests using temporary databases and image directories for:

- Registration validation, case-insensitive duplicates, atomic failure, and reopen persistence.
- Login success/failure, logged-in restrictions, repeated logout, and logged-out restarts.
- Password boundaries and each missing character category independently; exact
  whitespace/case handling, password changes, and old/new password authentication.
- Own-profile permissions, restricted public-profile output, unknown users,
  optional-field clearing, identity preservation, and failure rollback.
- Actual image format validation, corrupt data, byte/dimension boundaries,
  rectangular images, replacement/removal, rollback, and cleanup retry.
- Migration from fresh/existing databases, startup/shutdown, and exclusive data-directory access.

Run the Gradle Wrapper's test, checkstyleMain, checkstyleTest, check, build, and
shadowJar tasks. Verify packaged SQLite availability and application startup as
the environment permits; report any verification that cannot be performed.

Update UserGuide to accurately describe the current UI and image/password rules
without inventing account screens. Update DeveloperGuide and architecture with
implemented service contracts, setup, persistence, dependencies, and limitations.
Record the implementation workflow under logs/.

## Deferred work

Account screens, seller-listing queries, persistence for other feature models,
password recovery, account deletion, cropping, and image editing remain outside
this milestone. No ADR is necessary for the routine boundaries established here;
SQLite and the single-instance architecture are already specified by the project.
