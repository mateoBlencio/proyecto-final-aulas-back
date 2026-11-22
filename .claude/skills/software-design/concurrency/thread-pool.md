# Thread Pool

## Intent

Reuse a bounded set of worker threads to execute submitted tasks, instead of creating and destroying a thread per unit of work.

## Problem

Creating an OS thread is expensive (stack allocation, kernel scheduling overhead) and threads are a finite, memory-bound resource — a few thousand of them can exhaust a process. Code that spawns `new Thread(...)` per incoming request or task has no cap: under load it creates unbounded threads, thrashes the scheduler with context switches, and can crash the process with `OutOfMemoryError: unable to create new native thread` long before CPU is the bottleneck. A thread pool bounds concurrency to a sane number, amortizes thread creation cost across many tasks, and gives you one place to control queuing, rejection, and shutdown behavior.

## When to use

- Handling many short-lived, independent units of work (HTTP requests, background jobs, parallel sub-computations) where per-task thread creation would dominate cost.
- You need an explicit concurrency ceiling — e.g. no more than N simultaneous connections to a downstream service with limited capacity.
- You want centralized lifecycle management: graceful shutdown, task rejection policy, and monitoring (queue depth, active count) in one place instead of scattered thread management.
- CPU-bound parallel work that should be capped near the core count to avoid oversubscription.

## When NOT to use

- I/O-bound work at very high concurrency (tens of thousands of concurrent blocking calls) — traditional pooled platform threads each pin an OS thread for the duration of a blocked call; this is the exact case virtual threads (Java 21+, `Executors.newVirtualThreadPerTaskExecutor()`) or async/reactive I/O were built for. Forcing a fixed-size pool here just serializes work behind a small thread count for no CPU-bound reason.
- The pool size is picked without any load testing or capacity reasoning — `Executors.newFixedThreadPool(10)` because 10 "felt reasonable" is a magic number; the right size depends on whether the workload is CPU-bound (~core count) or I/O-bound (much higher, bounded by downstream capacity, not CPU).
- A single, rare, long-running background task (a nightly batch job) doesn't need a pool at all — a single dedicated thread or a scheduled executor is simpler and avoids the bookkeeping of a pool sized for concurrency you'll never use.
- The pool is unbounded (`Executors.newCachedThreadPool()` under sustained load, or `newFixedThreadPool` fed by an unbounded queue) — this reintroduces the exact resource-exhaustion problem the pool was meant to prevent, just with an extra layer of indirection making it harder to see.

## Structure

A fixed set of worker threads pulls tasks from an internal queue; callers submit tasks without knowing which thread runs them.

```java
ExecutorService pool = new ThreadPoolExecutor(
    8, 8,                                   // core = max: fixed size, sized for CPU-bound work
    0L, TimeUnit.MILLISECONDS,
    new ArrayBlockingQueue<>(500),          // bounded queue — real backpressure
    new ThreadPoolExecutor.CallerRunsPolicy() // explicit rejection policy, not silent drop
);

Future<Result> future = pool.submit(() -> computeResult(input));
Result result = future.get(5, TimeUnit.SECONDS);

pool.shutdown();
pool.awaitTermination(30, TimeUnit.SECONDS);
```

## Consequences

- **Benefits**: bounded, predictable resource usage; amortized thread-creation cost; a single tunable knob (pool size) and a single place to observe saturation (queue depth, rejected task count); graceful shutdown is centralized instead of ad hoc per thread.
- **Costs**: an undersized pool serializes work that could have run in parallel, hurting latency; an oversized pool causes context-switch thrashing and memory pressure without throughput gain; the right size is workload-specific and can drift as the workload changes, so it needs load testing and periodic revisiting, not a one-time guess; task queuing adds latency variance that's hard to diagnose without visibility into queue depth; a pool exhausted by tasks blocked on a slow downstream can silently stall unrelated work sharing the same pool (thread starvation) — this is one of the harder concurrency bugs to reproduce because it depends on downstream timing, not on the code itself.
- Rejection policy is a real decision: `AbortPolicy` throws (caller must handle), `CallerRunsPolicy` provides natural backpressure by borrowing the caller's thread, `DiscardPolicy` silently drops tasks (rarely what you want — it hides failure).

## Related patterns

- [Producer-Consumer](producer-consumer.md) — a thread pool's internal task queue is exactly this pattern; pool workers are the consumers.
- [Futures/Promises](futures-promises.md) — `submit()` returns a `Future`; pools are the usual execution engine behind async composition.
- [Circuit Breaker](../distributed/circuit-breaker.md) — often paired with a pool dedicated to a specific downstream, so that downstream's slowness can't starve unrelated work.
- [Bulkhead](../distributed/bulkhead.md) — using separate pools per dependency is the Bulkhead pattern applied via thread pools.

## Smells that suggest this pattern

- Manual `new Thread(task).start()` scattered through request-handling code, with no cap on how many can exist concurrently.
- A service that becomes unresponsive or OOMs under load with thread-dump evidence of thousands of threads.
- No visibility into "how many tasks are running / queued right now" — a sign concurrency isn't centralized anywhere.

## Common misuse

- Picking a pool size as a magic number copied from a tutorial or a previous project, with no load test behind it and no comment explaining the reasoning.
- One shared pool for everything in the application — a slow downstream call for feature A exhausts the pool and stalls unrelated feature B, because there was never a bulkhead between them.
- Using a fixed-size pool with pooled platform threads for massively concurrent blocking I/O instead of async I/O or virtual threads, then "fixing" saturation by cranking the pool size into the thousands — which just trades one resource-exhaustion mode (too many bare threads) for another (too many pooled ones) without solving the underlying architecture mismatch.
- Swallowing or logging-and-forgetting exceptions from `Future.get()` (or never calling it) so a task's failure disappears silently instead of surfacing.
