## Problem Statement

The meetup integration adds information beneath listing-card footers without
allocating enough space to read it comfortably. The conversation list makes the
listing title open chat, which suggests the wrong destination. Sale Details
places all information against the left edge despite available horizontal space.
The navigation column's white background ends with its links, leaving beige
space below it.

## Solution

Give every My Listings card a consistent taller layout with a dedicated meetup
area. Make conversation cards open chat while preserving separate View Listing
and participant-profile buttons. Organize Sale Details into two columns on wider
windows and stacked sections on narrower windows. Extend the navigation column's
white background to the bottom of the window while retaining scrolling.

## User Stories

1. As a seller, I want space dedicated to meetup information on my listing cards, so that it does not crowd the existing details.
2. As a seller, I want every My Listings card to have the same taller height, so that mixed listing states form aligned rows.
3. As a seller, I want listing status and pending-offer counts retained above the meetup area, so that I can still manage my listings at a glance.
4. As a seller, I want the meetup date and time on separate lines, so that I can quickly read when the handover happens.
5. As a seller, I want both dates visible for an overnight meetup, so that I do not mistake which day it ends.
6. As a seller, I want up to two lines for the meetup place, so that ordinary pickup locations remain readable.
7. As a seller, I want oversized place names to end with an ellipsis, so that cards remain aligned without implying that the shortened text is complete.
8. As a participant, I want full meetup details available in the sale and conversation, so that I can read a place shortened on a listing card.
9. As a seller, I want an unbooked sale to show its offered-time count or "No meetup times yet", so that I know whether scheduling has started.
10. As a seller, I want cards without a meetup summary to reserve the same space, so that they align with cards that have meetups.
11. As a buyer, I want search and public-profile listing cards to stay compact, so that seller-only scheduling space does not expand browsing results.
12. As a user, I want listing images and two-line titles to retain their existing space, so that adding meetup information does not squeeze them.
13. As a participant, I want to click the body of a conversation card to open its conversation, so that the main destination is intuitive.
14. As a participant, I want a separate View Listing button on that card, so that I can inspect the item without entering chat.
15. As a participant, I want a separate other-participant profile button, so that I can view that person's public profile directly.
16. As a participant, I want either secondary button to open only its own destination, so that one click does not also navigate to chat.
17. As a participant, I want the same card behavior in Offers and sales and Other conversations, so that navigation is consistent.
18. As a keyboard user, I want to open conversations, listings, and profiles through distinct keyboard-accessible targets, so that these actions do not require a mouse.
19. As a participant, I want conversation previews, roles, unread indicators, and activity times retained, so that navigation improvements preserve the list's useful information.
20. As a sale participant, I want item, price, and participant information grouped on the left of Sale Details, so that the purchase context is easy to scan.
21. As a sale participant, I want status, meetup information, and available actions grouped on the right, so that progress and next steps are easy to find.
22. As a user with a narrow window, I want Sale Details sections to stack vertically, so that all information and actions remain accessible.
23. As a user, I want the navigation column to remain white from top to bottom, so that it reads as one continuous part of the interface.
24. As a user with a short window, I want navigation to remain scrollable, so that links remain reachable while the background covers the full height.
25. As a user, I want the warm, shadow-free theme retained, so that these improvements remain visually consistent with the application.

## Implementation Decisions

- This is a presentation refinement. Retain existing service permissions,
  persistence, business rules, navigation destinations, and sale/meetup actions.
  No schema changes, dependencies, or new service APIs are required.
- Extend the existing ListingCards presentation rather than creating a separate
  listing-card implementation. My Listings uses one uniform taller height;
  search and public-profile cards retain 240 x 304 layout units.
- Keep cards 240 units wide, the image frame 208 x 130, and the title area 52
  units high with its existing two-line behavior. Preserve image aspect ratios.
- Derive the taller seller-card height from the content and padding required,
  including overnight dates. Do not stretch cards to fill unused window height.
  Resizing changes the number of columns, not the card dimensions.
- Place the meetup area below the existing status/pending-offer footer. Show
  separate date and time lines, then up to two place lines. Keep full dates and
  times visible, including both dates for overnight meetups. Truncate only an
  oversized place with an ellipsis. Reserve the same area for unbooked-state
  text, or leave it empty when there is no meetup summary.
