package uk.gov.dwp.uc.pairtest;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

public class TicketServiceImpl implements TicketService {
    /**
     * Should only have private methods other than the one below.
     */

    private final TicketPaymentService paymentService;
    private final SeatReservationService seatReservationService;

    public TicketServiceImpl(TicketPaymentService paymentService, SeatReservationService seatReservationService) {
        this.paymentService = paymentService;
        this.seatReservationService = seatReservationService;
    }

    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests) throws InvalidPurchaseException {
        int[] counts = new int[Type.values().length];
        for (TicketTypeRequest request : ticketTypeRequests) {
            counts[request.getTicketType().ordinal()] += request.getNoOfTickets();
        }

        int amount = 0;
        int seats = 0;
        for (Type type : Type.values()) {
            int count = counts[type.ordinal()];
            amount += count * type.getPrice();
            if (type.requiresSeat()) {
                seats += count;
            }
        }

        paymentService.makePayment(accountId, amount);
        seatReservationService.reserveSeat(accountId, seats);
    }

}
