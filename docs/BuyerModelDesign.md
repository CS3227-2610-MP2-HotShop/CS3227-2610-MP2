# Buyer Model Design

Status: approved for implementation by the user through the implement skill.

## Scope and ownership

Implement shared models for buyer features that the seller developer will review
and reuse: User, Listing, ListingImage, Offer, Transaction, and
CancellationRequest, with supporting enums. Defer chat, wishlists, meetups,
notifications, authentication, persistence, services, and UI integration.

HotShop_Architecture.md remains the baseline. The approved differences below
will be reflected there when the overall design is confirmed.

## Model structure

- Plain Java models independent of JavaFX and SQL.
- UUID identities assigned at creation; references between entities use IDs.
- Controlled operations enforce object-local invariants and valid transitions;
  avoid unrestricted setters. Keep fields immutable where possible.
- Future services enforce permissions and coordinate changes across models.
  For example, accepting an offer, reserving its listing, rejecting competing
  offers, and creating a transaction must ultimately succeed atomically.
- SGD only; represent amounts as integer cents. Listing prices and offer
  amounts must be at least one cent. Offers may exceed the asking price.

## User

Identity and profile: ID, username, display name, optional profile image, and
optional preferred pickup location. Credentials are managed separately.
Every user can buy and sell; ownership of the listing determines the seller.
There are no assigned account roles or separate Buyer/Seller subclasses.

## Listing and ListingImage

A listing represents one sale, possibly a bundle, without quantity tracking.
Separate sales require separate listings. Private negotiations do not create
partial-sale tracking.

Listing information: ID, seller ID, title, description, category, price,
condition, pickup location, and status. Images associate relative filenames
and display order with the listing, following the architecture's storage model.

- Categories: Electronics, Books, Clothing, Furniture, Sports, Other.
- Conditions: New, Like new, Good, Fair, Poor.
- Statuses: AVAILABLE, RESERVED, SOLD, ARCHIVED.
- Required: title, description, pickup location, category, condition, price.
- Images are optional.
- Actual changes to title, description, price, category, condition, pickup
  location, or images reject pending offers through the future service layer.
  Saving unchanged values does not reject offers.
- While RESERVED, freeze title, description, price, category, condition,
  images, and pickup location. Cancellation makes the listing available again.
- Archiving an available listing rejects its pending offers and removes it from
  browsing while preserving history.
- Sold and archived listings cannot be edited or reopened. A seller creates a
  new listing to sell again. A sold listing may still be archived as specified
  in the architecture.
- A reserved listing must first have its transaction cancelled before archival.

## Offer

Information: ID, listing ID, buyer ID, amount, and status.

- Statuses: PENDING, ACCEPTED, REJECTED, WITHDRAWN.
- At most one pending offer per buyer/listing, enforced across records by services.
- Changing an amount requires withdrawal and a new offer; retain history.
- Follow the architecture: no offers on one's own listing or on reserved/sold
  listings. Acceptance rejects other pending offers and creates a transaction.

## Transaction

Information: ID, listing ID, accepted offer ID, buyer and seller IDs, agreed
price, status, and each participant's completion timestamp. Preserve snapshots
of the listing title, description, and condition at acceptance. Do not snapshot
pickup location in this milestone.

- Statuses: ACTIVE, COMPLETED, CANCELLED.
- Both participants must confirm completion; then the listing becomes SOLD.
- Before either confirms, either participant may cancel directly.
- After the first confirmation, cancellation requires both participants' agreement.
- Completed transactions are final; returns/refunds are outside this milestone.
- Cancellation releases the listing. Prior offers remain closed.

## CancellationRequest

A participant may request cancellation after the first completion confirmation.
The other participant may accept or reject; the requester may withdraw while
the request is pending.

- At most one pending request per transaction.
- A pending request leaves the transaction ACTIVE and listing RESERVED.
- Further completion confirmations are blocked while a request is pending.
- Rejection or withdrawal preserves the existing completion confirmation.
- New requests are allowed after rejection or withdrawal; retain request history.
- Acceptance cancels the transaction and releases the listing through the future
  coordinated workflow.

## Validation

Trim surrounding whitespace in textual fields.

| Field | Agreed constraint |
| --- | --- |
| Username | 3–30 ASCII letters, digits, or underscores |
| Username uniqueness | Case-insensitive; checked by future service/database |
| Display name | 1–80 characters |
| Listing title | 1–120 characters |
| Listing description | 1–5,000 characters |
| Pickup location | 1–200 characters when supplied; required for listings |
| Listing images | 0–10 with explicit display order |

## Implementation scope

All interview questions have been answered and the consolidated design approved.

The first implementation is limited to model classes, supporting types, and
JUnit 5 tests of their observable behaviour, with corresponding developer and
architecture documentation updates. The Java representation of coordinated
operations must not imply that model classes alone provide persistence,
authentication, uniqueness enforcement, or atomic cross-record updates.
