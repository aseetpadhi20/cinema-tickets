package uk.gov.dwp.uc.pairtest;

import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;

import java.util.EnumMap;
import java.util.Map;

final class TicketOrder {

    private final Map<Type, Long> counts = new EnumMap<>(Type.class);

    private TicketOrder() {
    }

    static TicketOrder from(TicketTypeRequest... requests) {
        TicketOrder order = new TicketOrder();
        for (TicketTypeRequest request : requests) {
            order.counts.merge(request.getTicketType(), (long) request.getNoOfTickets(), Long::sum);
        }
        return order;
    }

    long totalTickets() {
        long total = 0;
        for (long count : counts.values()) {
            total += count;
        }
        return total;
    }

    boolean hasAdult() {
        return count(Type.ADULT) > 0;
    }

    int amount() {
        long amount = 0;
        for (Type type : Type.values()) {
            amount += count(type) * type.getPrice();
        }
        return Math.toIntExact(amount);
    }

    int seats() {
        long seats = 0;
        for (Type type : Type.values()) {
            if (type.requiresSeat()) {
                seats += count(type);
            }
        }
        return Math.toIntExact(seats);
    }

    private long count(Type type) {
        return counts.getOrDefault(type, 0L);
    }
}
