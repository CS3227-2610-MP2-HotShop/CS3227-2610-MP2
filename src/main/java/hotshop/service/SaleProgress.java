package hotshop.service;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import hotshop.model.CancellationRequest;
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

    static NextStep nextStep(Transaction sale, UUID viewerId) {
        if (sale.getStatus() != TransactionStatus.ACTIVE) {
            return NextStep.NONE;
        }
        Optional<CancellationRequest> pending = sale.getPendingCancellation();
        if (pending.isPresent()) {
            return pending.orElseThrow().getRequesterId().equals(viewerId)
                    ? NextStep.WAIT_FOR_CANCELLATION_RESPONSE : NextStep.RESPOND_TO_CANCELLATION_REQUEST;
        }
        return sale.hasConfirmed(viewerId) ? NextStep.WAIT_FOR_CONFIRMATION : NextStep.CONFIRM_AFTER_HANDOVER;
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
