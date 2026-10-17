# Producer-Consumer

## Intent

Decouple the code that generates work items from the code that processes them, using a bounded, thread-safe queue as the handoff point.

## Problem

Producers and consumers often run at different, unpredictable rates. If a producer calls consumer logic directly (or vice versa), the two become temporally coupled: a slow consumer stalls the producer's thread, a fast producer can overwhelm the consumer, and neither side can be scaled, retried, or restarted independently. You need a way to let producers keep producing and consumers keep consuming without either blocking on the other's pace, while still bounding how much unconsumed work can pile up in memory.

## When to use

- One or more threads generate items (file lines, incoming requests, sensor readings) at a rate that doesn't match the processing rate.
- You need backpressure: producers should block or reject when consumers fall behind, instead of buffering unboundedly.
- You want to fan work out to a pool of worker threads pulling from a single queue.
- Producer and consumer have different failure/retry semantics and should be able to fail independently without crashing the other side.
- You're building a batching layer (accumulate N items or T milliseconds, then flush) — the queue is the accumulation buffer.

## When NOT to use

- Single producer, single consumer, synchronous request/response where the caller needs the result immediately — a direct method call is simpler and you lose nothing since there's no concurrency to buffer against.
- The "queue" is unbounded and nobody has thought about what happens when the consumer stops — this converts a backpressure mechanism into a slow-motion `OutOfMemoryError`. An `ArrayBlockingQueue` with a real capacity is a design decision; a `LinkedBlockingQueue` with the default (`Integer.MAX_VALUE`) capacity is usually an accident.
- Ordering matters strictly across multiple producers and the queue doesn't preserve it (e.g. a thread-pool of consumers processing a single FIFO queue out of order) — you'll need a different structure (per-key queues, sequencing) or a single consumer.
- The work is trivially cheap (sub-microsecond) and the queue's synchronization overhead costs more than the work itself — just do it inline.

## Structure

One or more producer threads push to a shared `BlockingQueue`; one or more consumer threads pull from it. The queue itself provides the mutual exclusion and blocking/backpressure — no explicit locks needed.

```java
BlockingQueue<Task> queue = new ArrayBlockingQueue<>(1000);

// Producer
void produce(Task task) throws InterruptedException {
    queue.put(task); // blocks if the queue is full — this IS the backpressure
}

// Consumer
void consume() {
    while (!Thread.currentThread().isInterrupted()) {
        try {
            Task task = queue.take(); // blocks if empty
            process(task);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
        }
    }
}
```

## Consequences

- **Benefits**: producers and consumers scale, fail, and deploy independently; the bounded queue gives free, built-in backpressure; throughput improves because neither side idles waiting for a direct call to return.
- **Costs**: an extra hop means added latency per item versus a direct call; queue depth is a new failure mode to monitor (a growing queue silently signals a stuck or slow consumer, often invisible until memory pressure or SLA breach); poison messages (items that repeatedly fail to process) can block a consumer indefinitely without a dead-letter strategy; debugging requires tracing an item across two threads instead of one call stack, which is materially harder to reproduce locally than a straight-line bug.
- Shutdown is easy to get wrong: consumers blocked in `take()` need an explicit wake-up (poison pill, interrupt, or a queue with a timed poll) or the process hangs on exit.

## Related patterns

- [Thread Pool](thread-pool.md) — consumers are usually a fixed pool of worker threads pulling from the queue.
- [Message Broker](../integration/message-broker.md) — the distributed, cross-process version of this same pattern (Kafka, RabbitMQ).
- [Circuit Breaker](../distributed/circuit-breaker.md) — often paired at the consumer side to stop pulling when downstream is failing.
- [Actor](actor.md) — an alternative decoupling model where each actor owns its own inbox instead of sharing one queue.

## Smells that suggest this pattern

- A producer method that calls consumer logic synchronously and blocks noticeably under load.
- Manual polling loops (`while (true) { check for new work; sleep(100); }`) instead of a blocking wait — this is Producer-Consumer done with a spinlock instead of a queue.
- A shared mutable list plus hand-rolled `synchronized`/`wait`/`notify` for handoff — this is what `BlockingQueue` already solves correctly.
- Producers silently dropping work when the consumer is slow, because there was never an explicit bound or backpressure policy.

## Common misuse

- Unbounded queues "to be safe" — this doesn't remove backpressure, it just moves the failure from a blocked `put()` to an OOM kill, which is strictly worse because it's less predictable and loses in-flight work.
- No shutdown/poison-pill strategy — the process hangs on `take()` forever when told to stop, and gets killed with `-9` in production instead of draining cleanly.
- Using a queue for two threads that are trivially callable directly, adding a thread hop and synchronization for a problem that concurrency didn't create.
- One giant shared queue for many logically distinct workloads, so a burst in one workload starves consumers of an unrelated workload — separate queues per workload class would give independent backpressure.
