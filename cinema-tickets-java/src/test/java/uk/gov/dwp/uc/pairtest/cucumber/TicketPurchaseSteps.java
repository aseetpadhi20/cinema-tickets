package uk.gov.dwp.uc.pairtest.cucumber;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.InOrder;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.paymentgateway.TicketPaymentServiceImpl;
import thirdparty.seatbooking.SeatReservationService;
import thirdparty.seatbooking.SeatReservationServiceImpl;
import uk.gov.dwp.uc.pairtest.TicketService;
import uk.gov.dwp.uc.pairtest.TicketServiceImpl;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class TicketPurchaseSteps {

    private TicketPaymentService paymentService;
    private SeatReservationService seatService;
    private TicketService ticketService;

    private long accountId;
    private InvalidPurchaseException rejection;

    @Before
    public void setUp() {
        paymentService = mock(TicketPaymentService.class, delegatesTo(new TicketPaymentServiceImpl()));
        seatService = mock(SeatReservationService.class, delegatesTo(new SeatReservationServiceImpl()));
        ticketService = new TicketServiceImpl(paymentService, seatService);
        rejection = null;
    }

    @Given("a customer with account id {long}")
    public void aCustomerWithAccountId(long id) {
        accountId = id;
    }

    @When("the customer buys {int} adult, {int} child and {int} infant tickets")
    public void theCustomerBuys(int adults, int children, int infants) {
        List<TicketTypeRequest> requests = new ArrayList<>();
        if (adults > 0) requests.add(new TicketTypeRequest(Type.ADULT, adults));
        if (children > 0) requests.add(new TicketTypeRequest(Type.CHILD, children));
        if (infants > 0) requests.add(new TicketTypeRequest(Type.INFANT, infants));
        purchase(requests.toArray(new TicketTypeRequest[0]));
    }

    @When("the customer makes these ticket requests")
    public void theCustomerMakesTheseTicketRequests(DataTable table) {
        List<TicketTypeRequest> requests = new ArrayList<>();
        for (Map<String, String> row : table.asMaps()) {
            requests.add(new TicketTypeRequest(Type.valueOf(row.get("type")), Integer.parseInt(row.get("quantity"))));
        }
        purchase(requests.toArray(new TicketTypeRequest[0]));
    }

    @When("the customer makes no ticket requests")
    public void theCustomerMakesNoTicketRequests() {
        purchase();
    }

    @Then("the purchase is accepted")
    public void thePurchaseIsAccepted() {
        assertNull(rejection, "purchase should have been accepted");
    }

    @Then("the purchase is rejected")
    public void thePurchaseIsRejected() {
        assertNotNull(rejection, "purchase should have been rejected");
    }

    @Then("the customer pays £{int}")
    public void theCustomerPays(int amount) {
        verify(paymentService).makePayment(accountId, amount);
    }

    @Then("the number of seats reserved is {int}")
    public void seatsAreReserved(int seats) {
        verify(seatService).reserveSeat(accountId, seats);
    }

    @Then("nothing is paid and no seats are reserved")
    public void nothingIsPaidAndNoSeatsAreReserved() {
        verifyNoInteractions(paymentService, seatService);
    }

    @Then("a payment of £{int} was requested before the reservation of {int} seats")
    public void paymentWasRequestedBeforeReservation(int amount, int seats) {
        InOrder order = inOrder(paymentService, seatService);
        order.verify(paymentService).makePayment(accountId, amount);
        order.verify(seatService).reserveSeat(accountId, seats);
        verifyNoMoreInteractions(paymentService, seatService);
    }

    private void purchase(TicketTypeRequest... requests) {
        try {
            ticketService.purchaseTickets(accountId, requests);
        } catch (InvalidPurchaseException e) {
            rejection = e;
        }
    }
}
