---
  layout: default.md
  title: "Marketplace rules"
  pageNav: 3
---

## Marketplace rules

### Accounts and profiles

The account service enforces these rules:

- Registration requires a unique username, display name, and password. Usernames
  use 3-30 ASCII letters, digits, or underscores and are case-insensitively unique.
  Usernames cannot be changed. Display names contain 1-80 Unicode code points.
- Passwords contain 8-128 Unicode code points, including an ASCII uppercase letter,
  lowercase letter, digit, and a printable ASCII symbol. Any printable ASCII
  non-letter, non-digit symbol counts, including `$`, `+`, `<`, `=`, `>`, `^`,
  `|`, and `~`. Spaces are allowed but do not satisfy the symbol requirement.
  Password case and whitespace are preserved exactly.
- Registration leaves the user logged out. Restarting also logs out; switching
  users requires logout. Password changes require the current password and retain
  the current session. Password recovery and account deletion are not available.
- Display name and optional preferred pickup location save together. A provided
  location contains 1-200 Unicode code points; it can also be cleared. Location
  preferences are private. Other logged-in users receive only display name and
  image through public-profile access.
- Profile images must contain readable JPEG or PNG data, be at most 5 MiB, and
  measure at most 512 pixels wide and 512 pixels high. Rectangular images are
  accepted. Images are not resized or cropped automatically.
- Image changes save separately from text details. HotShop copies imported images
  into its data folder; moving the original photo afterward does not affect the
  saved copy. Replacing or removing an image never deletes the original photo.
  Failed saves preserve the prior image. Failed cleanup is retried at startup.

### Listings and search

The listing service enforces these rules. Every listing action requires login.

- A listing needs a title (1-120 characters), description (1-5,000 characters),
  category, condition, pickup location (1-200 characters), and a price from
  S$0.01 to S$1,000,000. Categories are Electronics, Books, Clothing, Furniture,
  Sports, and Other; conditions are New, Like new, Good, Fair, and Poor.
- A listing may have 0 to 10 photos in a chosen order. Each photo must contain
  readable JPEG or PNG data, be at most 10 MiB, and measure at most 4096 pixels
  wide and high. Photos are not resized. HotShop copies them into its data
  folder, so moving or deleting the original photo does not affect the listing.
  If any photo is rejected, nothing about the listing changes.
- Sellers see all of their own listings, in every status: reserved listings
  first (they are waiting for a handover), then available, sold, and archived,
  each newest first. Each listing shows how many pending offers it has; open the
  listing to see the offers themselves.
- Only the seller can edit, archive, or delete a listing. Only available
  listings can be edited; saving without any change does not count as an edit.
- **Editing a listing rejects all of its pending offers**, because the buyers
  offered on the old details. Saving without any change keeps them.
- Archiving hides an available or sold listing from search but keeps it. You and
  the people involved can still open it from My Listings, My Offers, their
  conversations, and their sales. Archived listings cannot be reopened.
  **Archiving also rejects all pending offers.** A reserved listing cannot be
  archived.
- Deleting permanently removes an available or archived listing and its photos.
  Reserved and sold listings cannot be deleted, and neither can any listing that
  has ever received an offer, even one that was later withdrawn; archive it
  instead. If buyers have only messaged you about the listing, you can still
  delete it: the confirmation says how many conversations will be deleted with
  it, and those conversations disappear for the buyers too.
- Search shows only other sellers' available listings. It can match text in the
  title (ignoring upper and lower case), and filter by one category, one or more
  conditions, and a minimum and/or maximum price (both inclusive). Results are
  sorted newest first, or by price from low to high or high to low.

### Offers

The offer service enforces these rules. Every offer action requires login.

- Buyers can offer from S$0.01 to S$1,000,000.00 on another seller's available
  listing. Offers may be above the asking price. You cannot offer on your own
  listing, or on a listing that is reserved, sold, or archived.
- You can have only one pending offer on each listing. To change the amount,
  withdraw your offer and make a new one. Only pending offers can be withdrawn.
- Buyers see all of their own offers, newest first, with each listing's current
  status. Other buyers never see your offer or its amount.
- Sellers see every offer on their own listing, in Incoming Offers: the offer
  whose sale is active or completed first, then accepted offers whose sale was
  cancelled, then all other offers, newest first.
- Accepting an offer reserves the listing and automatically rejects every other
  pending offer on it. Only pending offers can be accepted or rejected, and only
  by the seller.
- Every refused action explains what went wrong and what to do next, for
  example: "You already have a pending offer of S$40.00 on this listing.
  Withdraw it before making a new one."
- Making an offer also starts your conversation with the seller about that
  listing, or continues it, so the seller can always reply to you. An optional
  message written in the Make Offer dialog becomes a message in that
  conversation.

### Sales and completion

The sale service enforces these rules. Every sale action requires login, and
only the sale's buyer and seller can see or act on it.

- Accepting an offer creates an **active sale**: the item is reserved while you
  meet and hand it over. After the handover, the buyer and seller each confirm
  completion. When both have confirmed, the sale is complete and the listing is
  sold. Completed sales are final.
