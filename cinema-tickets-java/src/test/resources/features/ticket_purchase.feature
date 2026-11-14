Feature: Purchasing cinema tickets

  Scenario Outline: Payment and seats are calculated from the ticket mix
    Given a customer with account id 10
    When the customer buys <adults> adult, <children> child and <infants> infant tickets
    Then the purchase is accepted
    And the customer pays £<amount>
    And the number of seats reserved is <seats>

    Examples:
      | adults | children | infants | amount | seats |
      | 1      | 0        | 0       | 25     | 1     |
      | 2      | 3        | 0       | 95     | 5     |
      | 2      | 0        | 2       | 50     | 2     |
      | 1      | 1        | 1       | 40     | 2     |
      | 25     | 0        | 0       | 625    | 25    |
      | 1      | 24       | 0       | 385    | 25    |
      | 10     | 10       | 5       | 400    | 20    |

  Scenario: Several requests of the same type are combined
    Given a customer with account id 7
    When the customer makes these ticket requests
      | type  | quantity |
      | ADULT | 1        |
      | CHILD | 1        |
      | ADULT | 1        |
      | CHILD | 1        |
    Then the purchase is accepted
    And the customer pays £80
    And the number of seats reserved is 4

  Scenario: Payment is taken before seats are reserved
    Given a customer with account id 3
    When the customer buys 1 adult, 1 child and 0 infant tickets
    Then a payment of £40 was requested before the reservation of 2 seats

  Scenario Outline: More than 25 tickets cannot be bought
    Given a customer with account id 10
    When the customer buys <adults> adult, <children> child and <infants> infant tickets
    Then the purchase is rejected with reason TOO_MANY_TICKETS
    And nothing is paid and no seats are reserved

    Examples:
      | adults | children | infants |
      | 26     | 0        | 0       |
      | 13     | 13       | 0       |
      | 10     | 10       | 6       |

  Scenario: The 25 ticket limit applies across separate requests
    Given a customer with account id 10
    When the customer makes these ticket requests
      | type  | quantity |
      | ADULT | 13       |
      | ADULT | 13       |
    Then the purchase is rejected with reason TOO_MANY_TICKETS
    And nothing is paid and no seats are reserved

  Scenario Outline: Child and infant tickets need an adult ticket
    Given a customer with account id 10
    When the customer buys <adults> adult, <children> child and <infants> infant tickets
    Then the purchase is rejected with reason ADULT_REQUIRED
    And nothing is paid and no seats are reserved

    Examples:
      | adults | children | infants |
      | 0      | 1        | 0       |
      | 0      | 0        | 1       |
      | 0      | 2        | 2       |

  Scenario Outline: Invalid account ids are rejected
    Given a customer with account id <id>
    When the customer buys 1 adult, 0 child and 0 infant tickets
    Then the purchase is rejected with reason INVALID_ACCOUNT
    And nothing is paid and no seats are reserved

    Examples:
      | id |
      | 0  |
      | -1 |

  Scenario: Zero quantities are rejected
    Given a customer with account id 10
    When the customer makes these ticket requests
      | type  | quantity |
      | ADULT | 2        |
      | CHILD | 0        |
    Then the purchase is rejected with reason INVALID_QUANTITY
    And nothing is paid and no seats are reserved

  Scenario: Negative quantities are rejected
    Given a customer with account id 10
    When the customer makes these ticket requests
      | type  | quantity |
      | ADULT | 5        |
      | CHILD | -5       |
    Then the purchase is rejected with reason INVALID_QUANTITY
    And nothing is paid and no seats are reserved

  Scenario: A huge quantity after a small one does not overflow the limit
    Given a customer with account id 10
    When the customer makes these ticket requests
      | type  | quantity   |
      | ADULT | 1          |
      | ADULT | 2147483647 |
    Then the purchase is rejected with reason TOO_MANY_TICKETS
    And nothing is paid and no seats are reserved

  Scenario: A purchase with no ticket requests is rejected
    Given a customer with account id 10
    When the customer makes no ticket requests
    Then the purchase is rejected with reason NO_TICKET_REQUESTS
    And nothing is paid and no seats are reserved
