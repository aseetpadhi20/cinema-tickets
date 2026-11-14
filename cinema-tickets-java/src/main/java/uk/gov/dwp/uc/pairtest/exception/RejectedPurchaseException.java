package uk.gov.dwp.uc.pairtest.exception;

public class RejectedPurchaseException extends InvalidPurchaseException {

    private final PurchaseRejectionReason reason;

    public RejectedPurchaseException(PurchaseRejectionReason reason) {
        super(reason.getMessage());
        this.reason = reason;
    }

    public PurchaseRejectionReason getReason() {
        return reason;
    }
}