- Reuse the current meetup summary data. Use the existing weekday and 24-hour
  formatting conventions; this spec changes layout, not meetup semantics.
- In ChatPages, treat the conversation card body, including its plain listing
  title, as the conversation target. Provide separate View Listing and
  participant-profile buttons. Their actions must not also activate the card.
  Preserve distinct focus and keyboard activation for all destinations.
- Apply conversation-card navigation to both existing groups. Keep grouping,
  ordering, previews, badges, read behavior, and refresh behavior unchanged.
- In SalePages, group item, price, and participant details on the left and sale
  status, meetup, and available actions on the right. Stack the groups when
  horizontal space is insufficient. Preserve all existing information, action
  availability, confirmations, and buyer/seller behavior.
- Choose exact seller-card height and the column breakpoint during layout
  verification; no numerical values were agreed for these. Support the existing
  960 x 640 minimum and 1100 x 750 default windows.
- In the marketplace shell and shared styles, make the navigation background
  cover its full allocated height, including space below the links. Keep the
  current links and navigation scrolling.
- Retain the warm light palette, subtle borders, shadow-free controls, and
  visible keyboard focus. Update user and developer documentation when the
  implementation actually changes.

## Testing Decisions

- Prefer one existing high-level seam: MarketplaceUiTest's real JavaFX controls
  backed by temporary application data and real services. No new testing layer
  is proposed. This is a proposed test approach for later implementation, not a
  claim that the user separately approved a testing architecture.
- Test observable navigation, rendered text, geometry, focus/activation, and
  action availability. Do not mirror private layout implementation. Underlying
  label text and containment alone do not prove that users can read it.
- Extend the existing listing-card and meetup journeys with mixed available,
  reserved, sold, and archived seller cards; compare uniform heights and row
  alignment with and without meetup summaries. Check that buyer cards remain
  compact and retain images, titles, and buyer metadata.
- Cover no offered times, one and several offered times, booked meetups,
  same-day and overnight times, short places, places that fit two lines, and
  oversized places. Verify full rendered date/time visibility and place-only
  truncation. Verify that full details remain accessible through existing sale
  and conversation routes.
- Exercise conversation-card body/title activation, View Listing, and profile
  buttons independently in both groups. Assert the actual destination after
  each action and verify keyboard access without double navigation.
- Exercise Sale Details for buyers and sellers, active and closed sales, and
  applicable confirmation/cancellation states. Inspect two-column and stacked
  layouts with long content so information and actions remain reachable.
- Check sidebar background coverage when links are shorter than the window
  and when navigation requires scrolling.
- Reuse snapshot generation and visually inspect minimum, default, and wider
  windows. Include mixed seller cards, long meetup details, conversation cards,
  Sale Details, and full-height navigation. Prior art includes existing image,
  title, card-reflow, dialog-theme, and meetup tests.
- During implementation, run the Gradle Wrapper test and Checkstyle tasks,
  followed by check and build. No implementation tests have been run for this
  specification-only task.

## Out of Scope

- Implementing any of these changes during this specification task.
- New marketplace capabilities, domain models, service rules, database changes,
  or changes to meetup booking/rescheduling/cancellation behavior.
- New navigation pages, conversation grouping changes, live refresh, reminders,
  meetup history, wishlists, or notifications.
- Redesigning unrelated screens or changing compact buyer listing-card sizing.
- Changing the existing minimum window size, image treatment, theme, or title
  truncation rules.

## Further Notes

- Status: specified for later implementation. The user explicitly requested
  saving the design now and implementing it later. The ready-for-agent label
  describes specification readiness, not an instruction to start work now.
- Q1-Q5 were agreed during the design interview. The final request was:
  "$to-spec Save all these details for now. Implementation will be done later."
- This deliberately revises the previous universal 304-unit card height,
  one-line meetup summary, and listing-title-opens-chat decisions for the
  affected screens. Existing design documents describe current behavior until
  implementation; the UI design scope records these planned revisions.
- No new domain terminology or hard-to-reverse architectural decision was
  introduced, so no glossary update or ADR is needed.
