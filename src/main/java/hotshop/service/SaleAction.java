package hotshop.service;

/** Sale operations a participant may perform right now; screens show only these. */
public enum SaleAction {
    CONFIRM_COMPLETION, CANCEL_SALE, REQUEST_CANCELLATION, ACCEPT_CANCELLATION, REJECT_CANCELLATION,
    WITHDRAW_CANCELLATION
}
