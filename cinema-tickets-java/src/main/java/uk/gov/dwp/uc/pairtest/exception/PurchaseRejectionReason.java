package uk.gov.dwp.uc.pairtest.exception;

public enum PurchaseRejectionReason {

    INVALID_ACCOUNT("Account id must be greater than zero"),
    NO_TICKET_REQUESTS("At least one ticket request is required"),
    MISSING_REQUEST_OR_TYPE("Ticket request and type must not be null"),
    INVALID_QUANTITY("Number of tickets must be greater than zero"),
    TOO_MANY_TICKETS("Too many tickets requested for one purchase"),
    ADULT_REQUIRED("Child and infant tickets require at least one adult ticket");

    private final String message;

    PurchaseRejectionReason(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
