# Cinema Tickets (Java)

Solution to the DWP cinema tickets exercise. See [ARCHITECTURE.md](ARCHITECTURE.md) for the design.

## Requirements

- Java 21
- Maven 3.9 or newer

## Compile

```
mvn compile
```

## Run

This is a library, so there is no `main` class to start. The way to run the code is through the tests.

## Test

Run everything (unit tests, integration tests and the Cucumber scenarios):

```
mvn clean test
```

Run one test class:

```
mvn test -Dtest=PurchaseValidatorTest
```

Run only the integration tests:

```
mvn test -Dtest=TicketServiceIntegrationTest,RunCucumberTest
```

Mockito prints a warning about self-attaching an agent. It is harmless and the tests still pass.

## Build the jar

```
mvn clean package
```

The jar is created in `target/`.
