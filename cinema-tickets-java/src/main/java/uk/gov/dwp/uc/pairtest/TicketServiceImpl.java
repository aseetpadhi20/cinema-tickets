package uk.gov.dwp.uc.pairtest;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import java.util.Objects;

public class TicketServiceImpl implements TicketService {
    /**
     * Should only have private methods other than the one below.
     */

    private final TicketPaymentService paymentService;
    private final SeatReservationService seatReservationService;
    private final PurchaseValidator validator;

    public TicketServiceImpl(TicketPaymentService paymentService, SeatReservationService seatReservationService) {
        this(paymentService, seatReservationService, new PurchaseValidator());
    }

    TicketServiceImpl(TicketPaymentService paymentService,
                      SeatReservationService seatReservationService,
                      PurchaseValidator validator) {
        this.paymentService = Objects.requireNonNull(paymentService, "paymentService must not be null");
        this.seatReservationService =
                Objects.requireNonNull(seatReservationService, "seatReservationService must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
    }

    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests) throws InvalidPurchaseException {
        validator.validateRequest(accountId, ticketTypeRequests);
        TicketOrder order = TicketOrder.from(ticketTypeRequests);
        validator.validateOrder(order);

        paymentService.makePayment(accountId, order.amount());
        seatReservationService.reserveSeat(accountId, order.seats());
    }

}
