package hotshop.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import hotshop.model.Meetup;
import hotshop.model.MeetupSlot;
import hotshop.model.MeetupTime;
import hotshop.model.TransactionStatus;
import hotshop.service.MeetupSummary;
import hotshop.service.SaleRole;

class MeetupBarTest {
    private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");
    private static final Instant NOW = Instant.parse("2026-09-26T02:00:00Z");
    private static final UUID SALE = UUID.randomUUID();
    private static final UUID BUYER = UUID.randomUUID();
    private static final UUID SELLER = UUID.randomUUID();
    private static final MeetupTime FRIDAY = new MeetupTime(Instant.parse("2026-10-02T06:00:00Z"),
            Instant.parse("2026-10-02T06:30:00Z"), "Library lobby");
    private static final MeetupTime SATURDAY = new MeetupTime(Instant.parse("2026-10-03T07:00:00Z"),
            Instant.parse("2026-10-03T07:30:00Z"), "Campus gate");

    @Test
    void of_sellerActiveSaleWithoutTimes_offersOfferTime() {
        MeetupBar bar = bar(SaleRole.SELLER, SELLER, summary(List.of(), null), TransactionStatus.ACTIVE);
        assertEquals("No meetup times offered yet", bar.text());
        assertEquals(List.of(MeetupBar.Action.OFFER_TIME, MeetupBar.Action.VIEW_SALE), bar.actions());
    }

    @Test
    void of_buyerActiveSaleWithoutTimes_waitsForSeller() {
        MeetupBar bar = bar(SaleRole.BUYER, BUYER, summary(List.of(), null), TransactionStatus.ACTIVE);
        assertEquals("Waiting for the seller to offer meetup times", bar.text());
        assertEquals(List.of(MeetupBar.Action.VIEW_SALE), bar.actions());
    }

    @Test
    void of_sellerWithTwoTimesOffered_offersViewTimesAndAnotherTime() {
        MeetupBar bar = bar(SaleRole.SELLER, SELLER, summary(slots(2), null), TransactionStatus.ACTIVE);
        assertEquals("2 times offered", bar.text());
        assertEquals(List.of(MeetupBar.Action.VIEW_TIMES, MeetupBar.Action.OFFER_TIME, MeetupBar.Action.VIEW_SALE),
                bar.actions());
    }

    @Test
    void of_sellerWithThreeTimesOffered_offersNoFurtherTime() {
        MeetupBar bar = bar(SaleRole.SELLER, SELLER, summary(slots(3), null), TransactionStatus.ACTIVE);
        assertEquals("3 times offered", bar.text());
        assertEquals(List.of(MeetupBar.Action.VIEW_TIMES, MeetupBar.Action.VIEW_SALE), bar.actions());
    }

    @Test
    void of_buyerWithOneTimeOffered_offersChooseTime() {
        MeetupBar bar = bar(SaleRole.BUYER, BUYER, summary(slots(1), null), TransactionStatus.ACTIVE);
        assertEquals("1 time offered", bar.text());
        assertEquals(List.of(MeetupBar.Action.CHOOSE_TIME, MeetupBar.Action.VIEW_SALE), bar.actions());
    }

    @Test
    void of_bookedUpcomingMeetup_showsTimeAndOffersMoveAndCancel() {
        MeetupBar bar = bar(SaleRole.BUYER, BUYER, summary(List.of(), booked()), TransactionStatus.ACTIVE);
        assertEquals("Meetup: Fri 2 Oct 2026, 14:00 to 14:30 · Library lobby", bar.text());
        assertEquals(List.of(MeetupBar.Action.PROPOSE_MOVE, MeetupBar.Action.CANCEL_MEETUP,
                MeetupBar.Action.VIEW_SALE), bar.actions());
    }

    @Test
    void of_moveProposedByOtherParticipant_offersAcceptAndReject() {
        Meetup meetup = booked();
        meetup.proposeMove(SELLER, SATURDAY, NOW);
        MeetupBar bar = bar(SaleRole.BUYER, BUYER, summary(List.of(), meetup), TransactionStatus.ACTIVE);
        assertEquals("Bob proposed moving the meetup to Sat 3 Oct 2026, 15:00 to 15:30 · Campus gate", bar.text());
        assertEquals(List.of(MeetupBar.Action.ACCEPT_MOVE, MeetupBar.Action.REJECT_MOVE,
                MeetupBar.Action.VIEW_SALE), bar.actions());
    }

    @Test
    void of_moveProposedByViewer_offersWithdrawProposal() {
        Meetup meetup = booked();
        meetup.proposeMove(BUYER, SATURDAY, NOW);
        MeetupBar bar = bar(SaleRole.BUYER, BUYER, summary(List.of(), meetup), TransactionStatus.ACTIVE);
        assertEquals("You proposed moving the meetup to Sat 3 Oct 2026, 15:00 to 15:30 · Campus gate", bar.text());
        assertEquals(List.of(MeetupBar.Action.WITHDRAW_MOVE, MeetupBar.Action.VIEW_SALE), bar.actions());
    }

