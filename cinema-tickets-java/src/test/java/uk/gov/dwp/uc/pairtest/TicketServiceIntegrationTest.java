package uk.gov.dwp.uc.pairtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.paymentgateway.TicketPaymentServiceImpl;
import thirdparty.seatbooking.SeatReservationService;
import thirdparty.seatbooking.SeatReservationServiceImpl;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * End-to-end checks against the real third-party implementations. The detailed rule matrix
 * lives in {@link TicketServiceImplTest}; these tests only prove the pieces work together.
 */
class TicketServiceIntegrationTest {

    private TicketPaymentService paymentService;
    private SeatReservationService seatService;
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        paymentService = mock(TicketPaymentService.class, delegatesTo(new TicketPaymentServiceImpl()));
        seatService = mock(SeatReservationService.class, delegatesTo(new SeatReservationServiceImpl()));
        ticketService = new TicketServiceImpl(paymentService, seatService);
    }

    private static TicketTypeRequest req(Type type, int n) {
        return new TicketTypeRequest(type, n);
    }

    @Test
    void mixedPurchasePaysOnceThenReservesOnce() {
        ticketService.purchaseTickets(9L, req(Type.ADULT, 2), req(Type.CHILD, 3), req(Type.INFANT, 2));

        InOrder order = inOrder(paymentService, seatService);
        order.verify(paymentService).makePayment(9L, 95);
        order.verify(seatService).reserveSeat(9L, 5);
        verifyNoMoreInteractions(paymentService, seatService);
    }

    @Test
    void maximumPurchaseIsAccepted() {
        ticketService.purchaseTickets(1L, req(Type.ADULT, 10), req(Type.CHILD, 10), req(Type.INFANT, 5));
        verify(paymentService).makePayment(1L, 400);
        verify(seatService).reserveSeat(1L, 20);
    }

    @Test
    void invalidPurchaseCallsNeitherService() {
        assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(1L, req(Type.CHILD, 1)));
        assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(1L, req(Type.ADULT, 1), req(Type.ADULT, Integer.MAX_VALUE)));
        verifyNoInteractions(paymentService, seatService);
    }

    @Test
    void invalidPurchaseDoesNotAffectALaterValidPurchase() {
        assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(1L, req(Type.INFANT, 1)));
        ticketService.purchaseTickets(1L, req(Type.ADULT, 1), req(Type.CHILD, 1));
        verify(paymentService).makePayment(1L, 40);
        verify(seatService).reserveSeat(1L, 2);
    }
}
