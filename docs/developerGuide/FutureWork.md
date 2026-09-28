---
  layout: default.md
  title: "Future Work"
  pageNav: 3
---

## Appendix: Future requirements

The current requirements are in [Requirements](Requirements.html).

### Wishlist user stories (future release)

Wishlist functionality is deferred: the sidebar entry and listing action are
disabled and labelled Coming soon. The following are proposed requirements,
not supported workflows or claims about the current data model.

| As a... | I want to... | So that... |
| --- | --- | --- |
| logged-in buyer | save a listing to my wishlist | I can find an item I am considering again without repeating a search. |
| logged-in buyer | view my saved listings and open their details | I can compare items and check their current availability before offering. |
| logged-in buyer | remove a listing from my wishlist | I can keep only the items I am still interested in. |

## Appendix: Planned enhancements

Team size: 2

These four proposals refine existing features and are not implemented in this
release. The deferred wishlist above is a future feature, not an enhancement in
this list.

1. **Show the latest offer's status on conversation cards.** Add a status label
   using the latest offer already carried by `ConversationSummary`, so users can
   inspect it without opening each conversation.
2. **Allow any meetup length from 15 minutes to 4 hours.** Replace the dialog's
   fixed duration choices with a validated duration input within the existing
   `MeetupTime` limits.
3. **Make All categories a selectable search option.** Currently it is the empty
   category prompt restored by Clear Filters. Let users remove just the category
   restriction while keeping their condition and price filters.
4. **Retain whether Filters is expanded across Back navigation.** Extend the
   existing saved search state to restore that panel's open/closed state, while
   continuing to open it automatically when a submitted price is invalid.
