---
  layout: default.md
  title: "Requirements"
  pageNav: 3
---

## Appendix: Requirements

### Product scope

**Target user:** people who buy and sell second-hand items with others who share
one HotShop installation (for example, a shared computer), and who hand items
over in person.

**Value proposition:** one desktop app, working offline, to list an item, agree
a price through offers, chat with the other person, arrange a handover time and
place, and record that the sale completed, instead of juggling listings,
messages, and calendars separately. Every user can both buy and sell.

### User stories

Priorities: `* * *` must have, `* *` nice to have, `*` unlikely to have or
deferred.

| Priority | As a... | I want to... | So that... |
| --- | --- | --- | --- |
| `* * *` | seller | create a listing with a price, details, and photos | buyers can find and judge my item |
| `* * *` | seller | edit an available listing | I can correct its details before anyone agrees to buy |
| `* * *` | seller | archive a listing | it leaves search but its history stays visible to the people involved |
| `* *` | seller | delete a listing nobody has offered on | I can remove a mistake completely |
| `* * *` | buyer | search other sellers' available listings by title, category, condition, and price | I find items I want quickly |
| `* * *` | buyer | make an offer, optionally with a message | the seller knows what I am willing to pay |
| `* * *` | buyer | withdraw my pending offer | I can change my mind or offer a different amount |
| `* * *` | seller | see every offer on my listing | I can compare them before deciding |
| `* * *` | seller | accept one offer | the item is reserved for that buyer and other offers are closed |
| `* *` | seller | reject an offer | the buyer knows it was declined |
| `* * *` | buyer or seller | confirm that the handover happened | the sale completes once both of us confirm |
| `* * *` | buyer or seller | cancel a sale before anyone confirms | the listing becomes available again |
| `* *` | buyer or seller | request cancellation after a confirmation, and answer the other person's request | a sale can only be undone by agreement once someone has confirmed |
| `* * *` | seller | see My Sales and a dashboard of offers, sales, and upcoming meetups | I know what needs my attention |
| `* * *` | buyer | see My Purchases with each sale's next step | I know what to do next |
| `* * *` | seller | offer the buyer up to three meetup times and places | the buyer can pick one that suits them |
| `* * *` | buyer | book one of the offered times | we have an agreed handover |
| `* *` | buyer or seller | propose moving a booked meetup, and accept or reject the other person's proposal | we can reschedule without cancelling |
| `* *` | buyer or seller | cancel a booked meetup | we can arrange a new time while the sale stays active |
| `* * *` | buyer | message the seller about a listing | I can ask questions before offering |
| `* * *` | buyer or seller | see my conversations with unread counts, offers and active sales first | I notice new messages and offer news |
| `* *` | seller | open the conversation with a buyer from their offer or sale | I can reply without searching for it |
| `*` | user | be notified when an offer, sale, or meetup changes | I don't have to check each page (not in this release: notifications were dropped for time; next steps, list ordering, and unread counts show these events instead) |

Wishlist user stories for a future release are listed in
[Future Work](FutureWork.html).

### Use cases

For all use cases, the **system** is HotShop and the **actor** is a logged-in
user, unless specified otherwise. Each step that changes data is saved in one
database transaction.

#### Use case: Make an offer and have it accepted

**Actors:** buyer, seller

**Main success scenario:**

1. The buyer opens another seller's available listing and makes an offer,
   optionally with a message.
2. HotShop saves the pending offer and starts, or reuses, the buyer's
   conversation with the seller, adding the message if there is one.
3. The seller opens the listing's Incoming Offers or the conversation, and
   accepts the offer.
4. HotShop accepts the offer, reserves the listing, rejects every other
   pending offer on it, and creates an active sale.
5. HotShop shows the sale's details, and both participants see the sale's next
   step.

   Use case ends.

**Extensions:**

- 1a. The listing is the buyer's own. HotShop refuses the offer. Use case ends.
- 1b. The listing is reserved, sold, or archived. HotShop refuses the offer and
  names the status. Use case ends.
- 1c. The buyer already has a pending offer on the listing. HotShop refuses and
  shows that offer's amount; the buyer may withdraw it and resume at step 1.
- 1d. The amount is outside S$0.01 to S$1,000,000.00, or the message is over
  1,000 characters. HotShop explains the limit. Resume at step 1.
- 2a. The buyer withdraws the offer before the seller responds. The offer is
  closed as withdrawn. Use case ends.
- 3a. The seller rejects the offer. The offer is closed as rejected and the
  listing stays available. Use case ends.
- 3b. The listing was edited or archived after the offer was made. Its pending
  offers were rejected then, so there is nothing to accept. Use case ends.

#### Use case: Arrange a meetup

**Actors:** seller, buyer of an active sale

**Main success scenario:**

1. The seller offers a meetup time: a date, start time, length, and place.
2. HotShop saves the offered time and shows it in the conversation's meetup bar.
3. The buyer chooses one of the offered times and books it.
4. HotShop books the meetup and deletes the sale's other offered times.
5. Both participants see the booked meetup in the conversation, the sale, and
   the seller's listing.

   Use case ends.

