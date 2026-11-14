package uk.gov.dwp.uc.pairtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    @Mock
    private TicketPaymentService paymentService;
    @Mock
    private SeatReservationService seatReservationService;

    private TicketService service;

    @BeforeEach
    void setUp() {
        service = new TicketServiceImpl(paymentService, seatReservationService);
    }

    private static TicketTypeRequest req(Type type, int n) {
        return new TicketTypeRequest(type, n);
    }

    private void assertInvalid(Long accountId, TicketTypeRequest... requests) {
        assertThrows(InvalidPurchaseException.class, () -> service.purchaseTickets(accountId, requests));
        verifyNoInteractions(paymentService, seatReservationService);
    }

    @Test
    void adultOnlyPaysAndReservesSeats() {
        service.purchaseTickets(1L, req(Type.ADULT, 2));
        verify(paymentService).makePayment(1L, 50);
        verify(seatReservationService).reserveSeat(1L, 2);
    }

    @Test
    void childrenPayAndGetSeats() {
        service.purchaseTickets(1L, req(Type.ADULT, 1), req(Type.CHILD, 2));
        verify(paymentService).makePayment(1L, 55);
        verify(seatReservationService).reserveSeat(1L, 3);
    }

    @Test
    void infantsPayNothingAndGetNoSeat() {
        service.purchaseTickets(1L, req(Type.ADULT, 2), req(Type.INFANT, 2));
        verify(paymentService).makePayment(1L, 50);
        verify(seatReservationService).reserveSeat(1L, 2);
    }

    @Test
    void multipleRequestsOfSameTypeAreAggregated() {
        service.purchaseTickets(7L, req(Type.ADULT, 1), req(Type.ADULT, 1), req(Type.CHILD, 1), req(Type.CHILD, 1));
        verify(paymentService).makePayment(7L, 80);
        verify(seatReservationService).reserveSeat(7L, 4);
    }

    @Test
    void invalidAccountRejected() {
        assertInvalid(null, req(Type.ADULT, 1));
        assertInvalid(0L, req(Type.ADULT, 1));
        assertInvalid(-1L, req(Type.ADULT, 1));
    }

    @Test
    void emptyOrNullRequestsRejected() {
        assertInvalid(1L);
        assertInvalid(1L, (TicketTypeRequest[]) null);
        assertInvalid(1L, (TicketTypeRequest) null);
        assertInvalid(1L, new TicketTypeRequest(null, 1));
    }

    @Test
    void zeroOrNegativeQuantityRejected() {
        assertInvalid(1L, req(Type.ADULT, 0));
        assertInvalid(1L, req(Type.ADULT, 1), req(Type.CHILD, -1));
    }

    @Test
    void oneInvalidRequestRejectsWholePurchase() {
        assertInvalid(1L, req(Type.ADULT, 2), req(Type.CHILD, 0));
    }

    @Test
    void exactlyMaximumTicketsIsAllowed() {
        service.purchaseTickets(1L, req(Type.ADULT, 20), req(Type.CHILD, 5));
        verify(paymentService).makePayment(1L, 575);
        verify(seatReservationService).reserveSeat(1L, 25);
    }

    @Test
    void moreThanMaximumTicketsRejected() {
        assertInvalid(1L, req(Type.ADULT, 26));
        assertInvalid(1L, req(Type.ADULT, 20), req(Type.CHILD, 3), req(Type.INFANT, 3));
    }

    @Test
    void maximumLimitAppliesAcrossSeparateRequests() {
        assertInvalid(1L, req(Type.ADULT, 13), req(Type.ADULT, 13));
    }

    @Test
    void overflowingCountsRejected() {
        assertInvalid(1L, req(Type.ADULT, Integer.MAX_VALUE), req(Type.ADULT, 1));
    }

    @ParameterizedTest(name = "{0} adult, {1} child, {2} infant -> £{3}, {4} seats")
    @CsvSource({
            "1, 0, 0, 25, 1",
            "1, 1, 1, 40, 2",
            "2, 3, 2, 95, 5",
            "3, 0, 3, 75, 3",
            "10, 10, 5, 400, 20",
            "25, 0, 0, 625, 25",
            "1, 24, 0, 385, 25"
    })
    void calculatesCorrectAmountAndSeats(int adults, int children, int infants, int amount, int seats) {
        List<TicketTypeRequest> requests = new ArrayList<>();
        if (adults > 0) requests.add(req(Type.ADULT, adults));
        if (children > 0) requests.add(req(Type.CHILD, children));
        if (infants > 0) requests.add(req(Type.INFANT, infants));

        service.purchaseTickets(5L, requests.toArray(new TicketTypeRequest[0]));

        verify(paymentService).makePayment(5L, amount);
        verify(seatReservationService).reserveSeat(5L, seats);
    }
}
