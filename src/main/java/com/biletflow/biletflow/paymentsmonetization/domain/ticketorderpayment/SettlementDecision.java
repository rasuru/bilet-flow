package com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment;

/** Provider facts are mapped to business decisions without HTTP, persistence, or Spring dependencies. */
public final class SettlementDecision {

    private SettlementDecision() {}

    public enum Action {
        WAIT,
        CAPTURE,
        VOID,
        SUCCEED,
        FAIL,
        REFUND,
        REFUNDED,
        REVIEW,
    }

    public static Action decide(
        SettlementStatus status,
        boolean stopPayment,
        boolean paid,
        boolean wantsRefund,
        boolean mutationUncertain
    ) {
        return switch (status) {
            case CAPTURED -> !paid ? Action.SUCCEED : wantsRefund ? (mutationUncertain ? Action.WAIT : Action.REFUND) : Action.WAIT;
            case AUTHORIZED -> paid ? Action.REVIEW : mutationUncertain ? Action.WAIT : stopPayment ? Action.VOID : Action.CAPTURE;
            case REFUNDED -> Action.REFUNDED;
            case VOIDED, DECLINED -> paid ? Action.REVIEW : Action.FAIL;
            case PENDING, NOT_FOUND -> Action.WAIT;
            case UNKNOWN -> Action.REVIEW;
        };
    }
}
