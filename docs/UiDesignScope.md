# UI Design Scope

This document records the confirmed UI specification. After confirming the consolidated scope, the user explicitly requested implementation using the implement skill. Implementation status and usage are documented in the User Guide.

## Agreed decisions

- Map the full application's UI inventory, then specify the first milestone around existing account, listing, offer, and transaction services.
- Defer implementation of chat, meetup, wishlist, and notification screens. Include relevant entry points on first-milestone pages, such as Chat with seller on listing details.
- Show both future-feature actions and future navigation destinations disabled and labelled "Coming soon" until implemented.
- Use one application shell with a persistent grouped sidebar. Buying and Selling are non-clickable headings with direct links to individual pages, not role modes or mandatory hub pages.
- Sidebar layout: Search; Buying (My Offers, My Purchases, Wishlist, Meetups); Selling (Dashboard, My Listings, My Sales, Availability & Meetups); Conversations; Notifications; My Profile; Log out. Future-feature destinations remain disabled and labelled "Coming soon" as agreed above.
- Require login for marketplace pages, including listing search/details and public profiles. Login and registration are accessible while signed out.
- Search initially shows its controls and guidance, with no listing cards. Search/Enter submits the query and selected filters; typing alone does not fetch results.
- An empty submitted query shows all eligible listings, respecting filters. Distinguish the initial guidance from a search yielding no matches.
- Use a reusable listing summary card that opens listing details. Show the first photo or a placeholder, title, SGD asking price, condition, and status. My Listings cards also show the pending-offer count. Editing and offer actions belong on listing details.
- Public profiles show the user's display name, photo, and available listings. Preferred pickup location remains private. This requires an additional service query beyond the existing public-profile response; the user accepted that scope extension.
- After login, open Search in its initial guidance-only state.
- Use one listing detail page with an image gallery, title, description, price, category, condition, listing pickup location, status, and seller profile link. Non-owners can make offers on available listings and see disabled Chat/Wishlist controls labelled "Coming soon". Owners have Edit, Archive, Delete, and Incoming Offers subject to existing business rules. Explain status-based restrictions separately from unfinished features.
- Make Offer opens a dialog with listing title, asking price, amount entry, and Submit/Cancel. When the current buyer already has a pending offer, show its amount and a withdrawal action instead of another submission.
- Returning from listing/profile details preserves search query, filters, sorting, and scroll position within the session. Refresh using the last submitted criteria without applying unsubmitted edits. A new login session resets search.
- Names/photos open public profiles wherever participants are identified, including listing sellers, incoming-offer buyers, and sale counterparts. Opening the current user's identity goes to My Profile. Public-profile listings open the shared listing detail page.
- Login uses username/password. Registration uses username, display name, password, and password confirmation. Include password visibility toggles and links between the pages. Successful registration returns to login with the username filled and a success message; it does not log in automatically.
- My Profile shows a read-only username, editable display name, and private preferred pickup location. Save text fields together. Replace Image and Remove Image are separate actions. Change Password opens a dialog requiring the current password.
- Create/edit listings share one form: title, description, SGD price, category, condition, pickup location, and up to ten photos with previews and reorder/remove controls. Save all listing changes on submission and warn before leaving with unsaved changes. New listings prefill pickup location from the user's preference when present, but allow editing.
- My Purchases and My Sales list title, agreed price, counterpart, sale status, and next action. Entries open a shared sale detail page with the agreed listing snapshot, both completion confirmations, cancellation-request history, and permitted actions. Active sales show a disabled Arrange Meetup control labelled "Coming soon".
- Require a consequence-specific confirmation before deleting or archiving a listing, accepting an offer, confirming sale completion, directly cancelling a sale, accepting a cancellation request, or saving actual listing changes that reject pending offers. Use explicit action labels rather than Yes/No. Ordinary saves and offer submission do not receive a second confirmation.
- Query and filter edits wait for Search/Enter. Sort changes immediately reorder the last submitted results. Clear Filters does not submit automatically; Refresh reruns the last submitted search. Controls cover category, conditions, minimum/maximum price, and newest/price sorting.
- Search, My Listings, and public-profile listings use listing-card grids. Offers, purchases, and sales use compact rows. Preserve existing service ordering; public-profile listings appear newest first.
- Show loading indicators and prevent duplicate submissions while requests run. Preserve form input on errors, show field-specific validation near fields, and provide Retry for failed page loads. Routine saves use inline success messages. Warn before discarding unsaved listing or profile-text edits, including during logout.
- Use a simple light theme, neutral background, one accent colour, readable status badges, and clearly labelled controls. The window is resizable, sidebar/content scroll as needed, and listing grids reduce columns as width narrows. Exact branding and colours are deferred.
- Initial window size is 1100 x 750, with an enforced minimum of 960 x 640 in JavaFX layout units. Retain the sidebar at minimum size, reduce grid columns, and scroll page content vertically. Validate these target dimensions against real forms during implementation.
- Hide actions that do not belong to the current user. Show relevant actions blocked by state disabled with an explanation. Unfinished features remain disabled and labelled "Coming soon".
- Select images using a file picker and show previews. Accept JPEG/PNG only: profile images up to 5 MiB and 512 x 512; listing images up to 10 MiB and 4096 x 4096, at most ten per listing. Reject invalid selections with a specific message; do not automatically crop or resize. Use a placeholder for absent or unloadable images.
- After successful empty loads, My Listings offers Create Listing; My Offers/My Purchases offer Search Listings; My Sales offers My Listings; Incoming Offers explains that no offers have been received; public profiles show "No available listings". Loading failures must not appear as empty collections.

