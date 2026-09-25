# Chat Screens Design

Status: agreed through a grill-with-docs interview on 2026-09-26 and
implemented the same day. See "Implementation notes" at the end for details
settled during implementation. The interview record is in
[the feature log](../logs/2026-09-26-chat-screens.md).
Builds on the implemented [ChatService Design](ChatServiceDesign.md) and the
screen conventions in [UI Design Scope](UiDesignScope.md).

## Agreed scope

- The user builds the whole chat UI for **both participants**, because the
  conversation is one shared page. This needs the teammate's agreement, since
  the earlier split gave them the buyer side of chat.
- **Chat only.** The meetup panel, "Arrange Meetup", and the "Meetups" and
  "Availability & Meetups" sidebar entries stay disabled until the meetup
  milestone.
- Screens call ChatService and OfferService only; no rules are reimplemented in
  the UI.

## Pages

- **Conversations** (sidebar): the list of the current user's conversations.
- **Conversation**: one conversation's messages, its offer bar, and the send
  box. Opened from the list or an entry point; Back returns to where it was
  opened.
- Both use the existing full-page navigation. There is no split view.

## Entry points

| Where | Who | Control |
| --- | --- | --- |
| Sidebar | Everyone | "Conversations (n)", where n is the unread total, updated after every navigation |
| Listing details | Non-owners | "Chat with seller" |
| Incoming Offers row | The listing's seller | "Chat with buyer" |
| Sale Details | Both participants | "Open Chat" |

"Chat with seller" opens the Conversation page even when no conversation
exists yet. It then shows an empty history, a hint that sending starts the
conversation, and the send box; the first send creates it.

## Conversations list

- One card per conversation: the listing title (opens the conversation), a
  profile link to the other participant, a Buying or Selling badge, the
  preview, an unread badge when there are unread items, and the last-activity
  time.
- Two headed groups in the service's order: **Offers and sales** (pending offer
  or active sale), then **Other conversations**.
- Empty state: "No conversations yet" with Search Listings.
- A **Refresh** button reloads the list.

## Conversation page

- **Header**: the listing title, a profile link to the other participant, a
  Buying or Selling badge, the listing's status badge, and View Listing.
- **Offer bar** below the header: the buyer's latest offer and its status, with
  the actions that apply. Actions call OfferService and reuse the existing Make
  Offer and Accept Offer dialogs.

  | Latest offer | Buyer sees | Seller sees |
  | --- | --- | --- |
  | Pending | Amount, Withdraw Offer | Amount, Accept Offer, Reject Offer |
  | Accepted | "Accepted · Sale <status>", View Sale | Same |
  | Rejected or withdrawn | Status; Make Offer if the listing is available | Status |
  | None | Make Offer if the listing is available | "No offer yet" |

  Make Offer, Withdraw, and Reject reload the conversation. Accept goes to Sale
  Details, as it does from Incoming Offers.
- **Messages** oldest first, each with sender ("You" or the display name), sent
  time, and text. Your own messages are right-aligned and tinted. The messages
  have **their own scrolling area**; the header, offer bar, and send box stay
  fixed. This is the only page that does not scroll as a whole. The messages
  scroll to the newest when the page opens and after each send.
- **Fitting the minimum window (960 x 640):** the fixed parts are compact. The
  badges, profile link, and View Listing share one row beside the title, the
  offer bar is one row, and the send box is 3 lines tall. The message area takes
  the remaining height, at least 200 px (about 250 px at minimum size). This is
  checked with snapshots at 960 x 640 and 1100 x 750.
- **Send box**: a multi-line box with Send and a "0 / 1,000" counter. Send is
  disabled while the box is blank; Ctrl+Enter sends and Enter adds a new line.
  A refused send shows the service's message beside the box and keeps the text;
  a successful one clears it. When sending is not allowed (sold or archived
  listing), the box stays visible but disabled with the reason.
- Unsent text triggers the existing unsaved-changes guard on leaving.
- Opening the page marks the conversation read, through the service.

"Chat with seller" on a sold or archived listing opens an existing conversation
read-only. With no conversation, the button is disabled with the hint
"Conversations can only be started about available or reserved listings."

## Make Offer dialog

Gains an optional message (up to 1,000 characters; blank means none), sent with
the offer through `submitOffer(listingId, amount, message)`.

## Refresh

Only one user is logged in at a time, so the other participant's new messages
appear after they log out and you log back in. Pages load fresh each time they
open; there is no live refresh.

The window keeps its agreed sizes: 1100 x 750 at start and a 960 x 640
minimum.

## Shared changes to agree with the teammate

- Building the buyer's side of chat here, which the earlier split gave to the
  teammate.
- Enabling "Chat with seller" on listing details and "Conversations" in the
  sidebar, and adding "Chat with buyer" to Incoming Offers and "Open Chat" to
  Sale Details (PR #9 screens).
- The optional message box in the Make Offer dialog.

## Verification scope

- `OfferBarTest`: every row of the offer bar table from both sides, plus
  listings that are reserved or sold.
- `MarketplaceUiTest` journeys: the first message starting a conversation; the
  sidebar count and list, opening, and replying; Chat with buyer, accepting from
  the offer bar, and Open Chat from the sale; an offer with a message and a
  withdrawal from the offer bar; a sold listing's read-only conversation with its
  completed sale; Chat with seller disabled on an archived listing; the draft
  guard. Snapshots of both pages at 960 x 640 and 1100 x 750.

## Deferred work

The meetup panel, "Arrange Meetup", the "Meetups" and "Availability & Meetups"
sidebar entries, Upcoming meetups on the Dashboard, and live refresh.

## Implementation notes

- The sidebar link reads "Conversations (n)" only when n is above 0, and plain
  "Conversations" otherwise.
- Accepting from the offer bar opens Sale Details as a new page, so Back returns
  to the conversation. Incoming Offers still replaces its page, as before.
- "Chat with seller" checks for an existing conversation only when the listing is
  sold or archived; on an available or reserved listing it is always enabled.
- A buyer reaches a sold or archived listing they never asked about only through
  a link, so that journey test opens the listing page directly.
- The offer bar keeps the amount on every row ("Offer of S$40.00 · Accepted ·
  Sale Active"), and a buyer with no offer also sees "No offer yet" beside Make
  Offer, so the bar always says what it is about.
- The two list groups use `ConversationSummary.isAboutOfferOrSale`, the same rule
  ChatService orders by, instead of a copy in the screen.
- The message area also jumps to the newest message when its content grows, for
  example when the window is resized, not only on opening and sending.
- The Make Offer dialog checks the message length before submitting, as it already
  does for the amount; the service remains the authority.
