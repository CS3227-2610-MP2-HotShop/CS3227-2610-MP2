# MeetupService Design

Status: agreed through a grill-with-docs interview on 2026-09-24 and implemented
the same day. See "Implementation notes" at the end for details settled during
implementation and review.

## Agreed scope

- Deliver MeetupService with SQLite persistence and JUnit 5 tests. Screens follow later.
- **This milestone includes the buyer operations** (booking, cancelling, and
  proposing moves) as well as the seller's, subject to the teammate's agreement.
  Buyer screens should call these operations rather than implementing them again.
- Meetups belong to **active sales** only. There is at most one scheduled meetup
  per sale at a time.
- Finished meetups (completed or cancelled) are **kept** as history, because
  purchase history shows meetup details.

## Meetup slots

- Slots are **offered by the seller to one buyer for one sale**, not published
  as general availability. Only that sale's buyer sees them.
- Each slot has a start, an end, and a pickup location (1-200 characters). It
  lasts **15 minutes to 4 hours**, starts in the future, and starts at most
  **60 days** ahead.
- **Up to 3** unbooked slots per sale at once; slots offered for the same sale
  cannot overlap.
- The seller cannot offer a slot that overlaps one of their own scheduled meetups.
- The same time may be offered to different buyers; whoever books first gets it,
  and the overlap rule stops the second booking.
- Slots are not editable. The seller can withdraw any unbooked slot.
- When the buyer books a slot, the sale's **other slots are deleted**. They are
  also deleted when the sale completes or is cancelled.
- Slots whose time has passed cannot be booked and are not shown.

## Meetups

- **Booking:** only the buyer books, choosing one of the sale's offered future
  slots. Neither participant may already have another scheduled meetup at an
  overlapping time, in any role (buyer or seller).
- **Moving:** either participant proposes **one** new time and place (the same
  length, future, and 60-day rules as slots). The other participant accepts (the
  meetup moves) or rejects (it stays); the proposer may withdraw. One pending
  proposal per meetup. Accepting rechecks the overlap rule for both participants.
- **Cancelling:** either participant may cancel a scheduled meetup. The sale
  stays active; any pending proposal is withdrawn; the seller offers new slots
  and the buyer books again.
- **Outcome follows the sale:** completing the sale marks its scheduled meetup
  completed; cancelling the sale cancels it and withdraws any pending proposal.
  A meetup whose time has passed without the sale completing stays scheduled
  and is shown as past.
- Confirming completion is allowed with or without a meetup; meetups plan the
  handover but do not lock the sale.

## Next steps

These replace the sale's "Meet to hand over the item, then confirm completion"
step. Cancellation-request steps keep priority, as now.

| Situation | Buyer | Seller |
| --- | --- | --- |
| No slots offered, nothing booked | Waiting for the seller to offer meetup times | Offer meetup times |
| Slots offered, nothing booked | Choose one of the offered times | Waiting for the buyer to choose a time |
| Meetup booked, upcoming | Meet on (date, time) at (place), then confirm completion | Same |
| Meetup time has passed | Did the handover happen? Confirm completion | Same |
| Pending move proposal from the other participant | Respond to the proposal to move the meetup | Same |
| Pending move proposal from the viewer | Waiting for them to respond to your proposed time | Same |

Once the viewer has confirmed completion, "Waiting for the other participant to
confirm" applies as before.

## Where meetups appear

Services carry the data. My Listings and My Sales are separate pages, as the
marketplace screens (PR #9) built them and as agreed for TransactionService.

- **Meetups are arranged in the chat** (decided 2026-09-25): offering, booking,
  moving, and cancelling happen inside the conversation for that sale (one
  buyer, one listing). Meetup screens therefore wait for ChatService.
- **Active sale entries** in My Sales and My Purchases show the meetup summary
  only: offered slots, or the booked meetup with any pending move proposal.
- **Reserved listings** in My Listings show the same summary only.
- There is no separate slot, availability, or meetup page, so the disabled
  "Meetups" and "Availability & Meetups" sidebar entries from PR #9 should be
  removed, and the sale page's "Arrange Meetup" control should open the chat,
  once chat exists. Both changes need the teammate's agreement.
- The **sales dashboard** gains **upcoming meetups**: the seller's scheduled
  meetups that have not started.

## Errors

The shared `ServiceException` codes: `VALIDATION` for bad times, lengths,
locations, or too many slots; `NOT_FOUND`; `PERMISSION` for the wrong
participant (for example, a seller booking, or a non-participant); and
`INVALID_STATE` for inactive sales, taken or past slots, overlaps, and proposals
that are not pending. Messages say what is wrong and what to do, with real
values, following the OfferService standard.

## Hooks and notes for later services

- TransactionService calls MeetupService's completion and cancellation steps
  inside its own database transactions.
- ~~NotificationService: notify the other participant when slots are offered, a
  meetup is booked, moved, or cancelled, and when a proposal is made or
  answered.~~ Dropped on 2026-09-25 with notifications; the sale's next step
  shows these instead.
- **Note for ChatService (user suggestion, not yet decided):** list conversations
  with a pending or accepted offer at the top, and general enquiries without
  offers below.

## Verification scope

JUnit 5 tests with temporary databases covering: slot length, start, 60-day,
location, and three-slot boundaries; overlap between a sale's slots and with the
seller's meetups; buyer-only booking; other slots deleted on booking; overlap
across roles at booking and on accepted moves; first-booking-wins for a shared
time; proposal accept, reject, withdraw, and one-pending rule; cancelling and
rebooking; meetup completion and cancellation with the sale; past slots and past
meetups; next steps from both sides; dashboard upcoming count; restart
persistence; migration on fresh and version-4 databases.

## Deferred work

Meetup screens and the chat integration. (Notifications were also listed here
until they were dropped on 2026-09-25.)

## Implementation notes

- The booked-meetup step text is generic ("Meet at the booked time and place,
  then confirm completion"). Screens show it next to the summary's time and
  place rather than the step text carrying them.
- A move proposal checks overlaps for both participants when it is made, as
  well as when it is accepted, so an impossible time is refused early.
- The seller cannot offer new slots while a meetup is booked; cancel it first.
- `getMeetupSummary(saleId)` lets a screen load one sale's summary directly.
- The summary shows the scheduled meetup, or the completed one. Cancelled
  meetups are kept in the database as history but not shown.
- A meetup counts as past once its **end** time passes (for the next step); the
  dashboard's upcoming count uses its **start** time. A meetup in progress is
  neither upcoming nor past.
- Withdrawing a slot does not require the sale to be active. Slots of a closed
  sale are already deleted, so this changes nothing in practice.