## Confirmation messages

- Delete Listing: permanently removes the listing and its managed photos.
- Archive Listing: hides the listing from search, rejects pending offers, and cannot be undone.
- Accept Offer: reserves the listing, creates an active sale, and rejects other pending offers.
- Confirm Completion: records this participant's confirmation; if the other participant has already confirmed, completes the sale and marks the listing sold.
- Cancel Sale / Accept Cancellation Request: cancels the sale and makes the listing available again; prior offers remain closed.
- Save changed listing with pending offers: saves changes and rejects pending offers. An unchanged save does not need this warning.

## Requested UI inventory

- Login and registration pages.
- Own-profile and public-profile pages.
- Reusable individual listing display.
- Listing detail page.
- Search page with a search bar.

## Additional agreed inventory

- Application navigation and logout.
- My Listings and create/edit listing form, including photo selection and ordering.
- My Offers and incoming offers.
- My Purchases, My Sales, and sale details/actions.
- Seller dashboard.
- Future wishlist, conversations/chat, availability/meetups, and notifications UI: inventory only, with disabled destinations and contextual entry points in this milestone.
- Offer entry, image gallery, profile-image selection, search filters/sorting, confirmations, and loading/error/empty states.

The sidebar and page grouping follow the agreed decisions above. Incoming Offers belongs to the owner's listing detail page; Create Listing belongs on My Listings.

## Deferred detail and validation

- Exact branding, colours, spacing, and component styling are deferred to the visual pass.
- Validate form/dialog fit, scrolling, and grid behavior at the agreed minimum window size during implementation.
- Detailed internal designs for unfinished feature screens are deferred with those features.
- The public seller-listing service query is implemented and covered by authenticated service tests.

## Review status

Individual recommendations Q1-Q28 and the consolidated scope were confirmed. Implementation was explicitly authorized in the subsequent request: "Confirmed. $implement this."

## Implementation notes

- Feature screens use programmatic JavaFX controls and the shared stylesheet.
- The Filters panel collapses to keep search results visible at minimum width/height.
- JUnit tests exercise service boundaries, search state, and actual JavaFX workflows
  using temporary databases. Screenshots are generated under `build/ui-checks/`.
- Exact branding and unfinished-feature screen internals remain deferred as agreed.
