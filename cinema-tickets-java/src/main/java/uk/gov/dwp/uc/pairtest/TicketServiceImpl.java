package uk.gov.dwp.uc.pairtest;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import java.util.Objects;

public class TicketServiceImpl implements TicketService {
    /**
     * Should only have private methods other than the one below.
     */

    private static final int MAX_TICKETS_PER_PURCHASE = 25;
    private static final String TOO_MANY_TICKETS_MESSAGE =
            "Cannot purchase more than " + MAX_TICKETS_PER_PURCHASE + " tickets at a time";

    private final TicketPaymentService paymentService;
    private final SeatReservationService seatReservationService;

    public TicketServiceImpl(TicketPaymentService paymentService, SeatReservationService seatReservationService) {
        this.paymentService = Objects.requireNonNull(paymentService, "paymentService must not be null");
        this.seatReservationService =
                Objects.requireNonNull(seatReservationService, "seatReservationService must not be null");
    }

    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests) throws InvalidPurchaseException {
        validateAccount(accountId);
        int[] counts = countTickets(ticketTypeRequests);
        validateAdultPresent(counts);

        int amount = calculateAmount(counts);
        int seats = calculateSeats(counts);

        paymentService.makePayment(accountId, amount);
        seatReservationService.reserveSeat(accountId, seats);
    }

    private void validateAccount(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new InvalidPurchaseException("Account id must be greater than zero");
        }
    }

    /**
     * Validates each request and totals the tickets per type (indexed by {@link Type#ordinal()}).
     */
    private int[] countTickets(TicketTypeRequest[] requests) {
        if (requests == null || requests.length == 0) {
            throw new InvalidPurchaseException("At least one ticket request is required");
        }
        int[] counts = new int[Type.values().length];
        int total = 0;
        for (TicketTypeRequest request : requests) {
            if (request == null || request.getTicketType() == null) {
                throw new InvalidPurchaseException("Ticket request and type must not be null");
            }
            int noOfTickets = request.getNoOfTickets();
            if (noOfTickets <= 0) {
                throw new InvalidPurchaseException("Number of tickets must be greater than zero");
            }
            // total is never above the maximum here, so this comparison cannot overflow,
            // unlike checking (total + noOfTickets) after the addition.
            if (noOfTickets > MAX_TICKETS_PER_PURCHASE - total) {
                throw new InvalidPurchaseException(TOO_MANY_TICKETS_MESSAGE);
            }
            total += noOfTickets;
            counts[request.getTicketType().ordinal()] += noOfTickets;
        }
        return counts;
    }

    private void validateAdultPresent(int[] counts) {
        if (counts[Type.ADULT.ordinal()] == 0) {
            throw new InvalidPurchaseException("Child and infant tickets require at least one adult ticket");
        }
    }

    private int calculateAmount(int[] counts) {
        int amount = 0;
        for (Type type : Type.values()) {
            amount += counts[type.ordinal()] * type.getPrice();
        }
        return amount;
    }

    private int calculateSeats(int[] counts) {
        int seats = 0;
        for (Type type : Type.values()) {
            if (type.requiresSeat()) {
                seats += counts[type.ordinal()];
            }
        }
        return seats;
    }

}
