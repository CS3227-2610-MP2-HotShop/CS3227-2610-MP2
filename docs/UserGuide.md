# HotShop User Guide

## Requirements

Install Java 25. A graphical desktop is required.

## Start HotShop

From the project directory on Windows:

```powershell
.\gradlew.bat run
```

On macOS/Linux, use `./gradlew run` (run `chmod +x gradlew` first if needed).
The first build downloads dependencies and requires internet access.

HotShop opens the **Log in** page in an 1100 by 750 window. The minimum window
size is 960 by 640 (JavaFX layout units). Pages and the sidebar scroll when
needed; listing grids wrap to fewer columns in narrower windows.

Startup creates or opens a local database and image folder at `.hotshop` in your
home directory. Accounts, listings, offers, and images in that folder survive
application restarts. Only one HotShop instance may use the same folder at a time.
Opening an older HotShop data folder upgrades it automatically without removing
existing accounts.

If startup fails, HotShop displays an error and exits without resetting existing
data. Check folder permissions and close another running HotShop instance before
retrying. Close HotShop before backing up its entire data folder, including images.

## Run the packaged application

Build on the operating system and architecture where the JAR will run:

```powershell
.\gradlew.bat shadowJar
java -jar release/HotShop.jar
```

The JAR includes JavaFX libraries for the build machine's platform, but does not
include Java itself. The CI artifact targets Linux.

## Accounts and navigation

Choose **Create an account** to register with a username, display name, password,
and password confirmation. Registration returns to login with your username
filled in. Password visibility checkboxes let you inspect what you typed.
After login, the Search page opens. Every user can both buy and sell; the sidebar's
Buying and Selling headings organise pages and do not switch account roles.

Use **My Profile** to save your display name and private preferred pickup location.
Your username cannot be changed. **Replace Image** and **Remove Image** save
separately from the text fields. **Change Password** requires your current password
and matching new passwords; it keeps you logged in. Clicking another participant's
name/photo opens their public profile with their available listings, newest first.
Your own identity link opens My Profile instead.

Use **Back** to return through pages and **Log out** to end the session. Leaving a
listing editor or profile with unsaved text changes asks whether to discard them;
the same protection applies to logout and closing the window. Wait for an active
operation to finish before navigating or closing.

## Search and listings

Search starts with guidance and no results. Enter a title query and press Enter
or **Search**. An empty query searches all eligible listings from other sellers.
Open **Filters** for category, conditions, and minimum/maximum SGD price. Query
and filter changes take effect only on submission. Sorting reorders the last
submitted results immediately. **Clear Filters** does not submit; **Refresh**
reruns the last submitted search. Back navigation retains draft fields and the
submitted search, sorting, and scroll position. A new login resets search.

For example, search for `desk`, choose Furniture, and set a maximum price of
`50.00`. Matching listing cards show a photo or placeholder, title, asking price,
condition, and status. Open a card to see all photos, the description, category,
condition, pickup location, and seller profile link. No matches and failed loads
have different messages; a failed load offers **Retry**.

Under Selling, **My Listings** shows your listings and pending-offer counts.
Choose **Create Listing**, enter the details, and optionally use **Add Photo**.
Photo previews have **Move Up**, **Move Down**, and **Remove** controls. New listings
prefill your preferred pickup location, which you can change for that listing.
Photos are validated before being added and revalidated when saved; the app
does not automatically crop or resize images.

Open one of your listings for **Edit Listing**, **Archive Listing**, **Delete
Listing**, and Incoming Offers. Actions unavailable in the listing's state are
disabled with an explanation. Saving actual changes with pending offers asks for
confirmation because those offers will be rejected. Archival and permanent deletion
also explain their consequences before proceeding.

## Offers and sales

On another seller's available listing, choose **Make Offer**, enter an SGD amount
with at most two decimal places, and choose **Submit Offer**. Your pending offer
then appears with **Withdraw Offer**; changing an amount requires withdrawing first.
**My Offers** contains your offer history and links to listings and accepted purchases.

Sellers accept or reject offers in the listing's Incoming Offers section.
**Accept Offer** asks for confirmation: it reserves the listing, creates an active
sale, and rejects competing pending offers. Accepted offers link to their sale.

**My Purchases** and **My Sales** show agreed sales, counterpart profiles, statuses,
and next steps. Open a sale to see its agreed title, description, condition, price,
confirmation timestamps, and cancellation history. Both participants must use
**Confirm Completion** after the handover; the second confirmation completes the
sale and marks the listing sold.

