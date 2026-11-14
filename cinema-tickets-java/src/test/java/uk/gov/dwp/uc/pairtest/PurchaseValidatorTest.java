package uk.gov.dwp.uc.pairtest;

import org.junit.jupiter.api.Test;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;
import uk.gov.dwp.uc.pairtest.exception.PurchaseRejectionReason;
import uk.gov.dwp.uc.pairtest.exception.RejectedPurchaseException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurchaseValidatorTest {

    private final PurchaseValidator validator = new PurchaseValidator();

    private static TicketTypeRequest req(Type type, int n) {
        return new TicketTypeRequest(type, n);
    }

    private PurchaseRejectionReason requestRejectedFor(Long accountId, TicketTypeRequest... requests) {
        return assertThrows(RejectedPurchaseException.class,
                () -> validator.validateRequest(accountId, requests)).getReason();
    }

    private PurchaseRejectionReason orderRejectedFor(TicketTypeRequest... requests) {
        return assertThrows(RejectedPurchaseException.class,
                () -> validator.validateOrder(TicketOrder.from(requests))).getReason();
    }

    @Test
    void invalidAccountIdsAreRejected() {
        assertEquals(PurchaseRejectionReason.INVALID_ACCOUNT, requestRejectedFor(null, req(Type.ADULT, 1)));
        assertEquals(PurchaseRejectionReason.INVALID_ACCOUNT, requestRejectedFor(0L, req(Type.ADULT, 1)));
        assertEquals(PurchaseRejectionReason.INVALID_ACCOUNT, requestRejectedFor(-1L, req(Type.ADULT, 1)));
    }

    @Test
    void accountIsCheckedBeforeTheRequests() {
        assertEquals(PurchaseRejectionReason.INVALID_ACCOUNT, requestRejectedFor(0L, req(Type.CHILD, 0)));
    }

    @Test
    void missingRequestsAreRejected() {
        assertEquals(PurchaseRejectionReason.NO_TICKET_REQUESTS, requestRejectedFor(1L));
        assertEquals(PurchaseRejectionReason.NO_TICKET_REQUESTS, requestRejectedFor(1L, (TicketTypeRequest[]) null));
    }

    @Test
    void nullRequestOrTypeIsRejected() {
        assertEquals(PurchaseRejectionReason.MISSING_REQUEST_OR_TYPE, requestRejectedFor(1L, (TicketTypeRequest) null));
        assertEquals(PurchaseRejectionReason.MISSING_REQUEST_OR_TYPE, requestRejectedFor(1L, req(Type.ADULT, 1), null));
        assertEquals(PurchaseRejectionReason.MISSING_REQUEST_OR_TYPE, requestRejectedFor(1L, new TicketTypeRequest(null, 1)));
    }

    @Test
    void zeroOrNegativeQuantityIsRejected() {
        assertEquals(PurchaseRejectionReason.INVALID_QUANTITY, requestRejectedFor(1L, req(Type.ADULT, 0)));
        assertEquals(PurchaseRejectionReason.INVALID_QUANTITY, requestRejectedFor(1L, req(Type.ADULT, 1), req(Type.CHILD, -1)));
    }

    @Test
    void singleRequestOverTheLimitIsRejectedEvenWhenHuge() {
        assertEquals(PurchaseRejectionReason.TOO_MANY_TICKETS, requestRejectedFor(1L, req(Type.ADULT, 26)));
        assertEquals(PurchaseRejectionReason.TOO_MANY_TICKETS, requestRejectedFor(1L, req(Type.ADULT, Integer.MAX_VALUE)));
    }

    @Test
    void wellFormedRequestsPass() {
        assertDoesNotThrow(() -> validator.validateRequest(1L, new TicketTypeRequest[]{req(Type.ADULT, 25)}));
    }

    @Test
    void totalOverTheLimitIsRejectedAcrossRequests() {
        assertEquals(PurchaseRejectionReason.TOO_MANY_TICKETS, orderRejectedFor(req(Type.ADULT, 13), req(Type.ADULT, 13)));
        assertEquals(PurchaseRejectionReason.TOO_MANY_TICKETS,
                orderRejectedFor(req(Type.ADULT, 20), req(Type.CHILD, 3), req(Type.INFANT, 3)));
        assertEquals(PurchaseRejectionReason.TOO_MANY_TICKETS,
                orderRejectedFor(req(Type.ADULT, 25), req(Type.ADULT, 25), req(Type.ADULT, 25)));
    }

    @Test
    void exactlyTheLimitPasses() {
        assertDoesNotThrow(() -> validator.validateOrder(TicketOrder.from(req(Type.ADULT, 20), req(Type.CHILD, 5))));
    }

    @Test
    void childOrInfantWithoutAnAdultIsRejected() {
        assertEquals(PurchaseRejectionReason.ADULT_REQUIRED, orderRejectedFor(req(Type.CHILD, 1)));
        assertEquals(PurchaseRejectionReason.ADULT_REQUIRED, orderRejectedFor(req(Type.INFANT, 1)));
        assertEquals(PurchaseRejectionReason.ADULT_REQUIRED, orderRejectedFor(req(Type.CHILD, 1), req(Type.INFANT, 1)));
    }

    @Test
    void rejectionIsAnInvalidPurchaseExceptionWithTheReasonMessage() {
        InvalidPurchaseException e = assertThrows(InvalidPurchaseException.class,
                () -> validator.validateRequest(0L, new TicketTypeRequest[]{req(Type.ADULT, 1)}));
        assertTrue(e instanceof RejectedPurchaseException);
        assertEquals(PurchaseRejectionReason.INVALID_ACCOUNT.getMessage(), e.getMessage());
    }
}
