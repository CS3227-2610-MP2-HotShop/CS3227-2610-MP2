# Agent Interaction Log

## User Prompt

> lets drop notification

Earlier the same day the user said they and their teammate were reconsidering
notifications for lack of time. The assistant had explained the impact and
suggested checking the rubric first; this prompt records the decision to drop
the feature.

## Steps Taken

- Created branch `Drop-Notifications` from `origin/main` (MeetupService merged,
  ChatService still open as PR #11).
- Searched the repository for every mention of notifications: five Javadoc
  hook comments, one disabled sidebar entry, and ten documents.
- Checked that no test refers to the Notifications sidebar entry.
- Removed the Javadoc hooks from `OfferService` (accept, reject),
  `TransactionService` (confirm, cancel), and `MeetupService`.
- Removed the disabled "Notifications (Coming soon)" entry from the sidebar in
  `MarketplaceUi`.
- Updated `HotShop_Architecture.md`: removed NotificationService, the
  `notifications` table, NotificationRepository, the "creates notifications"
  step when an offer is accepted, and notifications from shared screens, and
  said in the status paragraph that the feature was dropped and how users see
  events instead.
- Updated `docs/DeveloperGuide.md` (status paragraph and the three "hook for
  later" notes) and `docs/UserGuide.md` (says HotShop sends no notifications and
  where to look instead).
- Marked the NotificationService hooks and deferred-work entries in the Offer,
  Transaction, and Meetup design documents as dropped on 2026-09-25, keeping the
  original text struck through as a record.
- Updated the teammate's `docs/UiDesignScope.md` sidebar list and added an
  implementation note about the removed entry.
- Added a small commit on `Chat-Service` (PR #11) changing its "notifications
  are under review" notes to "dropped".

## Reasoning Summary

- Nothing in the code depended on NotificationService, so dropping it only
  removes promises: comments, one disabled button, and plans in the docs.
- The architecture document is the living specification, so notifications were
  removed from it; the design documents are records of past decisions, so their
  hooks were marked as dropped instead of deleted.
- Users still see events through each sale's next step and list ordering, the
  pending-offer counts, and, once PR #11 merges, chat unread counts that
  include offer events.
- `docs/BuyerModelDesign.md` was left alone: it only records that notifications
  were out of scope for that milestone, which is still true.
- The work went on its own branch from `main` rather than into PR #11, so the
  chat PR stays about chat; only chat's own notes about notifications changed
  there.

## Changes Made

- `src/main/java/hotshop/service/OfferService.java`, `TransactionService.java`,
  `MeetupService.java`: removed NotificationService hook comments.
- `src/main/java/hotshop/ui/MarketplaceUi.java`: removed the disabled
  Notifications sidebar entry.
- `HotShop_Architecture.md`, `docs/DeveloperGuide.md`, `docs/UserGuide.md`,
  `docs/OfferServiceDesign.md`, `docs/TransactionServiceDesign.md`,
  `docs/MeetupServiceDesign.md`, `docs/UiDesignScope.md`: as above.
- On `Chat-Service`: `docs/ChatServiceDesign.md` and
  `logs/2026-09-25-chat-service.md` notes.
- This log.

## Verification

- Searched for the Notifications sidebar entry in the tests; nothing referred
  to it.
- `.\gradlew.bat test checkstyleMain checkstyleTest check build shadowJar`
  passed on `Drop-Notifications`: 503 tests, 0 failures, 0 skipped. (This
  branch is based on `main`, which does not yet include the chat tests.)
- The user asked whether tests were still running because windows kept
  appearing; the assistant explained that `MarketplaceUiTest` opens real JavaFX
  windows during the run and reported progress until it finished.
- The chat note commit on `Chat-Service` changed documentation only, so no
  Gradle tasks were rerun for it.

## Final Output and Conclusion

Notifications are dropped from the release. The teammate should agree to the
removed sidebar entry and the edit to `docs/UiDesignScope.md`, since both are
theirs. In-app notifications were one of the user's assigned features, so the
user should make sure the team's feature list and any grading notes reflect the
change.