**Extensions:**

- 1a. The seller already has three offered times for the sale. Offer Time is no
  longer shown until one is withdrawn or booked.
- 1b. The time is in the past, after the 60th day from today, not 15 minutes to
  4 hours long, or its place is blank or over 200 characters. HotShop explains
  the limit. Resume at step 1.
- 1c. The time overlaps one of the seller's other meetups. HotShop refuses and
  names the clashing meetup. Resume at step 1.
- 3a. The time overlaps another meetup of either participant. HotShop refuses
  the booking and names the clash. Resume at step 3.
- 5a. Either participant proposes moving the meetup to a new time and place.
  - 5a1. The other participant accepts: the meetup moves.
  - 5a2. The other participant rejects, or the proposer withdraws: the meetup
    stays as booked.
- 5b. Either participant cancels the meetup. The sale stays active. Resume at
  step 1.
- 5c. The sale completes or is cancelled. The meetup completes or is cancelled
  with it. Use case ends.
- 5d. The meetup's end time passes. It stays booked, and the next step asks
  both participants to confirm completion.

#### Use case: Cancel a sale after a confirmation

**Actors:** two participants of an active sale, one of whom has confirmed
completion

**Main success scenario:**

1. A participant requests cancellation of the sale.
2. HotShop records the pending request and blocks further confirmations.
3. The other participant accepts the request.
4. HotShop cancels the sale, cancels its meetup, and makes the listing
   available again.

   Use case ends.

**Extensions:**

- 1a. Nobody has confirmed yet. The participant cancels the sale directly
  instead. Resume at step 4.
- 1b. A request is already pending. HotShop refuses a second one. Use case ends.
- 2a. A participant tries to confirm completion. HotShop refuses while the
  request is pending.
- 3a. The other participant rejects the request. The sale continues and earlier
  confirmations stay. Use case ends.
- 3b. The requester withdraws the request. The sale continues. Use case ends.

#### Use case: Start and continue a conversation

**Actors:** buyer, seller

**Main success scenario:**

1. The buyer opens another seller's available or reserved listing and chooses
   Chat with seller.
2. The buyer writes a message and sends it.
3. HotShop starts the conversation and saves the message.
4. The seller sees the conversation with an unread count, opens it, and replies.

   Use case ends.

**Extensions:**

- 1a. The listing is the buyer's own. HotShop does not offer a conversation.
- 1b. The listing is sold or archived and the buyer has no conversation about
  it. HotShop refuses to start one.
- 1c. The buyer starts the conversation by making an offer instead. Resume at
  step 4.
- 2a. The message is blank or over 1,000 characters. HotShop refuses and keeps
  the draft. Resume at step 2.
- 4a. The listing has since been sold or archived. The conversation stays
  readable, but no new messages can be sent.
- 4b. The seller deletes a listing that only had enquiries. Its conversations
  are deleted with it, after the seller is warned how many. Use case ends.

### Non-functional requirements

1. Every change to listings, offers, sales, meetups, and conversations is saved
   in one database transaction, so a failure part-way leaves nothing half done.
2. All data operations run one at a time on a single background worker, so two
   actions never interleave and the window stays responsive while they run.
3. Only one HotShop instance can use a data folder at a time.
4. HotShop works offline; all data stays in a local folder on the computer.
5. Existing data is upgraded automatically, without loss, when a newer version
   adds database tables.
6. Every refused action explains what is wrong and what to do next, using real
   values, and never shows internal errors such as SQL.
7. Every screen stays usable at the minimum window size of 960 x 640.

### Glossary

The project's domain terms are defined in
[CONTEXT.md](https://github.com/CS3227-2610-MP2-HotShop/CS3227-2610-MP2/blob/main/CONTEXT.md).
The terms used most in this guide:

| Term | Meaning |
| --- | --- |
| Active sale | A sale that has been agreed but not yet completed or cancelled. Its listing is reserved. |
| Archive | Withdraw a listing from search while keeping it and its history visible to the people involved. It cannot be reopened. |
| Cancellation request | A participant's proposal to cancel an active sale after the first completion confirmation; the other participant must agree. |
| Completion confirmation | A participant's declaration that the sale is complete. Both must confirm. |
| Conversation | The messages between one buyer and the seller about one listing; at most one per buyer and listing. |
| Delete | Permanently remove a listing with no offer or sale history, together with its enquiry conversations. |
| Enquiry | A conversation about a listing that the buyer never made an offer on. |
| Meetup | The booked time and place where an active sale's buyer and seller hand over the item. |
| Meetup slot (offered time) | A time and place the seller offers the buyer for one sale's handover. |
| Offer | A buyer's proposal to buy a listing at a specified amount. |
| Reschedule proposal (move proposal) | A participant's proposal to move a meetup to one new time and place. |
| Transaction (sale) | An agreed sale, created when the seller accepts an offer. |
| Withdraw | Take back your own pending offer, or your own pending request or proposal. |

