package uk.gov.dwp.uc.pairtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.paymentgateway.TicketPaymentServiceImpl;
import thirdparty.seatbooking.SeatReservationService;
import thirdparty.seatbooking.SeatReservationServiceImpl;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Wires the real TicketServiceImpl to the real third-party implementations
 * (spied only so interactions can be observed) and exercises it purely through
 * the public TicketService interface, one scenario per business rule.
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

    private void assertRejected(Long accountId, TicketTypeRequest... requests) {
        assertThrows(InvalidPurchaseException.class, () -> ticketService.purchaseTickets(accountId, requests));
        verifyNoInteractions(paymentService, seatService);
    }

    // ---- Business rule: ticket prices / seats per type ----

    @Test
    void adultTicketCosts25AndTakesASeat() {
        ticketService.purchaseTickets(1L, req(Type.ADULT, 1));
        verify(paymentService).makePayment(1L, 25);
        verify(seatService).reserveSeat(1L, 1);
    }

    @Test
    void childTicketCosts15AndTakesASeat() {
        ticketService.purchaseTickets(1L, req(Type.ADULT, 1), req(Type.CHILD, 1));
        verify(paymentService).makePayment(1L, 40);
        verify(seatService).reserveSeat(1L, 2);
    }

    @Test
    void infantTicketIsFreeAndTakesNoSeat() {
        ticketService.purchaseTickets(1L, req(Type.ADULT, 1), req(Type.INFANT, 1));
        verify(paymentService).makePayment(1L, 25);
        verify(seatService).reserveSeat(1L, 1);
    }

    // ---- Business rule: multiple tickets / mixed purchases ----

    @ParameterizedTest(name = "{0}A {1}C {2}I -> £{3}, {4} seats")
    @CsvSource({
            "1, 0, 0, 25, 1",
            "2, 3, 2, 95, 5",
            "10, 10, 5, 400, 20",
            "25, 0, 0, 625, 25",
            "1, 24, 0, 385, 25",
            "12, 0, 12, 300, 12"
    })
    void mixedPurchasesAreCalculatedCorrectly(int adults, int children, int infants, int amount, int seats) {
        List<TicketTypeRequest> requests = new ArrayList<>();
        if (adults > 0) requests.add(req(Type.ADULT, adults));
        if (children > 0) requests.add(req(Type.CHILD, children));
        if (infants > 0) requests.add(req(Type.INFANT, infants));

        ticketService.purchaseTickets(9L, requests.toArray(new TicketTypeRequest[0]));

        verify(paymentService).makePayment(9L, amount);
        verify(seatService).reserveSeat(9L, seats);
    }

    @Test
    void repeatedTypesInSeparateRequestsAreCombined() {
        ticketService.purchaseTickets(3L, req(Type.ADULT, 1), req(Type.CHILD, 1), req(Type.ADULT, 1), req(Type.CHILD, 1));
        verify(paymentService).makePayment(3L, 80);
        verify(seatService).reserveSeat(3L, 4);
    }

    @Test
    void paymentThenReservationEachHappenExactlyOnce() {
        ticketService.purchaseTickets(1L, req(Type.ADULT, 2), req(Type.CHILD, 2));
        InOrder order = inOrder(paymentService, seatService);
        order.verify(paymentService).makePayment(1L, 80);
        order.verify(seatService).reserveSeat(1L, 4);
        verifyNoMoreInteractions(paymentService, seatService);
    }

    // ---- Business rule: maximum of 25 tickets ----

    @Test
    void twentyFiveTicketsIncludingInfantsIsAccepted() {
        assertDoesNotThrow(() -> ticketService.purchaseTickets(1L,
                req(Type.ADULT, 10), req(Type.CHILD, 10), req(Type.INFANT, 5)));
        verify(paymentService).makePayment(1L, 400);
    }

    @Test
    void twentySixTicketsIsRejected() {
        assertRejected(1L, req(Type.ADULT, 26));
        assertRejected(1L, req(Type.ADULT, 13), req(Type.CHILD, 13));
        assertRejected(1L, req(Type.ADULT, 10), req(Type.CHILD, 10), req(Type.INFANT, 6));
    }

    @Test
    void hugeQuantitiesDoNotOverflowTheLimit() {
        assertRejected(1L, req(Type.ADULT, Integer.MAX_VALUE), req(Type.ADULT, 1));
        assertRejected(1L, req(Type.ADULT, Integer.MAX_VALUE), req(Type.CHILD, Integer.MAX_VALUE));
    }

    // ---- Business rule: child/infant need an adult ----

    @Test
    void childOrInfantWithoutAdultIsRejected() {
        assertRejected(1L, req(Type.CHILD, 2));
        assertRejected(1L, req(Type.INFANT, 1));
        assertRejected(1L, req(Type.CHILD, 1), req(Type.INFANT, 1));
    }

    @Test
    void moreInfantsThanAdultsIsRejected() {
        assertRejected(1L, req(Type.ADULT, 1), req(Type.INFANT, 2));
    }

    // ---- Account validation ----

    @Test
    void accountIdMustBeGreaterThanZero() {
        assertRejected(null, req(Type.ADULT, 1));
        assertRejected(0L, req(Type.ADULT, 1));
        assertRejected(-5L, req(Type.ADULT, 1));
    }

    @Test
    void smallestValidAccountIdIsAccepted() {
        ticketService.purchaseTickets(1L, req(Type.ADULT, 1));
        verify(paymentService).makePayment(1L, 25);
    }

    @Test
    void largeAccountIdIsPassedThroughUnchanged() {
        ticketService.purchaseTickets(Long.MAX_VALUE, req(Type.ADULT, 1));
        verify(paymentService).makePayment(Long.MAX_VALUE, 25);
        verify(seatService).reserveSeat(Long.MAX_VALUE, 1);
    }

    // ---- Malformed requests ----

    @Test
    void emptyOrNullRequestsAreRejected() {
        assertRejected(1L);
        assertRejected(1L, (TicketTypeRequest[]) null);
        assertRejected(1L, (TicketTypeRequest) null);
        assertRejected(1L, req(Type.ADULT, 1), null);
        assertRejected(1L, new TicketTypeRequest(null, 1));
    }

    @Test
    void zeroOrNegativeQuantitiesAreRejected() {
        assertRejected(1L, req(Type.ADULT, 0));
        assertRejected(1L, req(Type.ADULT, -1));
        assertRejected(1L, req(Type.ADULT, 2), req(Type.CHILD, 0));
        assertRejected(1L, req(Type.ADULT, 2), req(Type.INFANT, -1));
    }

    // ---- Atomicity / independence ----

    @Test
    void rejectedPurchaseNeverCallsEitherExternalService() {
        assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(1L, req(Type.CHILD, 1)));
        verify(paymentService, org.mockito.Mockito.never()).makePayment(anyLong(), anyInt());
        verify(seatService, org.mockito.Mockito.never()).reserveSeat(anyLong(), anyInt());
    }

    @Test
    void invalidPurchaseDoesNotAffectALaterValidPurchase() {
        assertRejected(1L, req(Type.CHILD, 1));
        ticketService.purchaseTickets(1L, req(Type.ADULT, 1), req(Type.CHILD, 1));
        verify(paymentService).makePayment(1L, 40);
        verify(seatService).reserveSeat(1L, 2);
    }

    @Test
    void serviceIsReusableAcrossAccounts() {
        ticketService.purchaseTickets(1L, req(Type.ADULT, 1));
        ticketService.purchaseTickets(2L, req(Type.ADULT, 2), req(Type.INFANT, 1));
        verify(paymentService).makePayment(1L, 25);
        verify(paymentService).makePayment(2L, 50);
        verify(seatService).reserveSeat(1L, 1);
        verify(seatService).reserveSeat(2L, 2);
    }
}
