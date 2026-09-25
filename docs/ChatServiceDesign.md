# ChatService Design

Status: agreed through a grill-with-docs interview on 2026-09-25 and implemented
the same day. See "Implementation notes" at the end for details settled during
implementation and review.

## Agreed scope

- Deliver ChatService with SQLite persistence and JUnit 5 tests. Screens follow
  later; who builds the shared chat screen is settled with the teammate then.
- ChatService covers **both participants**, like the other services.
- The architecture's chat rules stand: one conversation per buyer and listing;
  buyers cannot chat about their own listings; only the buyer and seller can
  read a conversation; conversations stay readable after the listing is sold or
  archived; no attachments, typing indicators, editing, or deleting messages.
- Only one user is logged in per installation at a time, so chat is
  asynchronous: the other participant sees a message the next time they log
  in. No live refresh.

## Starting a conversation

- **Only the buyer starts a conversation**, in one of two ways:
  - sending the first message through "Chat with seller"; the conversation is
    not created until that first message is sent, so abandoned chats leave
    nothing behind;
  - **making an offer**, which creates the buyer's conversation on that listing
    (or reuses it) in the same database transaction as the offer.
- A buyer can start a conversation on an **available or reserved** listing, so
  they can ask about a reserved item in case its sale falls through.
- The offer dialog gains an **optional message**. When the buyer writes one, it
  becomes the conversation's first message; otherwise the conversation starts
  empty with the offer shown.
- Sellers never start conversations, but they can **open an existing one** for
  one of their listings and a given buyer, for example from Incoming Offers or
  from a sale. Every offer creates a conversation, so it always exists there.
- Offers made before this feature get their conversations created by the
  migration.

## Messages

- Plain text of 1-1,000 characters, with surrounding whitespace removed; blank
  messages are refused.
- Messages are shown in the order they were sent (sequence numbers within the
  conversation).
- Either participant can send while the listing is **available or reserved**.
  Once it is **sold or archived**, the conversation is read-only. A cancelled
  sale makes the listing available again, so sending resumes.

## Unread counts

- Opening a conversation marks everything in it as read for the viewer, and
  sending a message marks everything before it as read.
- Unread counts cover **messages and offer events**, for whoever did not cause
  them: a new offer or a withdrawal for the seller; an acceptance or rejection
  for the buyer, including automatic rejections (another offer accepted, the
  listing edited or archived). Each offer counts at most once.
- Each conversation shows its unread count, and the sidebar's Conversations
  link shows the total.

## Conversation list

- **One list** for both roles. Each entry shows the listing title, the other
  participant, whether the viewer is the buyer or the seller, a preview, and the
  unread count.
- **Top group:** conversations where the buyer has a pending offer on the
  listing, or where the two have an active sale on it. **Bottom group:**
  everything else (enquiries, rejected or withdrawn offers, finished sales).
- Within each group, conversations with unread items come first, then by latest
  activity: the latest message, offer made, or offer closed.
- The preview is the newer of the latest message and the latest offer event
  (for example "Offer of S$40.00 accepted").

## Offers and meetups inside a conversation

- Each conversation carries the buyer's **latest offer** on the listing (status
  and amount), so the chat can show it at the top with the actions that apply:
  Withdraw for the buyer, Accept and Reject for the seller. These call
  OfferService, so the rules stay in one place; the listing page keeps its own
  offer controls.
- Each conversation carries the ID of the **active sale** between the two on
  that listing, if any. The screen asks MeetupService for the meetup summary
  with it, so ChatService does not depend on meetup code. Meetups are arranged
  in the chat (see the MeetupService design).
- **No automatic messages.** Sale and meetup events are not written into the
  chat; the conversation shows the live offer, sale, and meetup details beside
  the messages instead.

## Listing deletion

A listing with any conversation cannot be deleted; the seller archives it
instead, as the glossary's **Delete** already says. ListingService adds this
refusal alongside the existing offer-history check.

## Errors

The shared `ServiceException` codes: `VALIDATION` for blank or overlong
messages; `NOT_FOUND`; `PERMISSION` for non-participants, a seller trying to
start a conversation, or a buyer chatting about their own listing; and
`INVALID_STATE` for sending on a sold or archived listing or starting a
conversation on one that is not available or reserved. Messages say what is
wrong and what to do, following the OfferService standard.

## Shared changes to agree with the teammate

- `OfferService` gains a `submitOffer(listingId, amount, message)` form with an
  optional message; the existing `submitOffer(listingId, amount)` keeps working.
  Submitting an offer creates or reuses the conversation.
- The Make Offer dialog (PR #9) can add the optional message box when
  convenient.
- `ListingService.deleteListing` refuses listings with conversations.

## Notes

- **Notifications were dropped** from the release on 2026-09-25. Counting offer
  events as unread in chat covers the most important offer news instead.
- Migration numbering: MeetupService (PR #10) added migration 005, so the chat
  migration is 006.

## Verification scope

JUnit 5 tests with temporary databases covering: starting by message and by
offer, reuse of the existing conversation, starting on available and reserved
listings and refusal on others, buyer-only starting and the own-listing refusal,
seller opening an existing conversation, message length boundaries and blank
text, read-only after sold or archived and reopening after a cancelled sale,
participant-only access, unread counts for messages and each offer event,
opening and sending marking read, list grouping and ordering, previews, latest
offer and active sale on the conversation, the delete refusal, restart
persistence, and migration on fresh and existing databases including the
conversation backfill for existing offers.

## Deferred work

The chat screen, the "Chat with seller" and "Chat with buyer" controls, the offer
bar and meetup panel inside the conversation, and the optional message box in
the Make Offer dialog.

## Implementation notes

- Operations: `messageSeller(listingId, text)` (the buyer's first or later
  message), `sendMessage(conversationId, text)`, `openConversation`,
  `openChatWithSeller(listingId)` (empty when the buyer has no conversation yet),
  `openChatWithBuyer(listingId, buyerId)`, `getConversations`, and
  `getUnreadCount`.
- **Which offer events count for the seller:** a new offer counts while it is
  pending, and a withdrawal counts. Offers the seller accepted or rejected
  themselves do not count for the seller, even if they never opened the
  conversation, because they already acted on them. A buyer's acceptances and
  rejections count for the buyer.
- Sending a message counts as opening the conversation for the sender, so it
  also clears offer events up to that moment.
- Offer events are worked out from each offer's creation and close times
  against the participant's last-opened time, so no event table is needed.
- If a message and an offer event happen at the same moment (an offer with a
  message), the preview shows the message.
- An offer with only whitespace as its message counts as having no message.
- The "latest offer" is the buyer's pending offer when they have one, otherwise
  their newest, so a withdrawal and a new offer in the same millisecond cannot
  show the withdrawn one.
- The migration marks the conversations it creates for existing offers as
  already opened by both participants at the latest offer event, so upgrading
  does not flag every old offer as unread.
- The migration `006_conversations.sql` ran fifth on this branch until it was
  rebased onto PR #10; it now follows `005_meetups.sql` as version 6.
