# Bulkhead

## Intent

Partition resources (threads, connections, memory) per dependency or workload so that one dependency's saturation can't exhaust resources needed by unrelated dependencies or workloads.

## Problem

When all calls to all dependencies share one resource pool (one thread pool, one connection pool), a single slow or overloaded dependency can consume the entire pool waiting on it — every thread is blocked on calls to the failing dependency, leaving none available to serve calls to healthy dependencies or to handle unrelated requests. The failure of one dependency becomes an outage of the whole service, even for functionality that never touches the failing dependency. Named after ship bulkheads: a hull breach in one compartment shouldn't sink the whole ship.

## When to use

- A service calls multiple independent downstream dependencies with different reliability/latency profiles, and they currently share one thread/connection pool.
- One dependency is known to be less reliable or slower than others (a third-party API vs. an internal, well-SLA'd service) and you want to cap its blast radius.
- You need to guarantee a minimum level of service for critical paths even when a non-critical dependency is degraded (e.g. checkout must keep working even if the recommendations service is down).
- High-value and low-value/best-effort workloads currently compete for the same resource pool, and a burst in one starves the other.

## When NOT to use

- Failure scenario: bulkheads are added around every single downstream call "for safety," each with its own small thread pool — under normal load this just fragments capacity into many small, mostly-idle pools, so the service handles *less* total concurrent traffic than one shared pool would, while adding a per-dependency pool to size and monitor.
- The service has exactly one real external dependency, or all dependencies fail together anyway (e.g. they're all behind the same network path/region) — partitioning resources doesn't isolate anything if the dependencies aren't actually independent in their failure modes.
- Failure scenario: a bulkhead's pool size is picked arbitrarily (e.g. "10, seems reasonable") without load testing — it's too small for real peak traffic to a healthy dependency, so the bulkhead itself becomes the bottleneck and starts rejecting legitimate calls well before the dependency is actually struggling.
- Low-traffic internal services with a single caller and no risk of one workload starving another — the isolation bulkhead provides has no failure mode to prevent here.

## Structure

Two common implementations: a **semaphore bulkhead** (limits concurrent calls via a permit count, cheap, no extra threads) or a **thread-pool bulkhead** (dedicated thread pool per dependency, allows true timeout-on-queue-full but costs more resources).

```java
BulkheadConfig config = BulkheadConfig.custom()
    .maxConcurrentCalls(15)       // at most 15 concurrent calls to this dependency
    .maxWaitDuration(Duration.ofMillis(100)) // queue briefly, then reject
    .build();

Bulkhead bulkhead = Bulkhead.of("recommendationsService", config);

Supplier<List<Item>> decorated = Bulkhead
    .decorateSupplier(bulkhead, () -> recommendationsClient.fetch(userId));

Try.ofSupplier(decorated)
    .recover(BulkheadFullException.class, ex -> Collections.emptyList()); // degrade, don't block checkout
```

The checkout thread pool is never touched by `recommendationsClient` calls beyond the 15-permit cap — even if `recommendationsClient` hangs entirely, at most 15 concurrent callers are affected, and the fallback (empty list) keeps checkout itself unaffected.

## Consequences

Benefits: contains one dependency's failure/slowness to its own resource allocation, protecting unrelated request paths; makes capacity planning explicit per dependency instead of implicit and shared; combined with a fallback, degrades gracefully instead of failing completely.

Costs: fragments total capacity — resources reserved for a partition sit idle even when other partitions are under load and could use them; each partition needs its own sizing/tuning based on real traffic, which is ongoing operational work, not a one-time setting; adds a rejection path (`BulkheadFullException` or equivalent) that callers must handle meaningfully, same as Circuit Breaker; thread-pool bulkheads specifically add real memory/thread overhead per partition. Pool utilization and rejection counts need to be monitored per partition — a bulkhead silently rejecting calls under normal-looking aggregate load is invisible without per-dependency metrics.

## Related patterns

- [Circuit Breaker](circuit-breaker.md) — commonly combined: Bulkhead caps concurrent exposure to a dependency, Circuit Breaker stops calling it once it's clearly unhealthy. "Circuit Breaker + Bulkhead + Timeout" is a standard trio.
- [Timeout](timeout.md) — without a timeout on the call itself, a thread-pool bulkhead just delays resource exhaustion rather than preventing it — permits get tied up indefinitely on hung calls.
- [Rate Limiter](rate-limiter.md) — Rate Limiter caps request *rate* (calls per time window), Bulkhead caps concurrent *in-flight* calls; they answer different questions and are often both present.

## Smells that suggest this pattern

- One slow downstream dependency taking down an otherwise-healthy service because every request-handling thread ends up blocked waiting on it.
- Two features with very different reliability requirements (critical checkout path vs. best-effort recommendations) sharing one thread/connection pool, so a recommendations outage degrades checkout too.
- Load tests showing that saturating one third-party integration drops throughput for endpoints that never call that integration.

## Common misuse

- A separate bulkhead around every single call site regardless of actual risk profile, fragmenting a service's total capacity into many small pools that are individually under-provisioned for real peak load.
- Pool sizes picked without load testing or capacity data, so the bulkhead becomes the new bottleneck instead of protecting against the dependency's actual failure mode.
- Bulkhead without a fallback for the rejected/queue-full case — callers just get a different exception (`BulkheadFullException`) instead of the resource-exhaustion symptom the bulkhead was meant to prevent, with no actual degraded-but-working behavior.
