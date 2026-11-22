# Idempotency

## Intent

Guarantee that performing the same operation more than once has the same effect as performing it exactly once, so retries and duplicate deliveries don't produce duplicate side effects.

## Problem

In a distributed system, "exactly once" delivery isn't achievable without idempotency doing the real work — networks drop responses (not requests), clients time out and retry, message brokers redeliver on ack failure, load balancers retry on connection reset. Every one of these can result in the same logical operation being executed more than once against a service that has no way to tell "this is a new charge" from "this is the same charge, sent again because the first response got lost." Without an idempotency mechanism, any retry or at-least-once delivery guarantee silently becomes a duplicate-side-effect bug: double charges, duplicate emails, double inventory decrements.

## When to use

- Any operation that will be retried (client-side retry logic, message broker at-least-once delivery, load balancer retries) and has a side effect that isn't naturally safe to repeat (payment, inventory mutation, sending a notification, creating a resource).
- Consuming from a message queue/event stream with at-least-once delivery semantics — redelivery after a crash before ack is a normal, expected occurrence, not an edge case.
- Any API endpoint that a client might legitimately call twice for the same logical intent (double-click, client-side retry after a client-visible timeout, offline-then-sync mobile clients).
- Building a Saga or any multi-step workflow with compensations — both the forward steps and the compensating actions must be idempotent, since the orchestrator may retry either after a crash.

## When NOT to use

- Naturally idempotent operations that don't need an explicit mechanism — `GET`, `PUT` with a full resource representation, `DELETE` of a specific id, `SET x = 5` — adding a dedup key/ledger on top of an operation that's already idempotent by construction is unneeded machinery.
- Failure scenario: an idempotency key is generated fresh per attempt (e.g. a new UUID each time the client calls, instead of one UUID reused across retries of the *same* logical request) — this defeats the entire mechanism; the server sees a "new" key on each retry and processes it as a new operation every time, while the team believes they've made the endpoint idempotent.
- Failure scenario: an idempotency ledger/dedup table has no expiry and no bound, and the key space is generated without enough entropy or scoping (e.g. keyed only on `userId + timestamp-to-the-minute`) — legitimate, distinct requests from the same user in the same window collide, and one gets silently dropped as a "duplicate."
- Read-only or purely idempotent-by-nature queries — building request-deduplication infrastructure around something that produces no side effect and is safe to run any number of times is effort spent protecting against a problem that doesn't exist there.

## Structure

Client generates (and reuses across retries of the same logical request) an idempotency key; server persists a record of `(key → result)` atomically with the operation's own side effect, and short-circuits on a repeat.

```java
@Transactional
PaymentResult charge(String idempotencyKey, ChargeRequest request) {
    Optional<PaymentResult> existing = idempotencyStore.find(idempotencyKey);
    if (existing.isPresent()) {
        return existing.get(); // same key, same result — no second charge
    }

    PaymentResult result = paymentProcessor.charge(request);

    // stored in the SAME transaction as the charge itself —
    // otherwise a crash between charge and store re-opens the duplicate window
    idempotencyStore.save(idempotencyKey, result);
    return result;
}
```

The critical detail: the dedup record and the side effect must be written atomically (same DB transaction, or via [Outbox](../integration/outbox.md)) — recording the key *after* an external call that can't be rolled back into the same transaction (e.g. a call to a third-party payment gateway) leaves a window where a crash between the external call and the dedup write causes exactly the duplicate this pattern exists to prevent.

## Consequences

Benefits: makes Retry and at-least-once message delivery actually safe to use; removes an entire class of duplicate-side-effect bugs; simplifies reasoning about failure — "did this run" no longer needs a case-by-case investigation.

Costs: requires a persistent store for keys/results with its own consistency and expiry policy (unbounded growth if keys never expire, false duplicate-detection if they expire too soon relative to realistic retry windows); adds a lookup on every request, and, if implemented as a separate write from the side effect, reintroduces the exact race it's meant to close; key generation and scoping must be gotten right by every caller — one caller generating a fresh key per retry silently defeats the mechanism for that caller only, which is easy to miss in review.

## Related patterns

- [Retry](retry.md) — the primary reason Idempotency exists; Retry without Idempotency is the most common way a transient failure becomes a duplicate side effect. "Retry + Idempotency + Timeout" is a standard trio.
- [Outbox](../integration/outbox.md) — a common way to atomically pair a side effect with the event/message that announces it, avoiding the write-then-crash-before-recording window described above.
- [Saga](saga.md) — every saga step and every compensating action must be idempotent, since the orchestrator retries either after a crash mid-saga.
- [Event-Driven](../architectural/event-driven.md) — at-least-once delivery is the default guarantee in most broker setups; idempotent consumers are the standard way to live with that guarantee instead of fighting it.

## Smells that suggest this pattern

- Duplicate charges, duplicate emails/notifications, or duplicate records in support tickets that correlate with client-side timeouts or known retry logic.
- A message consumer whose side effect visibly runs twice for one logical event during redelivery after a consumer crash/restart.
- "Just click submit once" as documented user guidance — an admission the endpoint isn't safe to call twice.

## Common misuse

- Generating the idempotency key fresh on every attempt (e.g. `UUID.randomUUID()` inside the retry loop) instead of once per logical request and reusing it across retries — this is the single most common way teams believe they've added idempotency without actually doing so.
- Storing the dedup record in a separate transaction/system from the side effect it's meant to guard, leaving a crash window where the two can diverge.
- No expiry policy on the idempotency store, causing unbounded growth, or too short an expiry relative to realistic client retry/backoff windows, causing the dedup check to miss legitimate retries.
- Assuming HTTP methods are idempotent by name (`PUT`/`DELETE`) without verifying the actual handler implementation has no side effect that varies by call count (e.g. a `PUT` handler that also increments an audit counter or fires a webhook on every call).
