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
├── TicketServiceImpl             (orchestrates validate, pay, reserve)
├── PurchaseValidator             (all validation rules)
├── TicketOrder                   (counts per type, amount and seats)
├── domain
│   └── TicketTypeRequest         (immutable request + Type enum)
└── exception
    ├── InvalidPurchaseException  (provided, unchecked)
    ├── RejectedPurchaseException (subclass carrying the reason)
    └── PurchaseRejectionReason   (enum of rejection reasons and messages)

thirdparty.paymentgateway.*       (provided, unchanged)
thirdparty.seatbooking.*          (provided, unchanged)
```

The service sits between the caller and the two external services: caller -> `TicketService` -> external services. It only orchestrates. The rules live in `PurchaseValidator` and the calculations in `TicketOrder`, both package-private, so the public surface stays the same as the provided interface.

## 4. Key design decisions and why

### 4.1 Program to interfaces, constructor injection
`TicketServiceImpl` takes `TicketPaymentService` and `SeatReservationService` in its constructor and depends only on those interfaces. The public constructor creates a default `PurchaseValidator`; a package-private constructor accepts one so it can be replaced.

- The collaborators can be replaced in tests (Mockito mocks) without changing production code.
- The provided third-party implementations are never modified, which the constraints require.

### 4.2 Pricing and seat rules live on the `Type` enum
Each `Type` constant carries its own price and whether it needs a seat:

| Type | Price | Seat |
|---|---|---|
| ADULT | £25 | yes |
| CHILD | £15 | yes |
| INFANT | £0 | no |

- Prices are whole pounds held as `int`. This is dictated by the provided `makePayment(long, int)` signature, so fractional amounts are not supported.
- Adding or changing a ticket type touches one place; `TicketOrder` loops over `Type.values()` and does not branch on specific types when calculating amount and seats.
- The business rule "infants pay nothing and get no seat" is expressed as data (`0`, `false`), not as `if` statements.

### 4.3 Immutable `TicketTypeRequest`
The class is `final` with `private final` fields. A request cannot change after it is created, so validation and calculation always see the same values. A unit test checks this by reflection.

### 4.4 Validate first, then act
`purchaseTickets` runs in a fixed order:

```
validateRequest -> TicketOrder.from -> validateOrder -> pay (order.amount()) -> reserve (order.seats())
```

- All validation completes before any external call, so an invalid purchase never causes a payment or a reservation. This is verified with `verifyNoInteractions` in the tests.
- Payment is requested before seats are reserved. The tests verify this order and that each service is called exactly once.

### 4.5 `TicketOrder`
Requests are totalled per type in an immutable `TicketOrder`, backed by an `EnumMap`. Repeated requests of the same type (for example two separate `ADULT` requests) add up correctly, and the later rules and the calculations work on totals rather than on the raw request list. `TicketOrder` exposes `totalTickets()`, `hasAdult()`, `amount()` and `seats()`.

### 4.6 Rejection with a reason
Every invalid input throws `RejectedPurchaseException`, a subclass of the provided `InvalidPurchaseException`, so callers that catch the provided type still work. It carries a `PurchaseRejectionReason`, so a caller can tell "too many tickets" from "no adult" without parsing the message. The provided `InvalidPurchaseException` itself is not edited. The rules applied are:

| Rule | Source |
|---|---|
| Account id must be non-null and greater than zero | Assumption in the requirements (ids above zero are valid) |
| At least one request; no null request; no null type | Chosen invalid-input checks (the task leaves "invalid" to the implementer) |
| Quantity per request must be greater than zero | Chosen invalid-input check |
| Total tickets at most 25 (infants included) | Business rule |
| At least one adult ticket | Business rule: child and infant tickets cannot be bought without an adult |

No rule beyond these is applied. `TicketTypeRequest` does not validate itself, so `PurchaseValidator` is the single validation boundary.

### 4.7 Overflow guard
`validateRequest` rejects any single request above 25 before anything is added, and `TicketOrder` keeps its counts as `long`. Even a very large number of requests therefore cannot wrap the total around to a small or negative number, and `validateOrder` then compares the true total against 25. `amount()` and `seats()` narrow to `int` with `Math.toIntExact`, and are only called after validation has bounded the total.

### 4.8 Fail-fast construction
The `TicketServiceImpl` constructors reject null collaborators with `Objects.requireNonNull`, so a wiring mistake fails at start-up rather than on the first purchase.

## 5. Testing strategy

The project was built test-first in red/green commit pairs (see `git log`).

| Suite | Style | What it covers |
|---|---|---|
| `TicketTypeRequestTest` | Unit | Getters, immutability, price and seat rule per type |
| `TicketOrderTest` | Unit | Totals, amount, seats, adult check, aggregation, no overflow |
| `PurchaseValidatorTest` | Unit | Every validation rule, with the rejection reason asserted |
| `TicketServiceImplTest` | Unit with mocked collaborators | Calculation, aggregation, every validation rule through the service, call order |
| `TicketServiceIntegrationTest` | Real third-party implementations, wrapped with Mockito delegation only to observe calls | A few end-to-end checks through the public `TicketService` interface (the detailed rule matrix stays in the unit suite). The provided implementations are no-ops, so these prove wiring, not behaviour |

Mocks cover the interaction checks (what was called, how often, in what order). The integration suite shows the service works with the real provided implementations.

## 6. Not included

- No retry, compensation or transaction handling: the requirements say payment and reservation always succeed. The consequence is that payment and reservation are not atomic: if `reserveSeat` failed after `makePayment` succeeded, the customer would be charged with no seats, and the service could not undo it because neither provided interface offers a refund or cancel call.
- No idempotency protection: the interfaces carry no request id, so a caller that retries a purchase after a timeout would be charged twice.
- No account lookup or balance check: the requirements say ids above zero are valid with sufficient funds.
- No logging or configuration: not required.
