# Publish-Subscribe (Pub/Sub)

## Intent

Let producers publish events to a named topic without knowing who (if anyone) is listening, and let consumers subscribe to topics they care about without knowing who produces them — decoupling both the number and identity of participants on each side.

## Problem

When a producer needs to notify multiple interested parties of something that happened (an order was placed, a user signed up), calling each interested party directly means the producer must know the full list of subscribers, and that list grows every time a new feature wants to react to the same event. The producer's code changes every time someone downstream wants to add a new reaction to an event that has nothing to do with the producer's own responsibility.

## When to use

- One event needs to trigger independent reactions in multiple, unrelated consumers (e.g., "user signed up" → send welcome email, provision account, update analytics, notify sales) and that list of reactions is expected to grow.
- Producers and consumers are owned by different teams and shouldn't need to coordinate deployments to add a new subscriber.
- You want new consumers addable without changing the producer at all — pure Open/Closed at the integration level.
- The domain event genuinely represents "something happened" (past tense, fact) rather than "please do this thing for me" (a command, which implies a specific, known handler).

## When NOT to use

- There's exactly one consumer for the event and no realistic prospect of a second. This is just a direct call (or a single-consumer queue) wearing a pub/sub costume — you pay the topic/subscription management overhead for a 1:1 relationship that a direct method call or REST call handles more simply and with clearer error semantics.
- The publisher needs to know whether the operation succeeded, or needs a return value. Pub/sub is fire-and-forget by design — bolting a response channel onto it (see Message Broker's failure scenario 1) means you've built request/response worse than request/response would have built itself.
- The "event" is actually a command in disguise ("ProcessRefund") aimed at one specific, known service. Publishing a command as if it were a broadcast event obscures who's actually responsible for handling it — send it directly or via a point-to-point queue instead.

**Failure scenarios:**
1. A team uses pub/sub for "please charge this customer's card," publishing a `ChargeRequested` event that exactly one payment service subscribes to. When the charge fails, there's no publisher-visible error — the checkout flow has already returned success to the user, and someone has to build a separate mechanism just to notify the customer their payment actually failed. A direct synchronous call to the payment service, with a proper error response, would have made the failure visible where it needed to be, immediately.
2. A "UserUpdated" topic accumulates twelve subscribers over two years, each doing something slightly different in reaction, and nobody can now answer "what happens when a user's email changes?" without checking twelve separate codebases. The implicit fan-out that made pub/sub attractive also made the system's actual behavior untraceable from any single vantage point.

## Structure

```
Publisher ──▶ [ Topic: "OrderPlaced" ]
                    │
       ┌────────────┼────────────────┐
       ▼            ▼                ▼
  EmailService  InventoryService  AnalyticsService
  (subscriber)   (subscriber)      (subscriber)
```

```java
// Publisher: emits a fact, knows nothing about who reacts to it
@Service
class OrderService {
    private final ApplicationEventPublisher events; // or a broker-backed publisher

    void placeOrder(Order order) {
        orderRepository.save(order);
        events.publishEvent(new OrderPlaced(order.id(), order.total()));
    }
}

// Subscribers: added independently, no change to OrderService required
@Component
class EmailOnOrderPlaced {
    @EventListener
    void handle(OrderPlaced event) { emailService.sendConfirmation(event.orderId()); }
}

@Component
class InventoryOnOrderPlaced {
    @EventListener
    void handle(OrderPlaced event) { inventoryService.reserve(event.orderId()); }
}
```

At in-process scale this can be Spring's `ApplicationEventPublisher`; across services it's typically a broker topic (Kafka, SNS, RabbitMQ exchange) — see [Message Broker](message-broker.md) for the underlying transport.

## Consequences

**Benefits:**
- New subscribers added with zero change to the publisher — genuine Open/Closed at the integration boundary.
- Publisher's responsibility stays narrow: emit facts about its own domain, not orchestrate every downstream reaction.
- Natural fit for eventual-consistency workflows where reactions don't need to happen within the original request.

**Costs:**
- Eventual consistency: subscribers process asynchronously, so there's a window where the rest of the system hasn't "caught up" to the event yet — code that assumes immediate consistency across services will have subtle bugs here.
- Implicit fan-out makes system behavior hard to trace — "what happens when X" requires grepping every codebase for subscribers to that topic, since there's no central list.
- No built-in feedback to the publisher about subscriber failures — a subscriber silently failing to process an event doesn't roll back or even necessarily surface anywhere the publisher's team will see.
- Schema evolution is a shared contract across every subscriber; changing an event's shape can silently break subscribers the publishing team doesn't know exist.

## Related patterns

- [Message Broker](message-broker.md) — the broker is the infrastructure pub/sub usually runs on; pub/sub is the interaction style. You can have a broker without pub/sub semantics (e.g., point-to-point queues), and conceptually pub/sub without a broker (in-process event bus, as in the Spring example above).
- [Outbox](outbox.md) — needed when the publish step must be atomic with a DB write, which is the common case for domain-event publishers.
- [Idempotency](../distributed/idempotency.md) — subscribers must tolerate at-least-once delivery and possible redelivery/replay.
- [Observer](../behavioral/observer.md) — pub/sub is essentially the distributed, decoupled-identity version of the in-process Observer pattern; Observer assumes subject and observers are in the same process and often the same call stack, pub/sub explicitly does not.

## Smells that suggest this pattern

- A service directly calling out to 3+ other services purely to notify them something happened, with the list of calls growing every quarter as new features want to react.
- A `NotificationService` (or similar) hardcoding calls to every team that wants to know about an event, becoming a bottleneck every new subscriber has to go through.
- Feature teams asking the owning team to "add one more call" to their service whenever they want to react to that service's events.

## Common misuse

- Using pub/sub for what's actually a request that needs a response — see failure scenario 1. If you need to know whether it worked, it's not a broadcast event.
- Letting a topic's subscriber list grow unbounded and undocumented until nobody can reason about the event's full blast radius — see failure scenario 2. A registry or living documentation of "who subscribes to what" is worth maintaining once you're past a handful of subscribers.
- Publishing overly generic, catch-all events ("EntityChanged") that force every subscriber to inspect the payload to figure out what actually happened — defeats the purpose of a typed, meaningful event contract.
