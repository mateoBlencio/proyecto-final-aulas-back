# Message Broker

## Intent

Decouple producers and consumers of messages by routing all messages through an intermediary (Kafka, RabbitMQ, ActiveMQ, SQS) instead of having services call each other directly.

## Problem

Direct service-to-service calls couple the caller to the callee's availability, location, and pace — if the callee is down or slow, the caller either fails or blocks. As the number of services grows, direct point-to-point integration becomes an O(N²) mesh of connections, each with its own retry/timeout/circuit-breaking logic. A broker gives services one thing to talk to (the broker) instead of every other service directly, and lets producer and consumer run at different speeds without one blocking the other.

## When to use

- A producer needs to hand off work without waiting for the consumer to finish processing it (e.g., "order placed" triggers email, inventory update, and analytics — none of which the checkout request should wait on).
- Multiple independent consumers need the same message (fan-out) without the producer knowing who they are.
- You need durability: messages must survive a consumer being down, and be processed once it comes back up, instead of being lost.
- Load leveling: producers burst, consumers process at a steady rate, and the broker absorbs the difference as a queue.
- Services are owned by different teams and you want to avoid direct network coupling (contract is "the message schema," not "the other team's endpoint").

## When NOT to use

- The interaction is fundamentally synchronous request/response — the caller needs the result before it can proceed (e.g., "validate this payment and tell me now if it succeeded"). Routing this through a broker adds a round trip, complicates error propagation (how does the caller learn about a failure two hops away?), and buys nothing over a direct call.
- Two services, one message type, no fan-out, no need for durability beyond the caller's own retry. A direct REST/gRPC call with a [Retry](../distributed/retry.md) policy solves this with far less infrastructure.
- The team has no one who can operate a broker (monitor consumer lag, handle poison messages, manage partitions/topics) — introducing one without that operational capacity trades an availability problem for a bigger one.

**Failure scenarios:**
1. A team puts Kafka between two services for a simple "check inventory, get a yes/no back" call because "everything should be async and decoupled." The caller now has to publish a request, poll or subscribe for a correlated response, and handle the case where no response ever arrives — reimplementing request/response semantics badly on top of a tool built for one-way messaging, when a plain synchronous HTTP call would have been simpler and easier to debug.
2. A five-person team adopts Kafka for a low-volume internal system, then spends more engineering time tuning partition counts, debugging consumer group rebalancing, and recovering from under-replicated partitions than they ever spend on the business logic the broker was supposed to support.

## Structure

```
Producer ──publish──▶ [ Broker: Topic/Queue ] ──deliver──▶ Consumer A
                                               └──deliver──▶ Consumer B
```

```java
// Producer: fire-and-forget, doesn't know or care who consumes it
@Service
class OrderService {
    private final KafkaTemplate<String, OrderPlaced> kafka;

    void placeOrder(Order order) {
        orderRepository.save(order);
        kafka.send("order-placed", order.id(), new OrderPlaced(order.id(), order.total()));
    }
}

// Consumer: independent, processes at its own pace, can be down without blocking the producer
@KafkaListener(topics = "order-placed", groupId = "email-service")
void onOrderPlaced(OrderPlaced event) {
    emailService.sendConfirmation(event.orderId());
}
```

## Consequences

**Benefits:**
- Producer and consumer availability/pace are decoupled — a slow or down consumer doesn't block the producer.
- Fan-out to N consumers without the producer knowing about any of them.
- Durability: broker persists messages until acknowledged, surviving consumer restarts/crashes.
- Absorbs load spikes (queue depth grows instead of consumers falling over).

**Costs:**
- Real infrastructure to run and monitor: broker uptime, disk usage, partition/queue configuration, consumer lag, dead-letter queues, poison-message handling.
- Debugging is harder — a request's effects are now spread across an async chain instead of one call stack; tracing requires correlation IDs threaded through every hop.
- Delivery semantics matter and are easy to get wrong: at-least-once delivery means consumers must be idempotent (see [Idempotency](../distributed/idempotency.md)) or you get duplicate side effects.
- Ordering guarantees are broker- and configuration-specific (e.g., per-partition ordering in Kafka, not global) — code that assumes global ordering will break.

## Related patterns

- [Pub-Sub](pub-sub.md) — the broker is the infrastructure/mechanism; pub/sub is the interaction *style* (many-to-many, topic-based) typically implemented on top of a broker. A broker can also support point-to-point queues (one consumer per message) without being pub/sub. Don't use the terms interchangeably: "message broker" answers "what do we run," "pub/sub" answers "how do producers and consumers relate."
- [Outbox](outbox.md) — solves the specific problem of atomically committing a DB write and a broker publish; needed whenever a service both persists state and publishes an event about it in the same operation.
- [Idempotency](../distributed/idempotency.md) — required on the consumer side whenever the broker gives at-least-once delivery, which is the common case.
- [Retry](../distributed/retry.md) — governs how a consumer handles a message it failed to process, often paired with a dead-letter queue after N attempts.

## Smells that suggest this pattern

- Service A calls Service B synchronously purely to trigger a side effect (send email, update a search index) that A doesn't need the result of before continuing.
- A growing point-to-point mesh where adding a new consumer of an event means modifying the producer's code to add another direct call.
- Producers timing out or failing because a downstream consumer is temporarily unavailable, even though the work itself could tolerate being delayed.

## Common misuse

- Using a broker to fake synchronous request/response (see failure scenario 1) instead of just making a direct call.
- Treating the broker as a database — reading old messages by replaying from offset 0 as a substitute for actually persisting state in a proper store. Brokers are for message delivery, not long-term queryable storage.
- Standing up a broker for a two-service, low-volume system where the operational cost (see failure scenario 2) permanently exceeds the coupling problem it was meant to solve.
