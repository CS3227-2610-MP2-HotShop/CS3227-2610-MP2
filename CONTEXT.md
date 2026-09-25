# HotShop

HotShop is a marketplace where users offer items for sale and agree purchases.

## Language

**User**:
A registered participant in HotShop who can both buy and sell. Buyer and seller describe the user's relationship to a particular listing or agreed sale, rather than separate account types.

**Buyer**:
A user acting as a potential purchaser of a listing or as the purchaser in an agreed sale.

**Seller**:
The user who owns a listing and offers it for sale.

**Profile**:
A user's display name, profile image, and preferred pickup location. Display name and image are visible to other users; preferred pickup location is private to the owner.

**Current user**:
The user currently logged in to this running HotShop application. At most one user is logged in at a time; restarting the application leaves no current user.

**Public profile**:
The portion of a user's profile visible to other logged-in HotShop users: display name and profile image. It excludes the user's preferred pickup location.

**Listing**:
A seller's advertised item or bundle offered as one sale, with its description and asking price. A listing has no separately tracked quantities.
_Avoid_: Product (when referring to the seller's advertisement)

**Archive**:
A seller's action that withdraws a listing from browsing and search while keeping it, and its offer, transaction, and conversation history, visible to the people involved. An archived listing cannot be reopened.
_Avoid_: Delete (when history is kept)

**Delete**:
A seller's action that permanently removes a listing and its images. Only possible for a listing with no offer, transaction, or conversation history; otherwise the seller archives it.
_Avoid_: Archive, remove

**Listing image**:
A photograph or other image associated with a listing for display to potential buyers.

**Offer**:
A buyer's formal proposal to purchase a particular listing at a specified amount.
_Avoid_: Bid (which implies an auction)

**Withdraw**:
A buyer's action that takes back their own pending offer. The offer stays in history as withdrawn and the buyer may make a new offer. An accepted offer cannot be withdrawn; if its sale is later cancelled, the offer stays accepted and the sale is cancelled.
_Avoid_: Retract (ambiguous between a withdrawn offer and a cancelled sale)

**Transaction**:
An agreed sale between a buyer and seller, formed when the seller accepts an offer.
_Avoid_: Order, payment (when referring to the agreed sale)

**Active sale**:
A transaction that has been agreed but not yet completed or cancelled: the participants are still to meet, hand over the item, and both confirm completion. Its listing is reserved.
_Avoid_: Pending sale, open order

**Meetup slot**:
A time and place a seller offers one buyer for handing over the item of one active sale. The buyer books one of the offered slots; the others are then withdrawn.
_Avoid_: Availability (slots are offered to one buyer, not published to everyone)

**Meetup**:
The booked meetup slot where an active sale's buyer and seller plan to hand over the item. It completes or is cancelled together with its sale, and can also be cancelled on its own so that new slots can be offered.
_Avoid_: Appointment, booking (as separate terms)

**Reschedule proposal**:
A participant's proposal to move a meetup to one new time and place. The other participant accepts or rejects it, and the proposer may withdraw it.

**Completion confirmation**:
A transaction participant's declaration that the agreed sale is complete. Both participants must confirm for the transaction to be completed.

**Conversation**:
The messages between one buyer and the seller about one listing. There is at most one conversation per buyer and listing. Only the buyer starts it, with their first message or their first offer on the listing. Only its buyer and seller can read it.
_Avoid_: Chat room, thread

**Message**:
A piece of text a participant sends in a conversation. Messages cannot be edited or deleted, and a message does not change any offer or sale, even if it says "I accept".

**Cancellation request**:
A transaction participant's proposal to cancel an active sale after the first completion confirmation, requiring the other participant's agreement. While it awaits a response, the sale remains active and further completion confirmations are blocked.