    @Test
    void of_bookedMeetupWhoseEndHasPassed_asksToConfirmOnTheSale() {
        Instant afterEnd = FRIDAY.endAt().plusSeconds(60);
        MeetupBar bar = MeetupBar.of(SaleRole.SELLER, SELLER, "Bob", summary(List.of(), booked()),
                TransactionStatus.ACTIVE, afterEnd, SINGAPORE);
        assertEquals("The meetup time has passed. Confirm completion on the sale if the handover happened",
                bar.text());
        assertEquals(List.of(MeetupBar.Action.VIEW_SALE), bar.actions());
    }

    @Test
    void of_bookedMeetupAtItsExactEnd_countsAsPassed() {
        MeetupBar bar = MeetupBar.of(SaleRole.SELLER, SELLER, "Bob", summary(List.of(), booked()),
                TransactionStatus.ACTIVE, FRIDAY.endAt(), SINGAPORE);
        assertEquals(List.of(MeetupBar.Action.VIEW_SALE), bar.actions());
    }

    @Test
    void of_bookedMeetupJustBeforeItsEnd_stillOffersMoveAndCancel() {
        MeetupBar bar = MeetupBar.of(SaleRole.SELLER, SELLER, "Bob", summary(List.of(), booked()),
                TransactionStatus.ACTIVE, FRIDAY.endAt().minusSeconds(1), SINGAPORE);
        assertEquals(List.of(MeetupBar.Action.PROPOSE_MOVE, MeetupBar.Action.CANCEL_MEETUP,
                MeetupBar.Action.VIEW_SALE), bar.actions());
    }

    @Test
    void of_completedSaleWithMeetup_showsWhereTheyMet() {
        Meetup meetup = booked();
        meetup.complete(NOW);
        MeetupBar bar = bar(SaleRole.SELLER, SELLER, summary(List.of(), meetup), TransactionStatus.COMPLETED);
        assertEquals("Sale completed · Met on Fri 2 Oct 2026, 14:00 to 14:30 · Library lobby", bar.text());
        assertEquals(List.of(MeetupBar.Action.VIEW_SALE), bar.actions());
    }

    @Test
    void of_completedSaleWithoutMeetup_showsSaleCompleted() {
        MeetupBar bar = bar(SaleRole.BUYER, BUYER, summary(List.of(), null), TransactionStatus.COMPLETED);
        assertEquals("Sale completed", bar.text());
        assertEquals(List.of(MeetupBar.Action.VIEW_SALE), bar.actions());
    }

    @Test
    void replacesOfferBar_activeOrCompletedSale_returnsTrue() {
        assertTrue(MeetupBar.replacesOfferBar(Optional.of(TransactionStatus.ACTIVE)));
        assertTrue(MeetupBar.replacesOfferBar(Optional.of(TransactionStatus.COMPLETED)));
    }

    @Test
    void replacesOfferBar_cancelledSale_returnsFalse() {
        assertFalse(MeetupBar.replacesOfferBar(Optional.of(TransactionStatus.CANCELLED)));
    }

    @Test
    void replacesOfferBar_noSale_returnsFalse() {
        assertFalse(MeetupBar.replacesOfferBar(Optional.empty()));
    }

    @Test
    void summary_bookedMeetup_showsTimeAndPlace() {
        assertEquals("Meetup: Fri 2 Oct 2026, 14:00 to 14:30 · Library lobby",
                MeetupBar.summary(summary(List.of(), booked()), SINGAPORE));
    }

    @Test
    void summary_completedMeetup_showsWhereTheyMet() {
        Meetup meetup = booked();
        meetup.complete(NOW);
        assertEquals("Met on Fri 2 Oct 2026, 14:00 to 14:30 · Library lobby",
                MeetupBar.summary(summary(List.of(), meetup), SINGAPORE));
    }

    @Test
    void summary_timesOffered_countsTimes() {
        assertEquals("2 times offered", MeetupBar.summary(summary(slots(2), null), SINGAPORE));
    }

    @Test
    void summary_nothingOffered_saysNoTimesYet() {
        assertEquals("No meetup times yet", MeetupBar.summary(summary(List.of(), null), SINGAPORE));
    }

    @Test
    void format_meetupCrossingMidnight_repeatsTheEndDate() {
        MeetupTime late = new MeetupTime(Instant.parse("2026-10-02T15:00:00Z"),
                Instant.parse("2026-10-02T17:00:00Z"), "Hall 3");
        assertEquals("Fri 2 Oct 2026, 23:00 to Sat 3 Oct 2026, 01:00 · Hall 3", MeetupBar.format(late, SINGAPORE));
    }

    private static Meetup booked() {
        return Meetup.book(MeetupSlot.offer(SALE, FRIDAY, NOW), BUYER, SELLER, NOW);
    }

    private static List<MeetupSlot> slots(int count) {
        return IntStream.range(0, count).mapToObj(index -> MeetupSlot.offer(SALE,
                new MeetupTime(FRIDAY.startAt().plusSeconds(3600L * index), FRIDAY.endAt().plusSeconds(3600L * index),
                        FRIDAY.location()), NOW)).toList();
    }

    private static MeetupBar bar(SaleRole role, UUID viewer, MeetupSummary summary, TransactionStatus status) {
        return MeetupBar.of(role, viewer, "Bob", summary, status, NOW, SINGAPORE);
    }

    private static MeetupSummary summary(List<MeetupSlot> slots, Meetup meetup) {
        return new MeetupSummary(SALE, slots, Optional.ofNullable(meetup));
    }
}
