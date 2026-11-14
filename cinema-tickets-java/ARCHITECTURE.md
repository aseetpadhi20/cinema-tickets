# Architecture

## 1. Scope

`TicketServiceImpl` implements the provided `TicketService` interface. For a valid request it:

1. calculates the amount to pay and makes one payment request to `TicketPaymentService`;
2. calculates the number of seats and makes one reservation request to `SeatReservationService`;
3. rejects any invalid request with `InvalidPurchaseException` before either external service is called.

## 2. Constraints that shaped the design

| Constraint (from the requirements) | Consequence |
|---|---|
| `TicketService` interface cannot be modified | The implementation conforms to the existing signature `purchaseTickets(Long, TicketTypeRequest...)`. |
| `thirdparty.*` code cannot be modified | The external services are used only through their interfaces. The implementation never touches their code. |
| `TicketTypeRequest` should be immutable | Final class, private final fields, no setters. |
| Payment and reservation are reliable external providers | No retry, rollback or error handling is built around them. |

## 3. Structure

```
uk.gov.dwp.uc.pairtest
├── TicketService                 (provided interface, unchanged)
├── TicketServiceImpl             (the service)
├── domain
│   └── TicketTypeRequest         (immutable request + Type enum)
└── exception
    └── InvalidPurchaseException  (provided, unchecked)

thirdparty.paymentgateway.*       (provided, unchanged)
thirdparty.seatbooking.*          (provided, unchanged)
```

A single service class sits between the caller and the two external services: caller -> `TicketService` -> external services. There are no further layers because the logic is small enough to stay in one class. If the rules grow, the validation can move into its own collaborator without changing the public interface.

## 4. Key design decisions and why

### 4.1 Program to interfaces, constructor injection
`TicketServiceImpl` takes `TicketPaymentService` and `SeatReservationService` in its constructor and depends only on those interfaces.

- The two collaborators can be replaced in tests (Mockito mocks) without changing production code.
- The provided third-party implementations are never modified, which the constraints require.

### 4.2 Pricing and seat rules live on the `Type` enum
Each `Type` constant carries its own price and whether it needs a seat:

| Type | Price | Seat |
|---|---|---|
| ADULT | £25 | yes |
| CHILD | £15 | yes |
| INFANT | £0 | no |

- Prices are whole pounds held as `int`. This is dictated by the provided `makePayment(long, int)` signature, so fractional amounts are not supported.
- Adding or changing a ticket type touches one place; `TicketServiceImpl` loops over `Type.values()` and does not branch on specific types when calculating amount and seats.
- The business rule "infants pay nothing and get no seat" is expressed as data (`0`, `false`), not as `if` statements.

### 4.3 Immutable `TicketTypeRequest`
The class is `final` with `private final` fields. A request cannot change after it is created, so validation and calculation always see the same values. A unit test checks this by reflection.

### 4.4 Validate first, then act
`purchaseTickets` runs in a fixed order:

```
validateAccount -> countTickets (+ per-request and limit validation) -> validateAdultPresent -> calculateAmount / calculateSeats -> pay -> reserve
```

- All validation completes before any external call, so an invalid purchase never causes a payment or a reservation. This is verified with `verifyNoInteractions` in the tests.
- Payment is requested before seats are reserved. The tests verify this order and that each service is called exactly once.

### 4.5 Counting by type
Requests are totalled into one array indexed by `Type.ordinal()`. This makes repeated requests of the same type (for example two separate `ADULT` requests) add up correctly, and lets the later rules work on totals rather than on the raw request list.

### 4.6 Rejection through a single unchecked exception
Every invalid input throws `InvalidPurchaseException` with a specific message. The rules applied are:

| Rule | Source |
|---|---|
| Account id must be non-null and greater than zero | Assumption in the requirements (ids above zero are valid) |
| At least one request; no null request; no null type | Chosen invalid-input checks (the task leaves "invalid" to the implementer) |
| Quantity per request must be greater than zero | Chosen invalid-input check |
| Total tickets at most 25 (infants included) | Business rule |
| At least one adult ticket | Business rule: child and infant tickets cannot be bought without an adult |

No rule beyond these is applied. `TicketTypeRequest` does not validate itself, so `TicketServiceImpl` is the single validation boundary.

### 4.7 Overflow guard
While counting, a running total of all tickets is kept. Each request is checked as `noOfTickets > 25 - total` before it is added. The running total is never above 25, so the subtraction cannot overflow and a huge request is rejected before any addition happens (for example `ADULT 1` followed by `ADULT Integer.MAX_VALUE`). Checking `total + noOfTickets` after adding would wrap around to a negative number and pass.

### 4.8 Fail-fast construction
The `TicketServiceImpl` constructor rejects null collaborators with `Objects.requireNonNull`, so a wiring mistake fails at start-up rather than on the first purchase.

## 5. Testing strategy

The project was built test-first in red/green commit pairs (see `git log`).

| Suite | Style | What it covers |
|---|---|---|
| `TicketTypeRequestTest` | Unit | Getters, immutability, price and seat rule per type |
| `TicketServiceImplTest` | Unit with mocked collaborators | Calculation, aggregation, every validation rule, call order |
| `TicketServiceIntegrationTest` | Real third-party implementations, wrapped with Mockito delegation only to observe calls | A few end-to-end checks through the public `TicketService` interface (the detailed rule matrix stays in the unit suite). The provided implementations are no-ops, so these prove wiring, not behaviour |

Mocks cover the interaction checks (what was called, how often, in what order). The integration suite shows the service works with the real provided implementations.

## 6. Not included

- No retry, compensation or transaction handling: the requirements say payment and reservation always succeed. The consequence is that payment and reservation are not atomic: if `reserveSeat` failed after `makePayment` succeeded, the customer would be charged with no seats, and the service could not undo it because neither provided interface offers a refund or cancel call.
- No idempotency protection: the interfaces carry no request id, so a caller that retries a purchase after a timeout would be charged twice.
- No account lookup or balance check: the requirements say ids above zero are valid with sufficient funds.
- No logging or configuration: not required.
