# Event-Driven Architecture

## Intent

Components communicate by publishing and reacting to events — facts about something that already happened — rather than calling each other synchronously, decoupling producers from consumers in both time and knowledge.

## Problem

Synchronous call chains (A calls B calls C calls D) couple every caller to every downstream service's availability and latency, so one slow or down downstream degrades the entire chain. Separately, when a single action needs to trigger several independent side effects (order placed → send email, update inventory, record analytics), direct calls force the producer to know about, call, and handle failures for every consumer, even ones it has no real business knowing about.

Concretely: if `OrderService.placeOrder()` directly calls `EmailService.send()`, and the email provider is down, the order either fails to place (unacceptable — email isn't essential to placing an order) or the failure has to be manually swallowed and logged inline, ad hoc, in every place that calls `EmailService`. Publishing `OrderPlaced` and letting `EmailListener` fail independently removes that coupling entirely.

## When to use

- Genuine fan-out: one event has multiple independent consumers that shouldn't block each other or need to know about each other.
- Producer and consumer have different failure, scaling, or deploy characteristics — a spike in order volume shouldn't block on a slow email provider.
- Decoupling bounded contexts or modules so each can evolve and deploy independently, without one team's API changes forcing lockstep changes in another module.
- Building a natural audit or history trail from a stream of things that happened, rather than bolting an audit log onto synchronous calls after the fact.
- Cross-module communication inside a modular monolith — e.g. this project's use of Spring Modulith's `@ApplicationModuleListener`.
- Integrating with external systems that are themselves event-based (webhooks, IoT telemetry, third-party notification streams) where forcing a synchronous model would require polling or artificial blocking.
- Workflows that are naturally a sequence of state transitions triggered by outside occurrences (order lifecycle: placed → paid → shipped → delivered), where each transition is a natural event boundary.

## When NOT to use

- When synchronous request/response is simpler to reason about and there's no real decoupling need. Concrete failure: a simple "validate then save" two-step operation gets rewritten as publish-a-validation-requested-event, listener-does-the-save — now a stack trace no longer shows the causal chain, and diagnosing a bug means digging through event logs or distributed traces instead of reading a call stack top to bottom.
- A latency-sensitive path where the caller needs the result synchronously before proceeding — e.g. "is this coupon valid" needed before completing checkout. Going asynchronous here means either blocking on a response anyway (defeating the purpose of decoupling) or building a polling/callback mechanism to simulate what a plain function call already returns in milliseconds.
- A small system with a single deploy unit and no plausible need for independent scaling or decoupling — a todo app does not need an event bus; the indirection has no corresponding payoff.
- When the team isn't ready to operate the failure modes events introduce: a dropped event or a down consumer means a side effect silently never happens, with no obvious error at the call site, versus a synchronous call that fails loudly and immediately. Concrete failure: the email-on-order-placed listener throws on a malformed address, the exception is swallowed by the event dispatcher's default error handling, and nobody notices customers stopped receiving confirmation emails for a week.

## Structure

A producer publishes an event (named as a past-tense fact — `OrderPlaced`, not `PlaceOrder`) to a channel. One or more consumers subscribe and react independently. The producer has zero knowledge of who, if anyone, is listening.

```
publisher/
  OrderService                → publishes OrderPlaced after committing the order

listeners/
  InventoryListener           → reacts to OrderPlaced, decrements stock
  EmailListener                → reacts to OrderPlaced, sends confirmation
  AnalyticsListener            → reacts to OrderPlaced, records the event
```

Two materially different flavors:
- **In-process** (same deploy unit): e.g. Spring's `ApplicationEventPublisher` or Spring Modulith's `@ApplicationModuleListener` — transactional by default, still synchronous unless explicitly configured `@Async`. Failure and ordering semantics are closer to a direct call.
- **Cross-process** (message broker — Kafka, RabbitMQ, SQS): genuinely asynchronous, typically at-least-once delivery, ordering not guaranteed unless explicitly partitioned. Requires operating a broker and handling redelivery, ordering, and idempotency as first-class concerns.

## Consequences

**Benefits:**
- Producer/consumer decoupling — add a new consumer without touching the producer's code at all.
- Independent scaling and failure isolation, since one slow consumer doesn't block the producer or any other consumer.
- A natural audit trail falls out of the event stream without any extra logging work.
- Modules or services can deploy independently once they only communicate through well-defined event contracts.

**Costs:**
- Eventual consistency — a consumer processes after the producer commits, and there's a real window where the system isn't fully settled.
- Harder debugging, since there's no single stack trace; correlation IDs and distributed tracing become necessary just to answer "what happened after X."
- Failure handling is now an explicit design problem (retry policy, dead-letter queue, who monitors it and what they do when it fires) rather than something a caller gets for free from a function call that throws.
- Ordering and idempotency become the team's problem to solve rather than implicit in call order — a consumer must tolerate out-of-order or duplicate delivery.
- Cross-process setups add the operational overhead of running, scaling, and monitoring a broker, plus schema/contract versioning for the events themselves.

## Related patterns

- [Saga](../distributed/saga.md) — coordinates multi-step processes that span several events and services when no single transaction can cover them all.
- [Message Broker](../integration/message-broker.md) and [Pub/Sub](../integration/pub-sub.md) — the typical transport for cross-process events.
- [Outbox](../integration/outbox.md) — publishes events reliably alongside a database transaction, avoiding the "committed the write but lost the event" failure mode.
- [Idempotency](../distributed/idempotency.md) — necessary because at-least-once delivery means consumers may see the same event more than once.
- [Observer](../behavioral/observer.md) — the in-process, single-deploy-unit ancestor of this pattern; event-driven architecture is Observer applied at system/service scale.
- [CQRS](cqrs.md) — often uses an event-driven mechanism to sync its read model from the write model, but the two patterns are independent: CQRS can update synchronously, and event-driven architecture is useful with no CQRS split at all.
- **Spring Modulith aside**: this project's primary use of event-driven architecture is `@ApplicationModuleListener` for cross-module communication inside a single deployable. That gets module decoupling and (with event externalization) an audit trail without standing up a separate broker — a middle ground between a fully synchronous layered call and a cross-process message broker.

## Smells that suggest this pattern

- A method that, after doing its core job, has a growing list of unrelated calls tacked onto the end ("save the order, then also email, also update inventory, also log analytics, also notify shipping") — each "also" is a candidate event listener.
- Modules reaching directly into another module's internal service classes across what's supposed to be an independent bounded-context boundary.
- A change to one module's internals repeatedly forcing changes in an unrelated module because they're wired together by direct calls instead of a published contract.
- A service class with a growing list of injected collaborators whose only purpose is to be notified after the main operation succeeds.

## Common misuse

- Wrapping every internal method call in a publish/subscribe indirection "for decoupling" when there's exactly one consumer and it needs the result synchronously anyway — this trades a direct, traceable call for an indirect one with no actual decoupling benefit, only added latency and harder debugging.
- Using events to express imperative command intent as if they were RPC — a `DoDiscountCalculation` event published expecting one specific consumer to compute a value and reply. That's a direct call wearing an event's naming convention; use a direct call or explicit [Command](../behavioral/command.md) for anything that expects a specific answer back. Reserve events for facts that have already happened, with no expectation of a reply.
- Introducing a message broker for cross-process events when the actual need was in-process decoupling within a single deploy unit — paying operational cost (broker infrastructure, delivery guarantees, monitoring) for a problem `ApplicationEventPublisher` or `@ApplicationModuleListener` would have solved with none of it.
- Publishing an event and immediately, synchronously blocking on all of its listeners completing before returning — this keeps every cost of the indirection (no direct stack trace, no compile-time caller list) while giving up the actual benefit (failure isolation, independent scaling) that justified using events in the first place.