Before either confirmation, **Cancel Sale** releases the listing. After a
confirmation, use **Request Cancellation**: the other participant must accept or
reject it, and the requester can withdraw it. Pending requests block completion.
Confirmation, direct cancellation, and accepting cancellation explain their effects
in confirmation dialogs. Only currently permitted actions are enabled.

The seller **Dashboard** summarises pending offers, active/completed sales, and
the total agreed value of completed sales.

## Feedback and unfinished features

Forms retain input after errors and show field-specific validation where possible.
Loading indicators show work in progress and prevent repeat submissions. Routine
profile and password saves use inline success messages. Empty collections explain
what to do next, such as creating a listing or searching for items.

Wishlist, meetups/availability, conversations/chat, and notifications have no
screens yet. Their navigation entries and relevant contextual controls are disabled
and labelled **Coming soon**. They do not open placeholder feature screens. The
meetup rules under Marketplace rules are already enforced and will apply once the
meetup screens are added.

## Marketplace rules

The account service enforces these rules:

- Registration requires a unique username, display name, and password. Usernames
  use 3-30 ASCII letters, digits, or underscores and are case-insensitively unique.
  Usernames cannot be changed. Display names contain 1-80 Unicode code points.
- Passwords contain 8-128 Unicode code points, including an ASCII uppercase letter,
  lowercase letter, digit, and punctuation character. Spaces are allowed but do
  not count as punctuation. Password case and whitespace are preserved exactly.
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
- Archiving hides an available or sold listing from search but keeps it, and
  people with a link to it can still open it. Archived listings cannot be
  reopened. **Archiving also rejects all pending offers.** A reserved listing
  cannot be archived.
- Deleting permanently removes an available or archived listing and its photos.
  Reserved and sold listings cannot be deleted, and neither can any listing that
  has ever received an offer, even one that was later withdrawn; archive it instead.
- Search shows only other sellers' available listings. It can match text in the
  title (ignoring upper and lower case), and filter by one category, one or more
  conditions, and a minimum and/or maximum price (both inclusive). Results are
  sorted newest first, or by price from low to high or high to low.

The offer service enforces these rules. Every offer action requires login.

- Buyers can offer from S$0.01 to S$1,000,000.00 on another seller's available
  listing. Offers may be above the asking price. You cannot offer on your own
  listing, or on a listing that is reserved, sold, or archived.
- You can have only one pending offer on each listing. To change the amount,
  withdraw your offer and make a new one. Only pending offers can be withdrawn.
- Buyers see all of their own offers, newest first, with each listing's current
  status. Other buyers never see your offer or its amount.
- Sellers see every offer on their own listing: the accepted offer first, then
  the others newest first.
- Accepting an offer reserves the listing and automatically rejects every other
  pending offer on it. Only pending offers can be accepted or rejected, and only
  by the seller.
- Every refused action explains what went wrong and what to do next, for
  example: "You already have a pending offer of S$40.00 on this listing.
  Withdraw it before making a new one."

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
  dashboard also counts your upcoming meetups as a seller; this count is not on
  the Dashboard screen yet.

The meetup service enforces these rules. Every meetup action requires login, and
only the sale's buyer and seller can see or act on its meetup.

- Meetups are arranged for an active sale. The seller offers the buyer up to 3
  meetup times, each with a start, an end, and a pickup location (1-200
  characters). A time lasts 15 minutes to 4 hours, starts in the future, and
  starts at most 60 days ahead. A sale's offered times cannot overlap each other,
  or any meetup the seller already has.
- Only the buyer books, by choosing one of the offered times. Booking deletes the
  sale's other offered times. Neither of you can book a time that overlaps
  another meetup you already have, whether you are buying or selling in it. If
  the seller offered the same time to two buyers, whoever books first gets it.
- The seller can withdraw an offered time that nobody has booked. Offered times
  cannot be edited, and times that have already started are no longer shown.
- Either of you can propose moving a booked meetup to a new time and place. The
  other person accepts (the meetup moves) or rejects (it stays as booked), and
  you can withdraw your own proposal. Only one proposal can be pending at a time.
- Either of you can cancel a booked meetup. The sale stays active, so the seller
  offers new times and the buyer books again.
- Completing the sale completes its meetup, and cancelling the sale cancels it.
  You can confirm completion with or without a meetup. A meetup whose time has
  passed stays booked, and the next step becomes "Did the handover happen?
  Confirm completion".
- Each sale in My Sales and My Purchases, and each reserved listing, carries the
  offered times or the booked meetup with any pending proposal, ready for the
  meetup screens to show.
- Every refused action explains what went wrong, for example: "The seller
  already has a meetup from Fri 25 Sep, 3:00 PM to Fri 25 Sep, 3:30 PM. Choose a
  different time."
