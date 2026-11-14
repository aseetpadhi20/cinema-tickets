# Design notes

`TicketServiceImpl` is the only class callers use. It validates the request, works out the price and the seats, then makes one payment request and one seat reservation request. Nothing is sent to either service unless the whole purchase is valid.

## How it is split

- `PurchaseValidator` holds the rules.
- `TicketOrder` adds up the tickets per type and works out the amount and the seats.
- `TicketTypeRequest.Type` holds each ticket's price and whether it needs a seat, so adding a ticket type is a change in one place.
- Rejections throw `RejectedPurchaseException`, which extends the provided `InvalidPurchaseException` and carries a reason, so a caller can tell why a purchase failed without reading the message. The provided exception itself is untouched.

`PurchaseValidator` and `TicketOrder` are package-private because nothing outside the package needs them.

## What I treat as invalid

The brief leaves this open, so these are my choices:

- an account id that is null, zero or negative
- no ticket requests, a null request, or a request with no ticket type
- a quantity of zero or less
- more than 25 tickets in total, with infants counting towards the 25
- child or infant tickets without an adult ticket

## Decisions

- There is no limit on infants per adult. The brief says infants sit on an adult's lap but gives no ratio, so I did not add one.
- Amounts are whole pounds in an `int`, because `makePayment` takes an `int`.
- Counts are kept as `long` and any single request over 25 is rejected before anything is added, so very large quantities cannot overflow.
- Payment is requested before the seat reservation.

## Not handled

The brief says both services always succeed, so I did not handle their failures:

- If the reservation failed after the payment went through, the customer would be charged and get no seats. Neither service has a refund or cancel call to undo it.
- A purchase that is retried would be charged twice, because there is no request id to spot the repeat.
