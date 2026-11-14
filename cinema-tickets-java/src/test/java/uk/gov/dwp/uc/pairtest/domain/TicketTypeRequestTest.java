package uk.gov.dwp.uc.pairtest.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketTypeRequestTest {

    @Test
    void exposesTypeAndQuantity() {
        TicketTypeRequest request = new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 3);
        assertEquals(TicketTypeRequest.Type.CHILD, request.getTicketType());
        assertEquals(3, request.getNoOfTickets());
    }

    @Test
    void isImmutable() {
        assertTrue(Modifier.isFinal(TicketTypeRequest.class.getModifiers()), "class should be final");
        for (Field field : TicketTypeRequest.class.getDeclaredFields()) {
            assertTrue(Modifier.isFinal(field.getModifiers()), field.getName() + " should be final");
            assertTrue(Modifier.isPrivate(field.getModifiers()), field.getName() + " should be private");
        }
    }

    @ParameterizedTest
    @CsvSource({"ADULT,25,true", "CHILD,15,true", "INFANT,0,false"})
    void typesHaveCorrectPriceAndSeatRule(TicketTypeRequest.Type type, int price, boolean seat) {
        assertEquals(price, type.getPrice());
        assertEquals(seat, type.requiresSeat());
    }
}