- Before anyone confirms, either of you can cancel the sale. The listing becomes
  available again; offers that were rejected when the sale was agreed stay
  rejected, so buyers make new offers.
- After one of you has confirmed, cancelling needs agreement: send a
  cancellation request. While it is pending, neither of you can confirm. The
  other person can accept it (the sale is cancelled and the listing released) or
  reject it (the sale continues and earlier confirmations stay). You can
  withdraw your own request. Only one request can be pending at a time.
- Sellers see **My Sales** and buyers see **My Purchases**: one entry per agreed
  sale, with sales waiting for a response to a cancellation request first, then
  other active sales, completed, and cancelled sales, each newest first. A
  listing appears twice in My Sales only if an earlier sale of it was cancelled.
- Each entry says what to do next, such as "Offer meetup times" or "Respond to
  the other participant's cancellation request", and which actions are available.
- The sales dashboard shows your pending offers across all your listings, your
  active and completed sales, and the total value of completed sales. Active
  sales are not included in the total because they can still be cancelled. The
  dashboard also counts your upcoming meetups as a seller: booked meetups that
  have not started yet.

### Meetups

The meetup service enforces these rules. Every meetup action requires login, and
only the sale's buyer and seller can see or act on its meetup.

- Meetups are arranged for an active sale. The seller offers the buyer up to 3
  meetup times, each with a start, an end, and a pickup location (1-200
  characters). A time lasts 15 minutes to 4 hours (chosen from 15, 30, or 45
  minutes, or 1, 1.5, 2, 3, or 4 hours), starts in the future in 15-minute
  steps, and starts on or before the 60th day after today (at any time that day,
  so an evening meetup on day 60 may end on day 61). A sale's offered times
  cannot overlap each other, or any meetup the seller already has.
- Only the buyer books, by choosing one of the offered times. Booking deletes the
  sale's other offered times. Neither of you can book a time that overlaps
  another meetup you already have, whether you are buying or selling in it. If
  the seller offered the same time to two buyers, whoever books first gets it.
- The seller can withdraw an offered time that nobody has booked. Offered times
  cannot be edited, and times that have already started are no longer shown.
- Either of you can propose moving a booked meetup to a new time and place. The
  other person accepts (the meetup moves) or rejects (it stays as booked), and
  you can withdraw your own proposal. Only one proposal can be pending at a time.
  While it is pending, the bar hides **Propose Move** and **Cancel Meetup**.
- Either of you can cancel a booked meetup. The sale stays active, so the seller
  offers new times and the buyer books again.
- Completing the sale completes its meetup, and cancelling the sale cancels it.
  You can confirm completion with or without a meetup. A meetup whose end time
  has passed stays booked, the bar no longer shows **Propose Move** or **Cancel
  Meetup**, and the next step becomes "Did the handover happen? Confirm
  completion".
- Each sale in My Sales and My Purchases shows a meetup summary. Reserved
  listings show offered-time counts or booked dates, times, and place in their
  dedicated meetup area.
- Every refused action explains what went wrong, for example: "The seller
  already has a meetup from Fri 25 Sep, 15:00 to Fri 25 Sep, 15:30. Choose a
  different time." Times in messages use the same 24-hour clock as the screens.

### Conversations and messages

The chat service enforces these rules. Every chat action requires login, and only
a conversation's buyer and seller can read it.

- Each buyer has at most one conversation with the seller about each listing.
  Only the buyer starts it, by sending the first message ("Chat with seller") or
  by making an offer. You can't message yourself about your own listing.
- A buyer can start a conversation about an available or reserved listing, for
  example to ask about a reserved item in case its sale falls through.
- Sellers can open a conversation with any buyer who has started one, for
  example from an offer or a sale, but they can't start one themselves.
- Messages are plain text of 1 to 1,000 characters. They can't be edited or
  deleted, and a message never accepts or changes an offer, even if it says
  "I accept".
- Either of you can send messages while the listing is available or reserved.
  Once it's sold or archived, the conversation stays readable but no new messages
  can be sent, unless the seller deletes a listing that only had enquiries, which
  deletes its conversations too. If a sale is cancelled, the listing is available again and you
  can keep messaging.
- Only one person is logged in to HotShop at a time, so the other person sees
  your message the next time they log in.
- Opening a conversation marks it as read. Each conversation shows how many
  unread items it has, and the total is shown for all your conversations. Unread
  items are the other person's messages, plus offer news you didn't cause: a new
  or withdrawn offer for the seller, and an accepted or rejected offer for the
  buyer (including offers rejected because the listing was edited or archived, or
  another offer was accepted).
- Your conversations are listed together, whether you're buying or selling.
  Conversations with a pending offer or an active sale come first, then the rest.
  Within each group, unread conversations come first, then the most recent.
- Each card on the Conversations page shows a preview of the latest message or
  offer news, such as "Offer of S$40.00 accepted". The buyer's latest offer and
  its status appear in the bar at the top of the conversation itself.
