# Saga

## Intent

Coordinate a multi-step business transaction that spans multiple services/databases by breaking it into a sequence of local transactions, each with a compensating action that undoes it if a later step fails.

## Problem

A business operation needs atomicity across service boundaries (e.g. "reserve inventory, charge payment, create shipment"), but there is no distributed transaction coordinator (2PC) — each service owns its own database, and holding locks across services/processes for the duration of the operation doesn't scale and doesn't survive partial outages. Without a Saga, a failure partway through leaves the system in an inconsistent state: inventory reserved but payment never charged, or payment charged but shipment never created, with no defined mechanism to reconcile it.

## When to use

- A business transaction spans two or more services/databases, each independently deployable and independently failable, and there is no shared transaction manager.
- Steps have natural, expressible compensations (cancel reservation, refund payment, void shipment) — the domain supports "undo," even if it isn't a literal rollback.
- The operation is long-running (seconds to days) — e.g. order fulfillment, travel booking with multiple providers — where holding a lock/transaction open isn't viable.
- You need eventual consistency across services and can tolerate a window where intermediate state is visible.

## When NOT to use

- The entire transaction can live inside one service and one database transaction. Reaching for a Saga here adds a state machine, compensations, and eventual-consistency semantics to a problem `@Transactional` already solves atomically.
- Failure scenario: a team implements a Saga for a multi-table update that is actually all within one microservice's database — they now have to build and test compensating actions for a case a `ROLLBACK` already handled for free.
- A step has no meaningful compensation (e.g. "send a confirmation email" can't be un-sent, "call an external non-refundable API"). Modeling it as a Saga step implies it can be compensated when it can't — the real design needs that step to be last, idempotent-and-safe-to-retry, or handled with a different strategy (e.g. outbox + manual reconciliation).
- Failure scenario: a Saga is implemented with compensating actions coded but never exercised in tests — the first real partial failure in production reveals the compensation is itself broken (wrong amount refunded, compensation not idempotent, compensation issued twice on retry), turning a recoverable failure into a data-integrity incident.
- The steps must be strictly atomic and isolated (no other transaction may observe intermediate state) — Sagas give eventual consistency, not isolation; if the domain can't tolerate a window of partial state (e.g. double-selling the last unit of inventory to two Sagas racing), you need a different mechanism (pessimistic reservation, single-service transaction) not a Saga.

## Structure

Two common coordination styles:

- **Choreography** — each service publishes an event on completing its local transaction; the next service reacts. No central coordinator; good for a small number of steps, gets hard to trace as steps grow.
- **Orchestration** — a central saga orchestrator calls each participant, tracks state, and triggers compensations in reverse order on failure. Easier to reason about and test; the orchestrator becomes a single place to look at overall progress.

```java
class OrderSagaOrchestrator {

    void createOrder(OrderRequest request) {
        SagaState state = new SagaState(request);
        try {
            inventoryClient.reserve(request.items());
            state.markReserved();

            paymentClient.charge(request.paymentInfo());
            state.markCharged();

            shippingClient.createShipment(request);
            state.markShipped();
        } catch (Exception e) {
            compensate(state); // undo completed steps in reverse order
            throw new OrderCreationFailedException(request.orderId(), e);
        }
    }

    private void compensate(SagaState state) {
        if (state.isCharged())   paymentClient.refund(state.paymentId());
        if (state.isReserved())  inventoryClient.release(state.reservationId());
    }
}
```

In production this is usually event-driven (each step is a message handler, state persisted between steps) rather than a single synchronous method, so the orchestrator survives a process restart mid-saga.

## Consequences

Benefits: achieves consistency across services without a distributed transaction coordinator; each local transaction stays fast and simple; failure handling (compensation) is explicit and visible rather than implicit.

Costs: no isolation — other transactions can observe intermediate saga state (e.g. inventory shown as reserved before payment is confirmed); compensations must be idempotent and are themselves failure-prone (compensation call can fail too, requiring retry/dead-letter handling); debugging requires tracing a saga's state across services/logs rather than one stack trace; significant added complexity — a state machine, a persistence store for saga state, and monitoring/alerting to detect stuck sagas.

## Related patterns

- [Outbox](../integration/outbox.md) — used to reliably publish each saga step's event alongside the local transaction that produced it, avoiding a lost-event/lost-state mismatch.
- [Event-Driven](../architectural/event-driven.md) — choreography-style sagas are built on this.
- [Idempotency](idempotency.md) — every saga step and every compensation must be idempotent, since retries and redeliveries are expected.
- [Retry](retry.md) — used within a saga step to handle transient failures before falling back to compensation.
- [Circuit Breaker](circuit-breaker.md) — protects saga steps calling flaky downstream services from cascading into a stuck saga.

## Smells that suggest this pattern

- A "distributed transaction" implemented as a sequence of synchronous service calls with no rollback path — the code has a happy path across three services and nothing for the case where step 2 fails after step 1 succeeded.
- Manual runbooks/tickets titled "reconcile order state" that exist because nobody automated the undo path for a multi-service operation.
- A service directly opens/manages a transaction against another service's database (or expects it to), because there was no other agreed way to keep them consistent.

## Common misuse

- Building a full orchestrator (state store, step handlers, compensation registry) for a two-step operation that could be a single retried call plus a background reconciliation job.
- Writing compensating actions but never testing the compensation path itself — the failure injection test suite covers "step 2 succeeds" but not "step 2 fails, does step 1's compensation actually run and actually work."
- Compensations that aren't idempotent — a redelivered failure event triggers `refund()` twice because the refund step didn't check whether it already ran.
- Choreography sagas where the event chain is undocumented — six services react to each other's events with no single place that shows the overall flow, making the "saga" only discoverable by grepping event handlers across repos.
