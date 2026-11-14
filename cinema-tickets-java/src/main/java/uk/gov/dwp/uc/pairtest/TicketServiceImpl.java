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

    static final int MAX_TICKETS_PER_PURCHASE = 25;

    private final TicketPaymentService paymentService;
    private final SeatReservationService seatReservationService;

    public TicketServiceImpl(TicketPaymentService paymentService, SeatReservationService seatReservationService) {
        this.paymentService = paymentService;
        this.seatReservationService = seatReservationService;
    }

    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests) throws InvalidPurchaseException {
        validateAccount(accountId);
        int[] counts = countTickets(ticketTypeRequests);
        validatePurchase(counts);

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

    private void validateAccount(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new InvalidPurchaseException("Account id must be greater than zero");
        }
    }

    private int[] countTickets(TicketTypeRequest[] requests) {
        if (requests == null || requests.length == 0) {
            throw new InvalidPurchaseException("At least one ticket request is required");
        }
        int[] counts = new int[Type.values().length];
        for (TicketTypeRequest request : requests) {
            if (request == null || request.getTicketType() == null) {
                throw new InvalidPurchaseException("Ticket request and type must not be null");
            }
            if (request.getNoOfTickets() <= 0) {
                throw new InvalidPurchaseException("Number of tickets must be greater than zero");
            }
            counts[request.getTicketType().ordinal()] += request.getNoOfTickets();
            // Guard against int overflow before the total check
            if (counts[request.getTicketType().ordinal()] > MAX_TICKETS_PER_PURCHASE) {
                throw new InvalidPurchaseException("Cannot purchase more than " + MAX_TICKETS_PER_PURCHASE + " tickets at a time");
            }
        }
        return counts;
    }

    private void validatePurchase(int[] counts) {
        int total = 0;
        for (int count : counts) {
            total += count;
        }
        if (total > MAX_TICKETS_PER_PURCHASE) {
            throw new InvalidPurchaseException("Cannot purchase more than " + MAX_TICKETS_PER_PURCHASE + " tickets at a time");
        }
    }

}
