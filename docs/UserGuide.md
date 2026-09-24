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

An 800 by 600 window titled **HotShop** displays **Welcome to HotShop**.
Resize the window as desired and close it with the operating system's close button.

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

## Current scope

The application still has a welcome screen only. Account registration, login,
profile editing, password changes, listing management and search, and offers are
implemented at service level for future screen integration; they are not
accessible from the current window. Completing or cancelling a sale, meetups,
chat, notifications, and other buyer/seller workflows are not yet available.

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
- Sellers see all of their own listings, in every status, newest first.
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
