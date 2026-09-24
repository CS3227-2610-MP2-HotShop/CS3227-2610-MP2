package hotshop.model;

import static hotshop.model.MeetupTimeTest.START;
import static hotshop.model.MeetupTimeTest.lasting;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class MeetupTest {
    private static final UUID SALE = UUID.fromString("00000000-0000-0000-0000-0000000000cc");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Instant BOOKED = START.minusSeconds(86_400);
    private static final MeetupTime LATER = new MeetupTime(START.plusSeconds(86_400),
            START.plusSeconds(88_200), "Canteen");

    private Meetup booked() {
        MeetupSlot slot = MeetupSlot.offer(SALE, lasting(30), BOOKED.minusSeconds(60));
        return Meetup.book(slot, BUYER, SELLER, BOOKED);
    }

    @Test
    void book_offeredSlot_copiesTimeAndIsScheduled() {
        Meetup meetup = booked();
        assertEquals(SALE, meetup.getTransactionId());
        assertEquals(lasting(30), meetup.getTime());
        assertEquals(MeetupStatus.SCHEDULED, meetup.getStatus());
        assertTrue(meetup.getPendingProposal().isEmpty());
    }

    @Test
    void acceptMove_otherParticipant_movesMeetup() {
        Meetup meetup = booked();
        RescheduleProposal proposal = meetup.proposeMove(SELLER, LATER, BOOKED.plusSeconds(60));
        meetup.acceptMove(proposal.getId(), BUYER, BOOKED.plusSeconds(120));
        assertEquals(LATER, meetup.getTime());
        assertEquals(ProposalStatus.ACCEPTED, meetup.getProposals().getFirst().getStatus());
        assertTrue(meetup.getPendingProposal().isEmpty());
    }

    @Test
    void acceptMove_proposer_throwsException() {
        Meetup meetup = booked();
        RescheduleProposal proposal = meetup.proposeMove(BUYER, LATER, BOOKED.plusSeconds(60));
        assertThrows(IllegalArgumentException.class,
                () -> meetup.acceptMove(proposal.getId(), BUYER, BOOKED.plusSeconds(120)));
        assertEquals(lasting(30), meetup.getTime());
    }

    @Test
    void rejectMove_otherParticipant_keepsTime() {
        Meetup meetup = booked();
        RescheduleProposal proposal = meetup.proposeMove(BUYER, LATER, BOOKED.plusSeconds(60));
        meetup.rejectMove(proposal.getId(), SELLER, BOOKED.plusSeconds(120));
        assertEquals(lasting(30), meetup.getTime());
        assertEquals(ProposalStatus.REJECTED, meetup.getProposals().getFirst().getStatus());
    }

    @Test
    void withdrawMove_proposer_keepsTimeAndAllowsNewProposal() {
        Meetup meetup = booked();
        RescheduleProposal proposal = meetup.proposeMove(BUYER, LATER, BOOKED.plusSeconds(60));
        meetup.withdrawMove(proposal.getId(), BUYER, BOOKED.plusSeconds(120));
        meetup.proposeMove(SELLER, LATER, BOOKED.plusSeconds(180));
        assertEquals(2, meetup.getProposals().size());
    }

    @Test
    void withdrawMove_otherParticipant_throwsException() {
        Meetup meetup = booked();
        RescheduleProposal proposal = meetup.proposeMove(BUYER, LATER, BOOKED.plusSeconds(60));
        assertThrows(IllegalArgumentException.class,
                () -> meetup.withdrawMove(proposal.getId(), SELLER, BOOKED.plusSeconds(120)));
    }

    @Test
    void proposeMove_pendingProposal_throwsException() {
        Meetup meetup = booked();
        meetup.proposeMove(BUYER, LATER, BOOKED.plusSeconds(60));
        assertThrows(IllegalStateException.class, () -> meetup.proposeMove(SELLER, LATER, BOOKED.plusSeconds(120)));
    }

    @Test
    void proposeMove_nonparticipant_throwsException() {
        Meetup meetup = booked();
        assertThrows(IllegalArgumentException.class,
                () -> meetup.proposeMove(STRANGER, LATER, BOOKED.plusSeconds(60)));
    }

    @Test
    void cancel_pendingProposal_cancelsAndWithdrawsProposal() {
        Meetup meetup = booked();
        meetup.proposeMove(BUYER, LATER, BOOKED.plusSeconds(60));
        meetup.cancel(BOOKED.plusSeconds(120));
        assertEquals(MeetupStatus.CANCELLED, meetup.getStatus());
        assertEquals(ProposalStatus.WITHDRAWN, meetup.getProposals().getFirst().getStatus());
    }

    @Test
    void complete_scheduledMeetup_completes() {
        Meetup meetup = booked();
        meetup.complete(BOOKED.plusSeconds(60));
        assertEquals(MeetupStatus.COMPLETED, meetup.getStatus());
    }

    @Test
    void proposeMove_cancelledMeetup_throwsException() {
        Meetup meetup = booked();
        meetup.cancel(BOOKED.plusSeconds(60));
        assertThrows(IllegalStateException.class, () -> meetup.proposeMove(BUYER, LATER, BOOKED.plusSeconds(120)));
    }

    @Test
    void cancel_completedMeetup_throwsException() {
        Meetup meetup = booked();
        meetup.complete(BOOKED.plusSeconds(60));
        assertThrows(IllegalStateException.class, () -> meetup.cancel(BOOKED.plusSeconds(120)));
    }

    @Test
    void acceptMove_timeBeforeProposal_throwsWithoutMoving() {
        Meetup meetup = booked();
        RescheduleProposal proposal = meetup.proposeMove(SELLER, LATER, BOOKED.plusSeconds(60));
        assertThrows(IllegalArgumentException.class,
                () -> meetup.acceptMove(proposal.getId(), BUYER, BOOKED.plusSeconds(30)));
        assertEquals(lasting(30), meetup.getTime());
    }

    @Test
    void restore_savedMeetupWithHistory_restoresState() {
        UUID id = UUID.randomUUID();
        var rejected = RescheduleProposal.restore(UUID.randomUUID(), id, BUYER, LATER, BOOKED.plusSeconds(60),
                ProposalStatus.REJECTED, BOOKED.plusSeconds(120));
        Meetup meetup = Meetup.restore(new Meetup.Snapshot(id, SALE, BUYER, SELLER, lasting(30),
                MeetupStatus.SCHEDULED, BOOKED, List.of(rejected)));
        assertEquals(id, meetup.getId());
        assertEquals(1, meetup.getProposals().size());
        meetup.proposeMove(SELLER, LATER, BOOKED.plusSeconds(180));
    }

    @Test
    void restore_pendingProposalBeforeLatest_throwsException() {
        UUID id = UUID.randomUUID();
        var first = RescheduleProposal.restore(UUID.randomUUID(), id, BUYER, LATER, BOOKED.plusSeconds(60),
                ProposalStatus.PENDING, null);
        var second = RescheduleProposal.restore(UUID.randomUUID(), id, SELLER, LATER, BOOKED.plusSeconds(120),
                ProposalStatus.PENDING, null);
        assertThrows(IllegalArgumentException.class, () -> Meetup.restore(new Meetup.Snapshot(id, SALE, BUYER,
                SELLER, lasting(30), MeetupStatus.SCHEDULED, BOOKED, List.of(first, second))));
    }

    @Test
    void restore_pendingProposalOnCancelledMeetup_throwsException() {
        UUID id = UUID.randomUUID();
        var pending = RescheduleProposal.restore(UUID.randomUUID(), id, BUYER, LATER, BOOKED.plusSeconds(60),
                ProposalStatus.PENDING, null);
        assertThrows(IllegalArgumentException.class, () -> Meetup.restore(new Meetup.Snapshot(id, SALE, BUYER,
                SELLER, lasting(30), MeetupStatus.CANCELLED, BOOKED, List.of(pending))));
    }
}
