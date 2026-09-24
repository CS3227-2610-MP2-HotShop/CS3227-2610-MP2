package hotshop.service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import hotshop.model.CancellationRequest;
import hotshop.model.Meetup;
import hotshop.model.MeetupStatus;
import hotshop.model.RescheduleProposal;
import hotshop.model.Transaction;
import hotshop.model.TransactionStatus;

/** Turns a sale's state into a participant's next step, available actions, and list position. */
final class SaleProgress {
    private static final int RANK_AWAITING_CANCELLATION_RESPONSE = 0;
    private static final int RANK_OTHER_ACTIVE = 1;
    private static final int RANK_COMPLETED = 2;
    private static final int RANK_CANCELLED = 3;

    private SaleProgress() {
    }

    /**
     * Cancellation requests come first, then the viewer's own confirmation, then the meetup: a pending
     * move, a booked meetup (upcoming or past), offered slots, or nothing arranged yet.
     */
    static NextStep nextStep(Transaction sale, UUID viewerId, MeetupSummary meetup, Instant now) {
        if (sale.getStatus() != TransactionStatus.ACTIVE) {
            return NextStep.NONE;
        }
        Optional<CancellationRequest> pending = sale.getPendingCancellation();
        if (pending.isPresent()) {
            return pending.orElseThrow().getRequesterId().equals(viewerId)
                    ? NextStep.WAIT_FOR_CANCELLATION_RESPONSE : NextStep.RESPOND_TO_CANCELLATION_REQUEST;
        }
        if (sale.hasConfirmed(viewerId)) {
            return NextStep.WAIT_FOR_CONFIRMATION;
        }
        Optional<Meetup> scheduled = meetup.meetup().filter(found -> found.getStatus() == MeetupStatus.SCHEDULED);
        if (scheduled.isPresent()) {
            return meetupStep(scheduled.orElseThrow(), viewerId, now);
        }
        boolean isBuyer = viewerId.equals(sale.getBuyerId());
        if (!meetup.offeredSlots().isEmpty()) {
            return isBuyer ? NextStep.CHOOSE_MEETUP_TIME : NextStep.WAIT_FOR_MEETUP_CHOICE;
        }
        return isBuyer ? NextStep.WAIT_FOR_MEETUP_TIMES : NextStep.OFFER_MEETUP_TIMES;
    }

    private static NextStep meetupStep(Meetup meetup, UUID viewerId, Instant now) {
        Optional<RescheduleProposal> move = meetup.getPendingProposal();
        if (move.isPresent()) {
            return move.orElseThrow().getProposerId().equals(viewerId)
                    ? NextStep.WAIT_FOR_MOVE_RESPONSE : NextStep.RESPOND_TO_MOVE_PROPOSAL;
        }
        return meetup.getTime().endAt().isAfter(now) ? NextStep.MEET_THEN_CONFIRM : NextStep.CONFIRM_AFTER_PAST_MEETUP;
    }

    /** Uses the same state queries the model enforces, so screens never offer a refused action. */
    static Set<SaleAction> availableActions(Transaction sale, UUID viewerId) {
        if (sale.getStatus() != TransactionStatus.ACTIVE) {
            return Set.of();
        }
        Optional<CancellationRequest> pending = sale.getPendingCancellation();
        if (pending.isPresent()) {
            return pending.orElseThrow().getRequesterId().equals(viewerId)
                    ? Set.of(SaleAction.WITHDRAW_CANCELLATION)
                    : Set.of(SaleAction.ACCEPT_CANCELLATION, SaleAction.REJECT_CANCELLATION);
        }
        Set<SaleAction> actions = EnumSet.noneOf(SaleAction.class);
        if (!sale.hasConfirmed(viewerId)) {
            actions.add(SaleAction.CONFIRM_COMPLETION);
        }
        actions.add(sale.hasConfirmation() ? SaleAction.REQUEST_CANCELLATION : SaleAction.CANCEL_SALE);
        return Set.copyOf(actions);
    }

    /** Sales needing a response come first, then other active, completed, and cancelled sales. */
    static int rank(Transaction sale) {
        return switch (sale.getStatus()) {
            case ACTIVE -> sale.getPendingCancellation().isPresent()
                    ? RANK_AWAITING_CANCELLATION_RESPONSE : RANK_OTHER_ACTIVE;
            case COMPLETED -> RANK_COMPLETED;
            case CANCELLED -> RANK_CANCELLED;
        };
    }
}
