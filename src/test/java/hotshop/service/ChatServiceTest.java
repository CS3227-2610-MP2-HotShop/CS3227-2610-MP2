package hotshop.service;

import static hotshop.service.ListingServiceTest.assertFailure;
import static hotshop.service.ListingServiceTest.draft;
import static hotshop.service.ListingServiceTest.loginAs;
import static hotshop.service.ListingServiceTest.registerAndLogin;
import static hotshop.service.ListingServiceTest.setStatus;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import hotshop.ApplicationRuntime;
import hotshop.model.ListingStatus;
import hotshop.model.Message;
import hotshop.model.OfferStatus;

class ChatServiceTest {
    private static final Instant START = Instant.parse("2026-09-25T00:00:00Z");

    @TempDir
    Path directory;

    private final TestClock clock = new TestClock(START);
    private final Map<String, UUID> users = new HashMap<>();

    @Test
    void messageSeller_availableListing_startsConversationWithMessage() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            signIn(runtime, "bobby");
            ConversationView view = runtime.getChats().messageSeller(listing, "  Still available?  ").join();
            ConversationSummary summary = view.summary();
            assertEquals(List.of("Still available?"), texts(view));
            assertEquals(users.get("bobby"), view.messages().get(0).senderId());
            assertEquals(1, view.messages().get(0).sequence());
            assertEquals(SaleRole.BUYER, summary.role());
            assertEquals("alice", summary.otherParticipant().displayName());
            assertEquals(listing, summary.listing().getId());
            assertEquals("Still available?", summary.preview());
            assertEquals(0, summary.unreadCount());
            assertTrue(summary.canSend());
            assertTrue(summary.latestOffer().isEmpty());
            assertTrue(summary.activeSaleId().isEmpty());
        }
    }

    @Test
    void messageSeller_existingConversation_appendsToSameConversation() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            signIn(runtime, "bobby");
            UUID first = runtime.getChats().messageSeller(listing, "Hi").join().summary().conversation().getId();
            ConversationView second = runtime.getChats().messageSeller(listing, "Still there?").join();
            assertEquals(first, second.summary().conversation().getId());
            assertEquals(List.of(1L, 2L), second.messages().stream().map(Message::sequence).toList());
            assertEquals(1, runtime.getChats().getConversations().join().size());
        }
    }

    @Test
    void messageSeller_reservedListing_startsConversation() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            acceptedSale(runtime, listing, "carol");
            signIn(runtime, "bobby");
            assertEquals(1, runtime.getChats().messageSeller(listing, "If it falls through?").join()
                    .messages().size());
        }
    }

    @Test
    void messageSeller_soldListing_reportsInvalidState() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            setStatus(directory, listing, ListingStatus.SOLD);
            signIn(runtime, "bobby");
            var failure = assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> runtime.getChats().messageSeller(listing, "Hi").join());
            assertTrue(failure.getMessage().contains("sold"), failure.getMessage());
            assertEquals(List.of(), runtime.getChats().getConversations().join());
        }
    }

    @Test
    void messageSeller_archivedListing_reportsInvalidState() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            runtime.getListings().archiveListing(listing).join();
            signIn(runtime, "bobby");
            assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> runtime.getChats().messageSeller(listing, "Hi").join());
        }
    }

    @Test
    void messageSeller_ownListing_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            assertFailure(ServiceException.Code.PERMISSION,
                    () -> runtime.getChats().messageSeller(listing, "Hi").join());
        }
    }

    @Test
    void messageSeller_unknownListing_reportsNotFound() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            signIn(runtime, "bobby");
            assertFailure(ServiceException.Code.NOT_FOUND,
                    () -> runtime.getChats().messageSeller(UUID.randomUUID(), "Hi").join());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, Message.MAX_LENGTH})
    void messageSeller_boundaryLength_savesMessage(int length) throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            signIn(runtime, "bobby");
            assertEquals(length, runtime.getChats().messageSeller(listing, "x".repeat(length)).join()
                    .messages().get(0).text().length());
        }
    }

    @Test
    void messageSeller_overlongMessage_reportsValidationWithLength() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            signIn(runtime, "bobby");
            var failure = assertFailure(ServiceException.Code.VALIDATION,
                    () -> runtime.getChats().messageSeller(listing, "x".repeat(Message.MAX_LENGTH + 1)).join());
            assertTrue(failure.getMessage().contains("1,000") && failure.getMessage().contains("1,001"),
                    failure.getMessage());
            assertEquals(List.of(), runtime.getChats().getConversations().join());
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void messageSeller_blankMessage_reportsValidation(String text) throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            signIn(runtime, "bobby");
            assertFailure(ServiceException.Code.VALIDATION,
                    () -> runtime.getChats().messageSeller(listing, text).join());
        }
    }

    @Test
    void sendMessage_sellerReply_marksEarlierMessagesReadForSeller() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID conversation = enquiry(runtime, "Chairs", "Still available?");
            signIn(runtime, "alice");
            ConversationView reply = runtime.getChats().sendMessage(conversation, "Yes!").join();
            assertEquals(List.of("Still available?", "Yes!"), texts(reply));
            assertEquals(SaleRole.SELLER, reply.summary().role());
            assertEquals(0, reply.summary().unreadCount());
            assertEquals("Yes!", reply.summary().preview());
            signIn(runtime, "bobby");
            assertEquals(1, runtime.getChats().getConversations().join().get(0).unreadCount());
        }
    }

    @Test
    void sendMessage_stranger_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID conversation = enquiry(runtime, "Chairs", "Hi");
            signIn(runtime, "carol");
            assertFailure(ServiceException.Code.PERMISSION,
                    () -> runtime.getChats().sendMessage(conversation, "Me too").join());
        }
    }

    @Test
    void sendMessage_unknownConversation_reportsNotFound() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            signIn(runtime, "bobby");
            assertFailure(ServiceException.Code.NOT_FOUND,
                    () -> runtime.getChats().sendMessage(UUID.randomUUID(), "Hi").join());
        }
    }

    @Test
    void sendMessage_soldListing_reportsInvalidStateButStaysReadable() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID conversation = enquiry(runtime, "Chairs", "Hi");
            setStatus(directory, listingOf(runtime, conversation), ListingStatus.SOLD);
            assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> runtime.getChats().sendMessage(conversation, "Sold already?").join());
            ConversationView view = runtime.getChats().openConversation(conversation).join();
            assertEquals(List.of("Hi"), texts(view));
            assertFalse(view.summary().canSend());
        }
    }

    @Test
    void sendMessage_cancelledSale_allowsSendingAgain() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            UUID sale = acceptedSale(runtime, listing, "bobby");
            runtime.getTransactions().cancelSale(sale).join();
            signIn(runtime, "bobby");
            UUID conversation = runtime.getChats().openChatWithSeller(listing).join().orElseThrow()
                    .summary().conversation().getId();
            assertEquals(1, runtime.getChats().sendMessage(conversation, "Still selling?").join()
                    .messages().size());
        }
    }

    @Test
    void openConversation_unreadMessages_marksEverythingRead() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID conversation = enquiry(runtime, "Chairs", "Hi");
            runtime.getChats().messageSeller(listingOf(runtime, conversation), "Hello?").join();
            signIn(runtime, "alice");
            assertEquals(2, runtime.getChats().getUnreadCount().join());
            clock.advanceSeconds(60);
            assertEquals(0, runtime.getChats().openConversation(conversation).join().summary().unreadCount());
            assertEquals(0, runtime.getChats().getUnreadCount().join());
        }
    }

    @Test
    void openConversation_stranger_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID conversation = enquiry(runtime, "Chairs", "Hi");
            signIn(runtime, "carol");
            assertFailure(ServiceException.Code.PERMISSION,
                    () -> runtime.getChats().openConversation(conversation).join());
        }
    }

    @Test
    void openChatWithSeller_noConversation_returnsEmpty() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            signIn(runtime, "bobby");
            assertTrue(runtime.getChats().openChatWithSeller(listing).join().isEmpty());
            assertEquals(List.of(), runtime.getChats().getConversations().join());
        }
    }

    @Test
    void openChatWithSeller_existingConversation_returnsItMarkedRead() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID conversation = enquiry(runtime, "Chairs", "Hi");
            signIn(runtime, "alice");
            runtime.getChats().sendMessage(conversation, "Yes").join();
            signIn(runtime, "bobby");
            clock.advanceSeconds(60);
            ConversationView view = runtime.getChats().openChatWithSeller(listingOf(runtime, conversation))
                    .join().orElseThrow();
            assertEquals(conversation, view.summary().conversation().getId());
            assertEquals(0, view.summary().unreadCount());
        }
    }

    @Test
    void openChatWithSeller_ownListing_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            assertFailure(ServiceException.Code.PERMISSION,
                    () -> runtime.getChats().openChatWithSeller(listing).join());
        }
    }

    @Test
    void openChatWithSeller_soldListingWithoutConversation_reportsInvalidState() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            setStatus(directory, listing, ListingStatus.SOLD);
            signIn(runtime, "bobby");
            assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> runtime.getChats().openChatWithSeller(listing).join());
        }
    }

    @Test
    void openChatWithBuyer_buyerWhoOffered_opensTheirConversation() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            offer(runtime, listing, "bobby", 4000, null);
            signIn(runtime, "alice");
            ConversationView view = runtime.getChats().openChatWithBuyer(listing, users.get("bobby")).join();
            assertEquals(SaleRole.SELLER, view.summary().role());
            assertEquals("bobby", view.summary().otherParticipant().displayName());
            assertEquals(0, view.summary().unreadCount());
        }
    }

    @Test
    void openChatWithBuyer_notSeller_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            offer(runtime, listing, "bobby", 4000, null);
            signIn(runtime, "carol");
            assertFailure(ServiceException.Code.PERMISSION,
                    () -> runtime.getChats().openChatWithBuyer(listing, users.get("bobby")).join());
        }
    }

    @Test
    void openChatWithBuyer_noConversation_reportsNotFound() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            signIn(runtime, "bobby");
            signIn(runtime, "alice");
            assertFailure(ServiceException.Code.NOT_FOUND,
                    () -> runtime.getChats().openChatWithBuyer(listing, users.get("bobby")).join());
        }
    }

    @Test
    void submitOffer_withMessage_startsConversationWithMessage() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            offer(runtime, listing, "bobby", 4000, "Can pick up tonight");
            ConversationSummary summary = runtime.getChats().getConversations().join().get(0);
            assertEquals(OfferStatus.PENDING, summary.latestOffer().orElseThrow().getStatus());
            assertEquals("Can pick up tonight", summary.preview());
            assertEquals(List.of("Can pick up tonight"),
                    texts(runtime.getChats().openConversation(summary.conversation().getId()).join()));
        }
    }

    @Test
    void submitOffer_withoutMessage_startsEmptyConversationShowingOffer() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            offer(runtime, listing, "bobby", 4000, "   ");
            ConversationSummary summary = runtime.getChats().getConversations().join().get(0);
            assertEquals("Offer of S$40.00 made", summary.preview());
            assertEquals(START, summary.lastActivityAt());
            assertEquals(List.of(), texts(runtime.getChats().openConversation(summary.conversation().getId())
                    .join()));
        }
    }

    @Test
    void submitOffer_existingConversation_reusesIt() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID conversation = enquiry(runtime, "Chairs", "Hi");
            clock.advanceSeconds(60);
            runtime.getOffers().submitOffer(listingOf(runtime, conversation), 4000, "Offering now").join();
            List<ConversationSummary> all = runtime.getChats().getConversations().join();
            assertEquals(1, all.size());
            assertEquals(conversation, all.get(0).conversation().getId());
            assertEquals(List.of("Hi", "Offering now"), texts(runtime.getChats().openConversation(conversation)
                    .join()));
        }
    }

    @Test
    void submitOffer_overlongMessage_reportsValidationAndSavesNothing() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            signIn(runtime, "bobby");
            assertFailure(ServiceException.Code.VALIDATION, () -> runtime.getOffers()
                    .submitOffer(listing, 4000, "x".repeat(Message.MAX_LENGTH + 1)).join());
            assertEquals(List.of(), runtime.getOffers().getMyOffers().join());
            assertEquals(List.of(), runtime.getChats().getConversations().join());
        }
    }

    @Test
    void getConversations_newOfferForSeller_countsOneUnread() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            offer(runtime, listing, "bobby", 4000, null);
            assertEquals(0, runtime.getChats().getConversations().join().get(0).unreadCount());
            signIn(runtime, "alice");
            assertEquals(1, runtime.getChats().getConversations().join().get(0).unreadCount());
        }
    }

    @Test
    void getConversations_offerWithdrawnBeforeSellerOpened_countsOnce() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            UUID offer = offer(runtime, listing, "bobby", 4000, null);
            clock.advanceSeconds(60);
            runtime.getOffers().withdrawOffer(offer).join();
            signIn(runtime, "alice");
            ConversationSummary summary = runtime.getChats().getConversations().join().get(0);
            assertEquals(1, summary.unreadCount());
            assertEquals("Offer of S$40.00 withdrawn", summary.preview());
        }
    }

    @Test
    void getConversations_offerWithdrawnAfterSellerOpened_countsWithdrawal() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            UUID offer = offer(runtime, listing, "bobby", 4000, null);
            signIn(runtime, "alice");
            clock.advanceSeconds(60);
            runtime.getChats().openChatWithBuyer(listing, users.get("bobby")).join();
            signIn(runtime, "bobby");
            clock.advanceSeconds(60);
            runtime.getOffers().withdrawOffer(offer).join();
            signIn(runtime, "alice");
            assertEquals(1, runtime.getChats().getConversations().join().get(0).unreadCount());
        }
    }

    @Test
    void getConversations_offerAccepted_countsUnreadForBuyerOnly() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            UUID sale = acceptedSale(runtime, listing, "bobby");
            ConversationSummary seller = runtime.getChats().getConversations().join().get(0);
            assertEquals(0, seller.unreadCount());
            assertEquals(sale, seller.activeSaleId().orElseThrow());
            signIn(runtime, "bobby");
            ConversationSummary buyer = runtime.getChats().getConversations().join().get(0);
            assertEquals(1, buyer.unreadCount());
            assertEquals("Offer of S$40.00 accepted", buyer.preview());
        }
    }

    @Test
    void getConversations_offerRejectedByListingEdit_countsUnreadForBuyer() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            offer(runtime, listing, "bobby", 4000, null);
            signIn(runtime, "alice");
            clock.advanceSeconds(60);
            runtime.getListings().updateListing(listing, draft("Chairs", 4500), List.of()).join();
            signIn(runtime, "bobby");
            ConversationSummary summary = runtime.getChats().getConversations().join().get(0);
            assertEquals(1, summary.unreadCount());
            assertEquals(OfferStatus.REJECTED, summary.latestOffer().orElseThrow().getStatus());
        }
    }

    @Test
    void getConversations_offerAndNewerEnquiry_listsOfferConversationFirst() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID chairs = listing(runtime, "alice", "Chairs");
            UUID lamp = listing(runtime, "alice", "Lamp");
            offer(runtime, chairs, "bobby", 4000, null);
            clock.advanceSeconds(60);
            runtime.getChats().messageSeller(lamp, "Is the lamp working?").join();
            assertEquals(List.of("Chairs", "Lamp"), titles(runtime.getChats().getConversations().join()));
        }
    }

    @Test
    void getConversations_sameGroup_listsUnreadThenLatestActivity() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID chairs = listing(runtime, "alice", "Chairs");
            UUID lamp = listing(runtime, "alice", "Lamp");
            UUID desk = listing(runtime, "alice", "Desk");
            signIn(runtime, "bobby");
            UUID chairsChat = runtime.getChats().messageSeller(chairs, "Chairs?").join()
                    .summary().conversation().getId();
            clock.advanceSeconds(60);
            UUID lampChat = runtime.getChats().messageSeller(lamp, "Lamp?").join().summary().conversation().getId();
            clock.advanceSeconds(60);
            runtime.getChats().messageSeller(desk, "Desk?").join();
            signIn(runtime, "alice");
            runtime.getChats().openConversation(lampChat).join();
            runtime.getChats().openConversation(chairsChat).join();
            clock.advanceSeconds(60);
            runtime.getChats().sendMessage(lampChat, "Works fine").join();
            assertEquals(List.of("Desk", "Lamp", "Chairs"), titles(runtime.getChats().getConversations().join()));
        }
    }

    @Test
    void getConversations_otherBuyersActiveSale_leavesEnquiryWithoutSale() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            signIn(runtime, "bobby");
            runtime.getChats().messageSeller(listing, "Hi").join();
            clock.advanceSeconds(60);
            acceptedSale(runtime, listing, "carol");
            List<ConversationSummary> all = runtime.getChats().getConversations().join();
            assertEquals(List.of("carol", "bobby"), all.stream()
                    .map(summary -> summary.otherParticipant().displayName()).toList());
            assertTrue(all.get(0).activeSaleId().isPresent());
            assertTrue(all.get(1).activeSaleId().isEmpty());
        }
    }

    @Test
    void getUnreadCount_severalConversations_sumsEveryUnreadItem() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID chairs = listing(runtime, "alice", "Chairs");
            UUID lamp = listing(runtime, "alice", "Lamp");
            offer(runtime, chairs, "bobby", 4000, "Tonight?");
            runtime.getChats().messageSeller(lamp, "Lamp?").join();
            runtime.getChats().messageSeller(lamp, "Hello?").join();
            signIn(runtime, "alice");
            assertEquals(4, runtime.getChats().getUnreadCount().join());
        }
    }

    @Test
    void deleteListing_enquiryConversation_reportsInvalidState() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID conversation = enquiry(runtime, "Chairs", "Hi");
            UUID listing = listingOf(runtime, conversation);
            signIn(runtime, "alice");
            var failure = assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> runtime.getListings().deleteListing(listing).join());
            assertTrue(failure.getMessage().contains("Archive"), failure.getMessage());
        }
    }

    @Test
    void getConversations_reopenedDatabase_keepsMessagesAndReadState() throws Exception {
        UUID conversation;
        try (ApplicationRuntime runtime = open()) {
            conversation = enquiry(runtime, "Chairs", "Hi");
            signIn(runtime, "alice");
            runtime.getChats().sendMessage(conversation, "Hello").join();
        }
        try (ApplicationRuntime runtime = open()) {
            loginAs(runtime, "bobby");
            ConversationSummary summary = runtime.getChats().getConversations().join().get(0);
            assertEquals(1, summary.unreadCount());
            assertEquals(List.of("Hi", "Hello"), texts(runtime.getChats().openConversation(conversation).join()));
        }
    }

    @Test
    void messageSeller_loggedOut_reportsSession() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            assertFailure(ServiceException.Code.SESSION,
                    () -> runtime.getChats().messageSeller(UUID.randomUUID(), "Hi").join());
        }
    }

    @Test
    void sendMessage_loggedOut_reportsSession() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            assertFailure(ServiceException.Code.SESSION,
                    () -> runtime.getChats().sendMessage(UUID.randomUUID(), "Hi").join());
        }
    }

    @Test
    void openConversation_loggedOut_reportsSession() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            assertFailure(ServiceException.Code.SESSION,
                    () -> runtime.getChats().openConversation(UUID.randomUUID()).join());
        }
    }

    @Test
    void openChatWithSeller_loggedOut_reportsSession() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            assertFailure(ServiceException.Code.SESSION,
                    () -> runtime.getChats().openChatWithSeller(UUID.randomUUID()).join());
        }
    }

    @Test
    void openChatWithBuyer_loggedOut_reportsSession() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            assertFailure(ServiceException.Code.SESSION,
                    () -> runtime.getChats().openChatWithBuyer(UUID.randomUUID(), UUID.randomUUID()).join());
        }
    }

    @Test
    void getConversations_loggedOut_reportsSession() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            assertFailure(ServiceException.Code.SESSION, () -> runtime.getChats().getConversations().join());
        }
    }

    @Test
    void getUnreadCount_loggedOut_reportsSession() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            assertFailure(ServiceException.Code.SESSION, () -> runtime.getChats().getUnreadCount().join());
        }
    }

    @Test
    void messageSeller_maximumLengthOfEmoji_savesMessage() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            signIn(runtime, "bobby");
            String emoji = "😀".repeat(Message.MAX_LENGTH);
            assertEquals(emoji, runtime.getChats().messageSeller(listing, emoji).join().messages().get(0).text());
        }
    }

    @Test
    void sendMessage_archivedListing_reportsInvalidState() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID conversation = enquiry(runtime, "Chairs", "Hi");
            UUID listing = listingOf(runtime, conversation);
            signIn(runtime, "alice");
            runtime.getListings().archiveListing(listing).join();
            assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> runtime.getChats().sendMessage(conversation, "Archived now").join());
        }
    }

    @Test
    void openChatWithSeller_soldListingWithConversation_opensReadOnly() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID conversation = enquiry(runtime, "Chairs", "Hi");
            UUID listing = listingOf(runtime, conversation);
            setStatus(directory, listing, ListingStatus.SOLD);
            ConversationView view = runtime.getChats().openChatWithSeller(listing).join().orElseThrow();
            assertEquals(List.of("Hi"), texts(view));
            assertFalse(view.summary().canSend());
        }
    }

    @Test
    void openChatWithBuyer_unknownListing_reportsNotFound() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            signIn(runtime, "alice");
            assertFailure(ServiceException.Code.NOT_FOUND,
                    () -> runtime.getChats().openChatWithBuyer(UUID.randomUUID(), users.get("alice")).join());
        }
    }

    @Test
    void submitOffer_pendingOfferAlready_savesNoMessage() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            offer(runtime, listing, "bobby", 4000, null);
            assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> runtime.getOffers().submitOffer(listing, 4500, "Higher now").join());
            UUID conversation = runtime.getChats().getConversations().join().get(0).conversation().getId();
            assertEquals(List.of(), texts(runtime.getChats().openConversation(conversation).join()));
        }
    }

    @Test
    void getConversations_sellerRejectsOffer_countsForBuyerNotSeller() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            UUID offer = offer(runtime, listing, "bobby", 4000, null);
            signIn(runtime, "alice");
            clock.advanceSeconds(60);
            runtime.getOffers().rejectOffer(offer).join();
            assertEquals(0, runtime.getChats().getConversations().join().get(0).unreadCount());
            signIn(runtime, "bobby");
            assertEquals(1, runtime.getChats().getConversations().join().get(0).unreadCount());
        }
    }

    @Test
    void getConversations_otherOfferAccepted_countsRejectionForBuyer() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            offer(runtime, listing, "bobby", 4000, null);
            acceptedSale(runtime, listing, "carol");
            signIn(runtime, "bobby");
            ConversationSummary summary = runtime.getChats().getConversations().join().get(0);
            assertEquals(OfferStatus.REJECTED, summary.latestOffer().orElseThrow().getStatus());
            assertEquals(1, summary.unreadCount());
        }
    }

    @Test
    void getConversations_listingArchived_countsRejectionForBuyer() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            offer(runtime, listing, "bobby", 4000, null);
            signIn(runtime, "alice");
            clock.advanceSeconds(60);
            runtime.getListings().archiveListing(listing).join();
            signIn(runtime, "bobby");
            ConversationSummary summary = runtime.getChats().getConversations().join().get(0);
            assertEquals(1, summary.unreadCount());
            assertFalse(summary.canSend());
        }
    }

    @Test
    void sendMessage_afterOfferAccepted_clearsOfferEventForSender() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID listing = listing(runtime, "alice", "Chairs");
            acceptedSale(runtime, listing, "bobby");
            signIn(runtime, "bobby");
            UUID conversation = runtime.getChats().getConversations().join().get(0).conversation().getId();
            clock.advanceSeconds(60);
            assertEquals(0, runtime.getChats().sendMessage(conversation, "Great, when can we meet?").join()
                    .summary().unreadCount());
        }
    }

    @Test
    void getConversations_newerOfferEvent_movesConversationUp() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID chairs = listing(runtime, "alice", "Chairs");
            UUID lamp = listing(runtime, "alice", "Lamp");
            signIn(runtime, "bobby");
            runtime.getChats().messageSeller(chairs, "Chairs?").join();
            clock.advanceSeconds(60);
            runtime.getChats().messageSeller(lamp, "Lamp?").join();
            clock.advanceSeconds(60);
            UUID offer = runtime.getOffers().submitOffer(chairs, 4000).join().offer().getId();
            clock.advanceSeconds(60);
            runtime.getOffers().withdrawOffer(offer).join();
            signIn(runtime, "alice");
            assertEquals(List.of("Chairs", "Lamp"), titles(runtime.getChats().getConversations().join()));
        }
    }

    @Test
    void getConversations_withdrawAndReofferSameMoment_showsPendingOfferInTopGroup() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID chairs = listing(runtime, "alice", "Chairs");
            UUID lamp = listing(runtime, "alice", "Lamp");
            signIn(runtime, "bobby");
            runtime.getChats().messageSeller(lamp, "Lamp?").join();
            UUID first = runtime.getOffers().submitOffer(chairs, 4000).join().offer().getId();
            runtime.getOffers().withdrawOffer(first).join();
            runtime.getOffers().submitOffer(chairs, 4200).join();
            clock.advanceSeconds(60);
            runtime.getChats().messageSeller(lamp, "Still there?").join();
            ConversationSummary top = runtime.getChats().getConversations().join().get(0);
            assertEquals("Chairs", top.listing().getDetails().title());
            assertEquals(4200, top.latestOffer().orElseThrow().getAmountCents());
        }
    }

    /** The seller lists an item, registering the seller the first time they appear; they stay logged in. */
    private UUID listing(ApplicationRuntime runtime, String seller, String title) {
        signIn(runtime, seller);
        return runtime.getListings().createListing(draft(title, 5000), List.of()).join().listing().getId();
    }

    /** Bob messages Alice about a new listing; returns the conversation with Bob logged in. */
    private UUID enquiry(ApplicationRuntime runtime, String title, String text) {
        UUID listing = listing(runtime, "alice", title);
        signIn(runtime, "bobby");
        return runtime.getChats().messageSeller(listing, text).join().summary().conversation().getId();
    }

    /** The buyer makes an offer, optionally with a message, and stays logged in. */
    private UUID offer(ApplicationRuntime runtime, UUID listing, String buyer, long amount, String message) {
        signIn(runtime, buyer);
        return runtime.getOffers().submitOffer(listing, amount, message).join().offer().getId();
    }

    /** The buyer offers S$40.00 and the seller, Alice, accepts; Alice is left logged in. */
    private UUID acceptedSale(ApplicationRuntime runtime, UUID listing, String buyer) {
        UUID offer = offer(runtime, listing, buyer, 4000, null);
        signIn(runtime, "alice");
        clock.advanceSeconds(60);
        return runtime.getOffers().acceptOffer(offer).join().transactionId();
    }

    private UUID listingOf(ApplicationRuntime runtime, UUID conversation) {
        return runtime.getChats().getConversations().join().stream()
                .filter(summary -> summary.conversation().getId().equals(conversation))
                .findFirst().orElseThrow().listing().getId();
    }

    private static List<String> texts(ConversationView view) {
        return view.messages().stream().map(Message::text).toList();
    }

    private static List<String> titles(List<ConversationSummary> summaries) {
        return summaries.stream().map(summary -> summary.listing().getDetails().title()).toList();
    }

    private void signIn(ApplicationRuntime runtime, String username) {
        if (users.containsKey(username)) {
            loginAs(runtime, username);
        } else {
            users.put(username, registerAndLogin(runtime, username));
        }
    }

    private ApplicationRuntime open() throws Exception {
        return ApplicationRuntime.open(directory, clock);
    }
}
