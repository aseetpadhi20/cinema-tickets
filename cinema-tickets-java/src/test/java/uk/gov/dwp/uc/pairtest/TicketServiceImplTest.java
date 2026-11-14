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

import static org.mockito.Mockito.verify;

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

    @Test
    void adultOnlyPaysAndReservesSeats() {
        service.purchaseTickets(1L, req(Type.ADULT, 2));
        verify(paymentService).makePayment(1L, 50);
        verify(seatReservationService).reserveSeat(1L, 2);
    }
}
