---
  layout: default.md
  title: "Manual testing"
  pageNav: 3
---

## Appendix: Instructions for manual testing

These steps test the listing, offer, sale, meetup, and chat features with two
accounts. They complement the [User Guide](../UserGuide.html), which explains each
screen; testing of accounts, profiles, and search is covered separately.

Only one user is logged in at a time, so every "as Bob" step means: choose
**Log out**, then log in as Bob. Messages and changes made by one user appear
for the other at their next login.

### Preparing a fresh data folder

Use a separate folder so tests never touch your own data. Build the JAR, then
start HotShop on the test folder (PowerShell):

```powershell
.\gradlew.bat shadowJar
java "-Dhotshop.dataDir=$env:TEMP\hotshop-test" -jar release\HotShop.jar
```

To start again from nothing, close HotShop and delete that folder.

Register three accounts (see the User Guide's account section). Carol is only
needed for the refusal checks:

| Username | Display name | Password |
| --- | --- | --- |
| `alice` | `Alice` | `Sample1!` |
| `bobby` | `Bob` | `Sample1!` |
| `carol` | `Carol` | `Sample1!` |

### Walkthrough: from listing to completed sale

1. **As Alice, create a listing.** My Listings, then **Create Listing**: title
   `Study desk`, description `Wooden desk with one drawer`, category Furniture,
   condition Good, price `50.00`, pickup location `Library entrance`. Choose
   **Save Listing**.
   Expected: the card appears in My Listings as Available with 0 pending offers.
2. **As Bob, message the seller.** Search, open **Study desk**, choose **Chat
   with seller**. The page shows "No messages yet." and a "No offer yet" bar.
   Type `Is it still available?` and choose **Send**.
   Expected: the message appears; Conversations lists the conversation with a
   Buying badge.
3. **As Bob, make an offer with a message.** In the conversation's bar choose
   **Make Offer**, enter `40.00` and the message `Can pick up tonight`, and
   choose **Submit Offer**.
   Expected: the bar shows "Offer of S$40.00 · Pending" with **Withdraw Offer**,
   and the message appears in the conversation.
4. **As Alice, see the unread items and accept.** The sidebar shows
   **Conversations (3)**: Bob's two messages plus the new offer. Open the
   conversation, choose **Accept Offer**, and confirm.
   Expected: Sale Details opens; the listing is Reserved; Conversations now
   shows no unread count for it.
5. **As Alice, offer a meetup time.** On Sale Details choose **Open Chat**. The
   bar says "No meetup times offered yet". Choose **Offer Time**; the dialog
   starts at tomorrow 12:00 for 30 minutes at `Library entrance`. Choose **Offer
   Time** again to save.
   Expected: the bar shows "1 time offered" with **View Times** and **Offer
   Time**.
6. **As Bob, book the time.** Open the conversation, choose **Choose Time**,
   then **Book**.
   Expected: the bar shows "Meetup: <tomorrow's date>, 12:00 to 12:30 · Library
   entrance" with **Propose Move**, **Cancel Meetup**, and **View Sale**.
7. **As Bob, propose a move.** Choose **Propose Move**, change the start time to
   `14:00`, and choose **Propose Move**.
   Expected: the bar says "You proposed moving the meetup to …" with **Withdraw
   Proposal**; Propose Move and Cancel Meetup are hidden while it is pending.
8. **As Alice, accept the move.** Open the conversation and choose **Accept
   Move**.
   Expected: the meetup now runs 14:00 to 14:30. My Listings shows the date,
   time, and place on the reserved card.
9. **Complete the sale.** As Alice, open My Sales, open the sale, choose
   **Confirm Completion**, and confirm. As Bob, do the same from My Purchases.
   Expected: after Bob confirms, the sale is completed, the listing is Sold, the
   conversation's bar shows "Sale completed · Met on …", and the send box is
   disabled because the listing is sold.

### Checks for refusals and edge cases

Start each from a new listing by Alice unless stated.

| Check | Steps | Expected |
| --- | --- | --- |
| One pending offer | As Bob, make an offer, then open the listing again. | Make Offer is replaced by your pending offer and **Withdraw Offer**. |
| Offer on a reserved listing | After Alice accepts Bob's offer, as Carol, open the listing. | Make Offer is disabled with "Only available listings can receive offers." Carol can still choose **Chat with seller**. |
| Other offers rejected on accept | As Bob and Carol, each make an offer; as Alice, accept Bob's. | Carol's offer shows as Rejected, and Carol's conversation shows an unread item. |
| Cancel before confirming | As Bob, offer `40.00`; as Alice, accept it. Then, as either user, open Sale Details and choose **Cancel Sale**, then confirm. | The sale is Cancelled and the listing is Available again. Bob's bar shows "Offer of S$40.00 · Accepted · Sale Cancelled" with **View Sale** and **Make Offer**. |
| Cancellation needs agreement | In an active sale, as Alice, choose **Confirm Completion**; then **Request Cancellation**. As Bob, open the sale. | Confirm Completion is disabled for Bob; **Accept Cancellation** and **Reject Cancellation** are shown. Accepting cancels the sale; rejecting keeps it active. |
| At most three offered times | In an active sale with no booked meetup, as Alice, offer three different times. | After the third, **Offer Time** is no longer shown. |
| Meetups up to 60 days ahead | In the Offer Time dialog, open the date picker. | Dates before today and after the 60th day from today are disabled; any start time on day 60 is accepted. |
| No double bookings | With a booked meetup between Alice and Bob (walkthrough steps 1 to 8, before completing the sale), create a second listing and sale between them; as Alice, offer the same time as the booked meetup. | Refused: "You already have a meetup from … to …. Choose a different time." |
| Cancel a meetup | With a booked meetup, choose **Cancel Meetup** and confirm. | The sale stays active and the bar asks the seller to offer times again. |
| Messages are limited | In any open conversation, paste a message over 1,000 characters and choose **Send**. | The counter below Send shows, for example, "1234 / 1,000". Sending is refused with "Messages can be at most 1,000 characters, but this one has 1,234." and the draft is kept. |
| Delete a listing with enquiries | As Bob, message Alice about a new listing without offering. As Alice, open the listing and choose **Delete Listing**. | The confirmation adds "1 conversation about this listing will also be deleted, for you and the buyers." After confirming, Bob no longer has the conversation. |
| Delete is refused after an offer | As Bob, make and then withdraw an offer. As Alice, open the listing. | **Delete Listing** is disabled; archive the listing instead. |
