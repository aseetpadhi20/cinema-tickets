package uk.gov.dwp.uc.pairtest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketOrderTest {

    private static TicketTypeRequest req(Type type, int n) {
        return new TicketTypeRequest(type, n);
    }

    @ParameterizedTest(name = "{0} adult, {1} child, {2} infant -> {3} total, {4} amount, {5} seats")
    @CsvSource({
            "1, 0, 0, 1, 25, 1",
            "2, 3, 2, 7, 95, 5",
            "1, 1, 1, 3, 40, 2",
            "10, 10, 5, 25, 400, 20"
    })
    void totalsAreCalculatedPerType(int adults, int children, int infants, int total, int amount, int seats) {
        TicketOrder order = TicketOrder.from(req(Type.ADULT, adults), req(Type.CHILD, children), req(Type.INFANT, infants));
        assertEquals(total, order.totalTickets());
        assertEquals(amount, order.amount());
        assertEquals(seats, order.seats());
    }

    @Test
    void requestsOfTheSameTypeAreAdded() {
        TicketOrder order = TicketOrder.from(req(Type.ADULT, 1), req(Type.CHILD, 2), req(Type.ADULT, 3));
        assertEquals(6, order.totalTickets());
        assertEquals(4 * 25 + 2 * 15, order.amount());
        assertEquals(6, order.seats());
    }

    @Test
    void infantsAreFreeAndHaveNoSeat() {
        TicketOrder order = TicketOrder.from(req(Type.ADULT, 1), req(Type.INFANT, 4));
        assertEquals(5, order.totalTickets());
        assertEquals(25, order.amount());
        assertEquals(1, order.seats());
    }

    @Test
    void hasAdultOnlyWhenAnAdultTicketIsPresent() {
        assertTrue(TicketOrder.from(req(Type.ADULT, 1), req(Type.CHILD, 1)).hasAdult());
        assertFalse(TicketOrder.from(req(Type.CHILD, 1), req(Type.INFANT, 1)).hasAdult());
    }

    @Test
    void totalDoesNotOverflowOnHugeQuantities() {
        TicketOrder order = TicketOrder.from(req(Type.ADULT, Integer.MAX_VALUE), req(Type.ADULT, Integer.MAX_VALUE));
        assertEquals(2L * Integer.MAX_VALUE, order.totalTickets());
    }
}
