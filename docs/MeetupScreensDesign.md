# Meetup Screens Design

Status: agreed through a grill-with-docs interview on 2026-09-26 and
implemented the same day. See "Implementation notes" at the end.
Builds on the implemented [MeetupService Design](MeetupServiceDesign.md) and the
conversation page in [Chat Screens Design](ChatScreensDesign.md). The interview
record is in [the feature log](../logs/2026-09-26-meetup-screens.md).

## Agreed scope

- Meetups are arranged **inside the conversation**, as the MeetupService design
  decided. There is no separate meetup page.
- Screens call MeetupService only; no meetup rules are reimplemented in the UI.
- Only the seller offers meetup times (up to 3 per sale); the buyer books one.
  Either participant can propose moving a booked meetup or cancel it.

## One bar for the latest stage

The conversation keeps **one fixed bar** under its header, and it always shows
the latest stage of the deal, so the message area keeps its space at 960 x 640:

| Stage | The bar shows |
| --- | --- |
| No offer, an offer made, or an offer closed | The offer bar from [Chat Screens Design](ChatScreensDesign.md) |
| Active sale | The meetup state below and its buttons, plus View Sale |
| Completed sale | "Sale completed", with "Met on <time> · <place>" when it had a meetup, plus View Sale |
| Cancelled sale | The offer bar again ("Accepted · Sale Cancelled"), so the buyer can make a new offer on an available listing |

Longer interactions open dialogs.

## Meetup states during an active sale

| Situation | Seller sees | Buyer sees |
| --- | --- | --- |
| No times offered | "No meetup times offered yet", Offer Time | "Waiting for the seller to offer meetup times" |
| Times offered, none booked | "n times offered", View Times (with Withdraw per time), Offer Time while fewer than 3 | "n times offered", Choose Time (with Book per time) |
| Booked, upcoming | Time and place, Propose Move, Cancel Meetup | Same |
| Move proposed by the other participant | The proposed time and place, Accept Move, Reject Move | Same |
| Move proposed by the viewer | The proposed time and place, Withdraw Proposal | Same |
| Booked, end time passed, sale not complete | "The meetup time has passed. Confirm completion on the sale if the handover happened" | Same |

View Sale is available in every active-sale state.

- Booking asks for no extra confirmation. Cancel Meetup confirms: "The sale
  stays active and the seller can offer new times."

## Entering a time

Offer Time and Propose Move open a dialog with a date picker, a start-time
dropdown in 15-minute steps, a length dropdown (15 min, 30 min, 45 min, 1 h,
1.5 h, 2 h, 3 h, 4 h), and a place prefilled with the listing's pickup location.
Each Offer Time submit adds one time. Service refusals (past, beyond 60 days,
overlaps) stay in the dialog.

- The date picker disables days before today and more than 60 days ahead.
- New times default to tomorrow, 12:00, 30 minutes, and the listing's pickup
  location. Propose Move starts from the current meetup's date, start, length,
  and place.
- The start-time list covers the whole day (00:00 to 23:45); the service refuses
  a start that has already passed.

## Elsewhere

- **Sale Details:** the disabled "Arrange Meetup" is removed. A **Meetup** line
  shows the same summary as the bar, and Open Chat is the way to arrange it.
- **My Sales, My Purchases, and reserved My Listings entries:** a one-line
  meetup summary ("Meetup: Fri 2 Oct 2026, 14:00 to 14:30 · Library lobby",
  "2 times offered", "No meetup times yet").
- **Dashboard:** a fifth summary card, "Upcoming meetups", counting the seller's
  scheduled meetups that have not started.
- **Sidebar:** the disabled "Meetups" and "Availability & Meetups" entries are
  removed. This needs the teammate's agreement and is flagged in the PR.

## Format

Meetup times show the weekday with the 24-hour clock: "Fri 2 Oct 2026, 14:00 to
14:30". Service error messages keep their own wording.

## Shared changes to agree with the teammate

- Removing the "Meetups" and "Availability & Meetups" sidebar entries.
- Removing "Arrange Meetup" from Sale Details and adding the Meetup line there.
- Meetup summaries on My Sales, My Purchases, and My Listings entries, and the
  Upcoming meetups card on the Dashboard (PR #9 screens).
- Three constants made public for the screens: `MeetupService.MAX_OFFERED_SLOTS`,
  `MeetupService.MAX_DAYS_AHEAD`, and `MeetupTime.MAX_LOCATION_LENGTH`.

## Verification scope

- `MeetupBarTest`: every meetup state from both sides, the 3-time limit, the
  passed-time boundary (exactly at the end counts as passed), completed sales
  with and without a meetup, which stages replace the offer bar, the one-line
  summaries, and a meetup crossing midnight.
- `MarketplaceUiTest` journeys: the seller offering a time through the dialog;
  the buyer choosing and booking; proposing a move; accepting the other
  person's move; cancelling with confirmation; the Sale Details meetup line; the
  My Sales and reserved My Listings summaries; the Dashboard card; the removed
  sidebar entries. Snapshots of the conversation with the meetup bar at 960 x 640
  and 1100 x 750.

## Deferred work

Meetup history (cancelled meetups and earlier proposals are kept by the service
but not shown), live refresh, and reminders.

## Implementation notes

- Stage choice is `MeetupBar.replacesOfferBar`; a completed sale's meetup comes
  from its `SaleForParticipant`, because a conversation only carries an active
  sale's ID.
- A pending move proposal takes priority over the "time has passed" text, so a
  proposal to a new future time can still be answered after the old time passed.
- Choosing a time closes the list before booking; a refusal (for example, the
  time was taken or has started) shows on the conversation page.
- Two chat-screen journeys asserted the old "Accepted · Sale Active/Completed"
  offer bar text; they now check the meetup bar, as agreed in Q11.
- The limits the screens mirror are shared with the rules instead of copied:
  `MeetupService.MAX_OFFERED_SLOTS`, `MeetupService.MAX_DAYS_AHEAD`, and
  `MeetupTime.MAX_LOCATION_LENGTH` are now public, like `Message.MAX_LENGTH`.
  The time dialog still checks the place before building a `MeetupTime`,
  because the model's constructor would otherwise throw on a blank place.
- Sale Details shows the bar's own text during an active sale (so a pending move
  or a passed time appears there too); lists use the shorter one-line summary.
- Completed sales that had a meetup also show "Met on ..." in lists and on Sale
  Details.
