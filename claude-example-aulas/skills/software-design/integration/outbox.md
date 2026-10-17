# Outbox

## Intent

Guarantee that a database write and the publication of an event about that write either both happen or neither happens, by writing the event to an outbox table in the same local transaction as the state change, then relaying it to the broker separately.

## Problem

A service that writes to its database and then publishes an event to a broker as two separate operations has no atomicity between them. If the DB commit succeeds and the publish fails (broker down, network blip, process crash between the two calls), the event is lost — the state changed but nothing downstream ever hears about it. If you publish first and the DB write then fails, you get an event for a change that never actually happened. Neither ordering is safe because a database transaction and a broker publish can't be wrapped in one atomic operation (no distributed transaction/2PC in practice for most stacks).

## When to use

- A service persists state and must publish an event about that change, and downstream consumers must never miss that event silently (order placed, payment captured, inventory decremented).
- You need "exactly once" semantics for the *fact that an event was produced*, even though delivery to consumers is still at-least-once (consumers still need [Idempotency](../distributed/idempotency.md)).
- You're implementing the transactional/domain-event side of an event-driven architecture, or the local steps of a [Saga](../distributed/saga.md), where a missed event breaks the whole workflow silently.
- Debugging has already surfaced a silent-data-loss incident: DB says the order was placed, but the email/inventory/analytics services never got the event because the publish call failed after the commit.

## When NOT to use

- The DB write and the "event" are consumed by the same process/transaction — i.e., there's no actual second system that needs to be told. If a single DB transaction already gives you the consistency you need (e.g., updating two tables in the same database), Outbox solves a problem you don't have; just use the transaction.
- The event is genuinely best-effort and losing it occasionally is acceptable (e.g., a non-critical analytics ping). Building an outbox table, a relay process, and monitoring for it costs real engineering time that isn't justified by a metric nobody will notice is missing.
- You can restructure the workflow so the broker publish isn't in the critical consistency path at all — e.g., a scheduled reconciliation job that periodically re-derives "what needs to be published" from current state, without needing an outbox table's bookkeeping.

**Failure scenarios:**
1. A team adds an outbox table for an internal event that's purely informational (a `PageViewed` analytics event) — building a relay process, monitoring, and cleanup job for an event where losing 0.1% of records has zero business impact. The operational cost (another poller, another failure mode to alarm on) permanently exceeds the value of the guarantee.
2. A team assumes a single Postgres transaction updating two tables in the same database needs an outbox pattern "because that's the correct way to publish events," when a plain `@Transactional` method already gives atomicity for both writes — there's no second system involved, so there's nothing for Outbox to protect against.

## Structure

```
      ┌─────────────── DB transaction ───────────────┐
      │  1. INSERT/UPDATE domain table                │
      │  2. INSERT into outbox_events (same tx)        │
      └─────────────────────────────────────────────────┘
                              │
                    (relay polls or uses CDC,
                     e.g. Debezium, on outbox_events)
                              ▼
                     Broker: publish event
                              │
                    mark outbox row as published
                     (or delete it)
```

```java
@Transactional
void placeOrder(Order order) {
    orderRepository.save(order);
    outboxRepository.save(new OutboxEvent(
        "OrderPlaced", order.id(), toJson(order))); // same transaction as the domain write
}

// Separate relay process/scheduled job — NOT part of the request path
@Scheduled(fixedDelay = 500)
void relayOutboxEvents() {
    for (OutboxEvent event : outboxRepository.findUnpublished()) {
        kafka.send(event.topic(), event.payload());
        outboxRepository.markPublished(event.id()); // or delete
    }
}
```

Spring Modulith's event publication registry implements this pattern out of the box for `@ApplicationModuleListener` — worth using directly instead of hand-rolling an outbox table in that stack.

## Consequences

**Benefits:**
- Publication becomes atomic with the domain write from the producer's point of view — no more "committed but never published" gap.
- Failure is recoverable: an unpublished row just sits in the outbox table until the relay succeeds, instead of being lost.
- No distributed transaction / 2PC needed — relies only on the local DB's ACID guarantees plus a relay that can retry.

**Costs:**
- Eventual consistency window: there's a gap between the DB commit and the actual broker publish (relay poll interval, or CDC lag) — consumers see the event slightly after the transaction committed, not atomically with it.
- Extra moving part: the relay process itself needs to be running, monitored, and alarmed on — if it silently stops, events pile up unpublished and nobody notices until someone asks "why didn't downstream react?"
- Outbox table needs its own lifecycle management (archival/cleanup of published rows) or it grows unbounded.
- At-least-once delivery is still the ceiling, not exactly-once to the consumer — a crash between publish and mark-published can redeliver, so consumers still need [Idempotency](../distributed/idempotency.md).

## Related patterns

- [Message Broker](message-broker.md) / [Pub-Sub](pub-sub.md) — Outbox solves the producer-side atomicity problem for whichever of these you're publishing through; it doesn't replace either.
- [Idempotency](../distributed/idempotency.md) — required downstream regardless of Outbox, because delivery is still at-least-once.
- [Saga](../distributed/saga.md) — Outbox is frequently the mechanism that reliably emits the events driving each step of a choreography-based saga.
- [Retry](../distributed/retry.md) — the relay's publish step should retry on transient broker failures before giving up and alerting.

## Smells that suggest this pattern

- A service's DB write succeeds but the corresponding event publish fails (broker timeout, network partition) and the event is simply gone — no record it was ever supposed to be sent.
- Manual reconciliation scripts that periodically "replay" events by re-diffing DB state against what downstream systems have, because publishing has silently dropped events before.
- Code that publishes an event *before* committing the transaction "to be safe," which actually makes it worse (event for a change that might roll back).

## Common misuse

- Building the outbox table and relay but never monitoring the relay — it silently stops polling (crashed pod, unhandled exception in the scheduled job) and the outbox table quietly grows while no new events reach consumers, for however long it takes someone to notice via a downstream complaint.
- Never cleaning up published rows, so the outbox table becomes one of the largest and slowest tables in the database years later.
- Reaching for a full outbox implementation when the actual requirement was satisfied by a single-database transaction — see failure scenario 2. Confirm there's a genuine second system in the picture before building this.
