package uk.gov.dwp.uc.pairtest;

import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.RejectedPurchaseException;

import static uk.gov.dwp.uc.pairtest.exception.PurchaseRejectionReason.ADULT_REQUIRED;
import static uk.gov.dwp.uc.pairtest.exception.PurchaseRejectionReason.INVALID_ACCOUNT;
import static uk.gov.dwp.uc.pairtest.exception.PurchaseRejectionReason.INVALID_QUANTITY;
import static uk.gov.dwp.uc.pairtest.exception.PurchaseRejectionReason.MISSING_REQUEST_OR_TYPE;
import static uk.gov.dwp.uc.pairtest.exception.PurchaseRejectionReason.NO_TICKET_REQUESTS;
import static uk.gov.dwp.uc.pairtest.exception.PurchaseRejectionReason.TOO_MANY_TICKETS;

class PurchaseValidator {

    static final int MAX_TICKETS_PER_PURCHASE = 25;

    void validateRequest(Long accountId, TicketTypeRequest[] requests) {
        if (accountId == null || accountId <= 0) {
            throw new RejectedPurchaseException(INVALID_ACCOUNT);
        }
        if (requests == null || requests.length == 0) {
            throw new RejectedPurchaseException(NO_TICKET_REQUESTS);
        }
        for (TicketTypeRequest request : requests) {
            if (request == null || request.getTicketType() == null) {
                throw new RejectedPurchaseException(MISSING_REQUEST_OR_TYPE);
            }
            if (request.getNoOfTickets() <= 0) {
                throw new RejectedPurchaseException(INVALID_QUANTITY);
            }
            if (request.getNoOfTickets() > MAX_TICKETS_PER_PURCHASE) {
                throw new RejectedPurchaseException(TOO_MANY_TICKETS);
            }
        }
    }

    void validateOrder(TicketOrder order) {
        if (order.totalTickets() > MAX_TICKETS_PER_PURCHASE) {
            throw new RejectedPurchaseException(TOO_MANY_TICKETS);
        }
        if (!order.hasAdult()) {
            throw new RejectedPurchaseException(ADULT_REQUIRED);
        }
    }
}
