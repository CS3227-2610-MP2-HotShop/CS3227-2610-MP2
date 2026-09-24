package hotshop.service;

import static hotshop.service.ListingServiceTest.assertFailure;
import static hotshop.service.ListingServiceTest.draft;
import static hotshop.service.ListingServiceTest.loginAs;
import static hotshop.service.ListingServiceTest.registerAndLogin;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import hotshop.ApplicationRuntime;
import hotshop.model.MeetupStatus;

class MeetupServiceTest {
    private static final String PASSWORD = "Sample1!";
    private static final Instant START = Instant.parse("2026-09-24T00:00:00Z");
    private static final Instant TOMORROW = START.plus(Duration.ofDays(1));
    private static final Instant LATER = TOMORROW.plus(Duration.ofDays(1));

    @TempDir
    Path directory;

    private final TestClock clock = new TestClock(START);
    private final Set<String> registered = new HashSet<>();

    @Test
    void offerSlot_seller_addsSlotThatTheBuyerSees() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "alice", "bobby", "Chairs");
            var summary = offer(runtime, sale, TOMORROW, 30);
            assertEquals(1, summary.offeredSlots().size());
            assertEquals("Library", summary.offeredSlots().get(0).time().location());
            loginAs(runtime, "bobby");
            assertEquals(1, runtime.getMeetups().getMeetupSummary(sale).join().offeredSlots().size());
        }
    }

    @Test
    void offerSlot_buyer_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "alice", "bobby", "Chairs");
            loginAs(runtime, "bobby");
            assertFailure(ServiceException.Code.PERMISSION, () -> offer(runtime, sale, TOMORROW, 30));
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {-60, 0})
    void offerSlot_startNotInFuture_reportsValidation(long secondsFromNow) throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "alice", "bobby", "Chairs");
            assertFailure(ServiceException.Code.VALIDATION,
                    () -> offer(runtime, sale, clock.instant().plusSeconds(secondsFromNow), 30));
        }
    }

    @Test
    void offerSlot_exactlySixtyDaysAhead_addsSlot() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "alice", "bobby", "Chairs");
            Instant limit = clock.instant().plus(Duration.ofDays(60));
            assertEquals(1, offer(runtime, sale, limit, 30).offeredSlots().size());
        }
    }

    @Test
    void offerSlot_moreThanSixtyDaysAhead_reportsValidation() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "alice", "bobby", "Chairs");
            Instant tooFar = clock.instant().plus(Duration.ofDays(60)).plusSeconds(60);
            var failure = assertFailure(ServiceException.Code.VALIDATION, () -> offer(runtime, sale, tooFar, 30));
            assertTrue(failure.getMessage().contains("60 days"), failure.getMessage());
        }
    }

    @Test
    void offerSlot_tooShort_reportsValidation() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "alice", "bobby", "Chairs");
            assertFailure(ServiceException.Code.VALIDATION, () -> offer(runtime, sale, TOMORROW, 14));
        }
    }

    @Test
    void offerSlot_fourthSlot_reportsValidationAfterThreeSucceed() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "alice", "bobby", "Chairs");
            offer(runtime, sale, TOMORROW, 30);
            offer(runtime, sale, TOMORROW.plusSeconds(3600), 30);
            assertEquals(3, offer(runtime, sale, TOMORROW.plusSeconds(7200), 30).offeredSlots().size());
            var failure = assertFailure(ServiceException.Code.VALIDATION,
                    () -> offer(runtime, sale, TOMORROW.plusSeconds(10_800), 30));
            assertTrue(failure.getMessage().contains("3"), failure.getMessage());
        }
    }

    @Test
    void offerSlot_overlappingSlotForSameSale_reportsInvalidState() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "alice", "bobby", "Chairs");
            offer(runtime, sale, TOMORROW, 60);
            assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> offer(runtime, sale, TOMORROW.plusSeconds(1800), 60));
        }
    }

    @Test
    void offerSlot_overlappingSellersScheduledMeetup_reportsInvalidState() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID first = agreedSale(runtime, "alice", "bobby", "Chairs");
            book(runtime, first, "bobby", TOMORROW);
            UUID second = agreedSale(runtime, "alice", "carol", "Desk");
            assertFailure(ServiceException.Code.INVALID_STATE, () -> offer(runtime, second, TOMORROW, 30));
        }
    }

    @Test
    void offerSlot_saleAlreadyHasMeetup_reportsInvalidState() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "alice", "bobby", "Chairs");
            book(runtime, sale, "bobby", TOMORROW);
            assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> offer(runtime, sale, TOMORROW.plusSeconds(86_400), 30));
        }
    }

    @Test
    void offerSlot_cancelledSale_reportsInvalidState() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "alice", "bobby", "Chairs");
            runtime.getTransactions().cancelSale(sale).join();
            assertFailure(ServiceException.Code.INVALID_STATE, () -> offer(runtime, sale, TOMORROW, 30));
        }
    }

    @Test
    void withdrawSlot_seller_removesSlot() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "alice", "bobby", "Chairs");
            UUID slot = offer(runtime, sale, TOMORROW, 30).offeredSlots().get(0).id();
            assertEquals(List.of(), runtime.getMeetups().withdrawSlot(slot).join().offeredSlots());
        }
    }

    @Test
    void withdrawSlot_buyer_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "alice", "bobby", "Chairs");
            UUID slot = offer(runtime, sale, TOMORROW, 30).offeredSlots().get(0).id();
            loginAs(runtime, "bobby");
            assertFailure(ServiceException.Code.PERMISSION, () -> runtime.getMeetups().withdrawSlot(slot).join());
        }
    }

    @Test
    void bookSlot_buyer_schedulesMeetupAndDeletesOtherSlots() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "alice", "bobby", "Chairs");
            offer(runtime, sale, TOMORROW, 30);
            UUID chosen = offer(runtime, sale, TOMORROW.plusSeconds(3600), 30).offeredSlots().get(1).id();
            loginAs(runtime, "bobby");
            var summary = runtime.getMeetups().bookSlot(chosen).join();
            var meetup = summary.meetup().orElseThrow();
            assertEquals(MeetupStatus.SCHEDULED, meetup.getStatus());
            assertEquals(TOMORROW.plusSeconds(3600), meetup.getTime().startAt());
            assertEquals(List.of(), summary.offeredSlots());
        }
    }

    @Test
    void bookSlot_seller_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "alice", "bobby", "Chairs");
            UUID slot = offer(runtime, sale, TOMORROW, 30).offeredSlots().get(0).id();
            assertFailure(ServiceException.Code.PERMISSION, () -> runtime.getMeetups().bookSlot(slot).join());
        }
    }

    @Test
    void bookSlot_slotTimeHasPassed_reportsInvalidState() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "alice", "bobby", "Chairs");
            UUID slot = offer(runtime, sale, TOMORROW, 30).offeredSlots().get(0).id();
            clock.advanceSeconds(2 * 86_400);
            loginAs(runtime, "bobby");
            assertFailure(ServiceException.Code.INVALID_STATE, () -> runtime.getMeetups().bookSlot(slot).join());
        }
    }

    @Test
    void bookSlot_buyerHasOverlappingMeetupAsBuyer_reportsInvalidState() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID fromAlice = agreedSale(runtime, "alice", "bobby", "Chairs");
            book(runtime, fromAlice, "bobby", TOMORROW);
            UUID fromCarol = agreedSale(runtime, "carol", "bobby", "Lamp");
            UUID slot = offer(runtime, fromCarol, TOMORROW, 30).offeredSlots().get(0).id();
            loginAs(runtime, "bobby");
            assertFailure(ServiceException.Code.INVALID_STATE, () -> runtime.getMeetups().bookSlot(slot).join());
        }
    }

    @Test
    void bookSlot_sameTimeOfferedToTwoBuyers_firstBookingWins() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID bobSale = agreedSale(runtime, "alice", "bobby", "Chairs");
            UUID bobSlot = offer(runtime, bobSale, TOMORROW, 30).offeredSlots().get(0).id();
            UUID carolSale = agreedSale(runtime, "alice", "carol", "Desk");
            UUID carolSlot = offer(runtime, carolSale, TOMORROW, 30).offeredSlots().get(0).id();
            loginAs(runtime, "bobby");
            runtime.getMeetups().bookSlot(bobSlot).join();
            loginAs(runtime, "carol");
            var failure = assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> runtime.getMeetups().bookSlot(carolSlot).join());
            assertTrue(failure.getMessage().startsWith("The seller already has a meetup"), failure.getMessage());
        }
    }

    @Test
    void bookSlot_buyerIsSellerInOverlappingMeetup_reportsInvalidState() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID bobSelling = agreedSale(runtime, "bobby", "carol", "Lamp");
            book(runtime, bobSelling, "carol", TOMORROW);
            UUID bobBuying = agreedSale(runtime, "alice", "bobby", "Chairs");
            UUID slot = offer(runtime, bobBuying, TOMORROW, 30).offeredSlots().get(0).id();
            loginAs(runtime, "bobby");
            var failure = assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> runtime.getMeetups().bookSlot(slot).join());
            assertTrue(failure.getMessage().startsWith("You already have a meetup"), failure.getMessage());
        }
    }

    @Test
    void acceptMove_otherParticipant_movesMeetup() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID meetup = bookedMeetup(runtime);
            loginAs(runtime, "bobby");
            var proposed = propose(runtime, meetup, LATER);
            assertTrue(proposed.meetup().orElseThrow().getPendingProposal().isPresent());
            loginAs(runtime, "alice");
            var moved = runtime.getMeetups().acceptMove(meetup).join().meetup().orElseThrow();
            assertEquals(LATER, moved.getTime().startAt());
            assertEquals("Canteen", moved.getTime().location());
            assertTrue(moved.getPendingProposal().isEmpty());
        }
    }

    @Test
    void acceptMove_proposer_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID meetup = bookedMeetup(runtime);
            propose(runtime, meetup, LATER);
            assertFailure(ServiceException.Code.PERMISSION, () -> runtime.getMeetups().acceptMove(meetup).join());
        }
    }

    @Test
    void acceptMove_proposedTimeNowOverlapsAnotherMeetup_reportsInvalidState() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID meetup = bookedMeetup(runtime);
            UUID sale = saleOf(runtime, meetup);
            propose(runtime, meetup, LATER);
            UUID other = agreedSale(runtime, "alice", "carol", "Desk");
            book(runtime, other, "carol", LATER);
            loginAs(runtime, "bobby");
            assertFailure(ServiceException.Code.INVALID_STATE, () -> runtime.getMeetups().acceptMove(meetup).join());
            assertEquals(TOMORROW, runtime.getMeetups().getMeetupSummary(sale).join()
                    .meetup().orElseThrow().getTime().startAt());
        }
    }

    @Test
    void rejectMove_otherParticipant_keepsTime() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID meetup = bookedMeetup(runtime);
            propose(runtime, meetup, LATER);
            loginAs(runtime, "bobby");
            var kept = runtime.getMeetups().rejectMove(meetup).join().meetup().orElseThrow();
            assertEquals(TOMORROW, kept.getTime().startAt());
            assertTrue(kept.getPendingProposal().isEmpty());
        }
    }

    @Test
    void withdrawMove_proposer_keepsTimeAndAllowsNewProposal() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID meetup = bookedMeetup(runtime);
            propose(runtime, meetup, LATER);
            assertEquals(TOMORROW, runtime.getMeetups().withdrawMove(meetup).join()
                    .meetup().orElseThrow().getTime().startAt());
            propose(runtime, meetup, LATER);
        }
    }

    @Test
    void withdrawMove_otherParticipant_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID meetup = bookedMeetup(runtime);
            propose(runtime, meetup, LATER);
            loginAs(runtime, "bobby");
            assertFailure(ServiceException.Code.PERMISSION, () -> runtime.getMeetups().withdrawMove(meetup).join());
        }
    }

    @Test
    void proposeMove_pendingProposal_reportsInvalidState() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID meetup = bookedMeetup(runtime);
            propose(runtime, meetup, LATER);
            loginAs(runtime, "bobby");
            assertFailure(ServiceException.Code.INVALID_STATE,
                    () -> propose(runtime, meetup, LATER.plusSeconds(86_400)));
        }
    }

    @Test
    void proposeMove_timeInPast_reportsValidation() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID meetup = bookedMeetup(runtime);
            assertFailure(ServiceException.Code.VALIDATION, () -> propose(runtime, meetup, START.minusSeconds(60)));
        }
    }

    @Test
    void proposeMove_nonParticipant_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID meetup = bookedMeetup(runtime);
            signIn(runtime, "carol");
            assertFailure(ServiceException.Code.PERMISSION, () -> propose(runtime, meetup, LATER));
        }
    }

    @Test
    void acceptMove_noPendingProposal_reportsInvalidState() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID meetup = bookedMeetup(runtime);
            assertFailure(ServiceException.Code.INVALID_STATE, () -> runtime.getMeetups().acceptMove(meetup).join());
        }
    }

    @Test
    void rejectMove_noPendingProposal_reportsInvalidState() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID meetup = bookedMeetup(runtime);
            assertFailure(ServiceException.Code.INVALID_STATE, () -> runtime.getMeetups().rejectMove(meetup).join());
        }
    }

    @Test
    void cancelMeetup_buyer_cancelsAndLetsSellerOfferNewTimes() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID meetup = bookedMeetup(runtime);
            UUID sale = saleOf(runtime, meetup);
            loginAs(runtime, "bobby");
            assertTrue(runtime.getMeetups().cancelMeetup(meetup).join().meetup().isEmpty());
            loginAs(runtime, "alice");
            assertEquals(1, offer(runtime, sale, TOMORROW, 30).offeredSlots().size());
        }
    }

    @Test
    void cancelMeetup_nonParticipant_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID meetup = bookedMeetup(runtime);
            signIn(runtime, "carol");
            assertFailure(ServiceException.Code.PERMISSION, () -> runtime.getMeetups().cancelMeetup(meetup).join());
        }
    }

    @Test
    void cancelMeetup_unknownMeetup_reportsNotFound() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            signIn(runtime, "alice");
            assertFailure(ServiceException.Code.NOT_FOUND,
                    () -> runtime.getMeetups().cancelMeetup(UUID.randomUUID()).join());
        }
    }

    @Test
    void proposeMove_unknownMeetup_reportsNotFound() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            signIn(runtime, "alice");
            assertFailure(ServiceException.Code.NOT_FOUND, () -> propose(runtime, UUID.randomUUID(), LATER));
        }
    }

    @Test
    void getMySales_pendingCancellationRequest_outranksMeetupStep() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID meetup = bookedMeetup(runtime);
            UUID sale = saleOf(runtime, meetup);
            runtime.getTransactions().confirmCompletion(sale).join();
            runtime.getTransactions().requestCancellation(sale).join();
            assertEquals(NextStep.WAIT_FOR_CANCELLATION_RESPONSE,
                    runtime.getTransactions().getMySales().join().get(0).nextStep());
        }
    }

    @Test
    void getMySales_viewerHasConfirmed_waitsForConfirmationDespiteMeetup() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID meetup = bookedMeetup(runtime);
            runtime.getTransactions().confirmCompletion(saleOf(runtime, meetup)).join();
            assertEquals(NextStep.WAIT_FOR_CONFIRMATION,
                    runtime.getTransactions().getMySales().join().get(0).nextStep());
        }
    }

    @Test
    void confirmCompletion_saleWithMeetup_completesMeetupAndKeepsIt() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID meetup = bookedMeetup(runtime);
            UUID sale = saleOf(runtime, meetup);
            runtime.getTransactions().confirmCompletion(sale).join();
            loginAs(runtime, "bobby");
            runtime.getTransactions().confirmCompletion(sale).join();
            var kept = runtime.getMeetups().getMeetupSummary(sale).join().meetup().orElseThrow();
            assertEquals(meetup, kept.getId());
            assertEquals(MeetupStatus.COMPLETED, kept.getStatus());
        }
    }

    @Test
    void cancelSale_saleWithOfferedSlots_deletesSlots() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "alice", "bobby", "Chairs");
            offer(runtime, sale, TOMORROW, 30);
            runtime.getTransactions().cancelSale(sale).join();
            assertEquals(List.of(), runtime.getMeetups().getMeetupSummary(sale).join().offeredSlots());
        }
    }

    @Test
    void acceptCancellation_saleWithMeetupAndPendingMove_cancelsMeetup() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID meetup = bookedMeetup(runtime);
            UUID sale = saleOf(runtime, meetup);
            propose(runtime, meetup, LATER);
            runtime.getTransactions().confirmCompletion(sale).join();
            runtime.getTransactions().requestCancellation(sale).join();
            loginAs(runtime, "bobby");
            runtime.getTransactions().acceptCancellation(sale).join();
            assertTrue(runtime.getMeetups().getMeetupSummary(sale).join().meetup().isEmpty());
            assertFailure(ServiceException.Code.INVALID_STATE, () -> runtime.getMeetups().acceptMove(meetup).join());
        }
    }

    @Test
    void getMySales_offeredSlots_showsChoiceStepsAndMeetupSummary() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "alice", "bobby", "Chairs");
            offer(runtime, sale, TOMORROW, 30);
            var seller = runtime.getTransactions().getMySales().join().get(0);
            assertEquals(NextStep.WAIT_FOR_MEETUP_CHOICE, seller.nextStep());
            assertEquals(1, seller.meetup().offeredSlots().size());
            loginAs(runtime, "bobby");
            assertEquals(NextStep.CHOOSE_MEETUP_TIME, runtime.getTransactions().getMyPurchases().join().get(0)
                    .nextStep());
        }
    }

    @Test
    void getMySales_bookedMeetup_showsMeetStepUntilItPasses() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            bookedMeetup(runtime);
            assertEquals(NextStep.MEET_THEN_CONFIRM, runtime.getTransactions().getMySales().join().get(0).nextStep());
            clock.advanceSeconds(2 * 86_400);
            assertEquals(NextStep.CONFIRM_AFTER_PAST_MEETUP,
                    runtime.getTransactions().getMySales().join().get(0).nextStep());
        }
    }

    @Test
    void getMyPurchases_pendingMoveFromBuyer_showsWaitingAndRespondSteps() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID meetup = bookedMeetup(runtime);
            loginAs(runtime, "bobby");
            propose(runtime, meetup, LATER);
            assertEquals(NextStep.WAIT_FOR_MOVE_RESPONSE,
                    runtime.getTransactions().getMyPurchases().join().get(0).nextStep());
            loginAs(runtime, "alice");
            assertEquals(NextStep.RESPOND_TO_MOVE_PROPOSAL,
                    runtime.getTransactions().getMySales().join().get(0).nextStep());
        }
    }

    @Test
    void getSalesDashboard_upcomingMeetup_countsItUntilItStarts() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            bookedMeetup(runtime);
            assertEquals(1, runtime.getTransactions().getSalesDashboard().join().upcomingMeetups());
            clock.advanceSeconds(2 * 86_400);
            assertEquals(0, runtime.getTransactions().getSalesDashboard().join().upcomingMeetups());
        }
    }

    @Test
    void getMyListings_reservedListingWithMeetup_carriesMeetupSummary() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            bookedMeetup(runtime);
            runtime.getListings().createListing(draft("Desk", 9000), List.of()).join();
            var mine = runtime.getListings().getMyListings().join();
            assertEquals(TOMORROW, mine.get(0).meetup().orElseThrow().meetup().orElseThrow().getTime().startAt());
            assertTrue(mine.get(1).meetup().isEmpty());
        }
    }

    @Test
    void getMeetupSummary_nonParticipant_reportsPermission() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "alice", "bobby", "Chairs");
            registerAndLogin(runtime, "carol");
            assertFailure(ServiceException.Code.PERMISSION,
                    () -> runtime.getMeetups().getMeetupSummary(sale).join());
        }
    }

    @Test
    void getMeetupSummary_pastSlot_isNotShown() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            UUID sale = agreedSale(runtime, "alice", "bobby", "Chairs");
            offer(runtime, sale, TOMORROW, 30);
            clock.advanceSeconds(2 * 86_400);
            assertEquals(List.of(), runtime.getMeetups().getMeetupSummary(sale).join().offeredSlots());
        }
    }

    @Test
    void bookSlot_reopenedDatabase_preservesMeetup() throws Exception {
        UUID sale;
        try (ApplicationRuntime runtime = open()) {
            sale = agreedSale(runtime, "alice", "bobby", "Chairs");
            book(runtime, sale, "bobby", TOMORROW);
        }
        try (ApplicationRuntime runtime = open()) {
            runtime.getAccounts().login("bobby", PASSWORD).join();
            var meetup = runtime.getMeetups().getMeetupSummary(sale).join().meetup().orElseThrow();
            assertEquals(TOMORROW, meetup.getTime().startAt());
            assertEquals("Library", meetup.getTime().location());
        }
    }

    @Test
    void meetupOperations_loggedOut_rejectAccess() throws Exception {
        try (ApplicationRuntime runtime = open()) {
            var meetups = runtime.getMeetups();
            UUID id = UUID.randomUUID();
            assertFailure(ServiceException.Code.SESSION,
                    () -> meetups.offerSlot(id, TOMORROW, TOMORROW.plusSeconds(1800), "Library").join());
            assertFailure(ServiceException.Code.SESSION, () -> meetups.withdrawSlot(id).join());
            assertFailure(ServiceException.Code.SESSION, () -> meetups.bookSlot(id).join());
            assertFailure(ServiceException.Code.SESSION, () -> meetups.getMeetupSummary(id).join());
        }
    }

    /**
     * The seller lists an item, the buyer offers, and the seller accepts. Users are registered the first
     * time they appear. The seller is left logged in and the sale ID returned.
     */
    UUID agreedSale(ApplicationRuntime runtime, String seller, String buyer, String title) {
        signIn(runtime, seller);
        UUID listing = runtime.getListings().createListing(draft(title, 5000), List.of()).join().listing().getId();
        signIn(runtime, buyer);
        UUID offer = runtime.getOffers().submitOffer(listing, 4000).join().offer().getId();
        signIn(runtime, seller);
        return runtime.getOffers().acceptOffer(offer).join().transactionId();
    }

    /** Offers a slot as the logged-in seller with the default location. */
    MeetupSummary offer(ApplicationRuntime runtime, UUID sale, Instant start, long minutes) {
        return runtime.getMeetups().offerSlot(sale, start, start.plus(Duration.ofMinutes(minutes)), "Library").join();
    }

    /** Alice sells chairs to Bob and Bob books tomorrow's slot; returns the meetup ID with Alice logged in. */
    private UUID bookedMeetup(ApplicationRuntime runtime) {
        UUID sale = agreedSale(runtime, "alice", "bobby", "Chairs");
        book(runtime, sale, "bobby", TOMORROW);
        return runtime.getMeetups().getMeetupSummary(sale).join().meetup().orElseThrow().getId();
    }

    /** Proposes moving the meetup to a 30-minute time at the canteen, as the logged-in participant. */
    private MeetupSummary propose(ApplicationRuntime runtime, UUID meetup, Instant start) {
        return runtime.getMeetups().proposeMove(meetup, start, start.plus(Duration.ofMinutes(30)), "Canteen").join();
    }

    private UUID saleOf(ApplicationRuntime runtime, UUID meetup) {
        return runtime.getTransactions().getMySales().join().stream()
                .map(entry -> entry.sale().getId())
                .filter(sale -> runtime.getMeetups().getMeetupSummary(sale).join().meetup()
                        .map(found -> found.getId().equals(meetup)).orElse(false))
                .findFirst().orElseThrow();
    }

    /** The logged-in seller offers one 30-minute slot and the buyer books it; the seller is left logged in. */
    private void book(ApplicationRuntime runtime, UUID sale, String buyer, Instant start) {
        String seller = runtime.getAccounts().getOwnProfile().join().getUsername();
        UUID slot = offer(runtime, sale, start, 30).offeredSlots().get(0).id();
        loginAs(runtime, buyer);
        runtime.getMeetups().bookSlot(slot).join();
        loginAs(runtime, seller);
    }

    private void signIn(ApplicationRuntime runtime, String username) {
        if (registered.add(username)) {
            registerAndLogin(runtime, username);
        } else {
            loginAs(runtime, username);
        }
    }

    private ApplicationRuntime open() throws Exception {
        return ApplicationRuntime.open(directory, clock);
    }
}
