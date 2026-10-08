# Timeout

## Intent

Bound the maximum time a caller will wait for an operation to complete, so a hung or slow dependency can't hold resources or block callers indefinitely.

## Problem

Network calls, by default, have no inherent time bound — a dependency that hangs (deadlock, GC pause, network partition where packets are neither delivered nor rejected) can leave a caller waiting forever, or for as long as the OS-level socket default allows (which is often much longer than acceptable, sometimes effectively unbounded). Every thread/connection stuck waiting on a hung call is a resource that isn't available for other work; enough of them and the caller itself becomes unavailable, independent of whether the caller's own logic has any bug.

## When to use

- Any call across a process/network boundary: HTTP calls, RPC, database queries, message broker operations, distributed lock acquisition.
- Any blocking operation with a resource cost while waiting (a thread, a connection, a lock) — timeouts convert "wait forever" into "fail after N and free the resource."
- Any call that another resilience pattern (Retry, Circuit Breaker, Bulkhead) needs to reason about as "failed" — those patterns can't count or react to a call that never returns.

## When NOT to use

- Failure scenario: a timeout is set shorter than a dependency's legitimate p99/p999 latency — under normal (not even degraded) load, a meaningful fraction of correct, healthy calls get killed and reported as failures, which then trips retries and circuit breakers based on a self-inflicted signal, not a real problem. Set timeouts from measured latency distributions, not guesses.
- A local, in-process, non-blocking call — there's no external dependency whose hang needs bounding; adding a timeout wrapper here is pure overhead with nothing to protect against.
- Failure scenario: a timeout fires and the code aborts the *caller's* wait, but the underlying operation isn't actually cancelled server-side — a payment API call times out client-side at 5s, the client considers it failed and retries, but the original request completes successfully at 6s server-side, resulting in two charges. Timeout alone doesn't guarantee the operation stopped; it guarantees the caller stopped waiting for it. Pair with idempotency for anything with a side effect.
- Setting one blanket timeout across every dependency and every operation type in a service regardless of their actual latency characteristics (a 200ms cache read and a 30s batch export report have nothing in common) — either the fast calls get an unnecessarily generous timeout that delays failure detection, or the slow calls get killed prematurely.

## Structure

Every network-crossing call should specify a connect timeout (bounding how long to wait to establish a connection) and a read/response timeout (bounding how long to wait for a response after the request is sent), sized from measured latency percentiles.

```java
WebClient client = WebClient.builder()
    .clientConnector(new ReactorClientHttpConnector(
        HttpClient.create()
            .responseTimeout(Duration.ofMillis(800)) // sized from p99 + margin, not guessed
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 300)))
    .build();

Mono<InventoryResult> result = client.get()
    .uri("/inventory/{sku}", sku)
    .retrieve()
    .bodyToMono(InventoryResult.class)
    .timeout(Duration.ofSeconds(1)) // belt-and-suspenders app-level timeout
    .onErrorResume(TimeoutException.class, ex -> Mono.just(InventoryResult.unknown(sku)));
```

The app-level `.timeout(...)` guards against the HTTP client's own timeout not firing correctly (e.g. slow body streaming past the response timeout) and gives a place to attach the fallback.

## Consequences

Benefits: bounds worst-case latency and resource hold time for any given call; makes a hung dependency behave like a fast failure instead of an indefinite stall, which every other resilience pattern (Retry, Circuit Breaker, Bulkhead) depends on to function correctly; gives callers a predictable SLA to design around.

Costs: a timeout that's too aggressive turns healthy-but-slightly-slow responses into false failures, which can cascade into unnecessary retries and circuit trips; a timeout that's too generous doesn't actually bound resource usage meaningfully and delays failure detection; timing out client-side does not guarantee the server-side operation stopped or rolled back — a "failed" call may have actually succeeded, which matters a lot for anything with a side effect; needs per-dependency tuning based on real latency data, not a single global default, and that data needs to be revisited as dependency performance changes over time.

## Related patterns

- [Retry](retry.md) — each retry attempt needs its own timeout, or a single hung attempt exhausts the whole retry budget. "Retry + Idempotency + Timeout" is a standard trio.
- [Circuit Breaker](circuit-breaker.md) — needs bounded call latency to reliably classify calls as failures within its sliding window; an unbounded hang looks indistinguishable from "still in progress" and won't count toward tripping the breaker. "Circuit Breaker + Bulkhead + Timeout" is a standard trio.
- [Bulkhead](bulkhead.md) — a thread-pool bulkhead without a timeout on the underlying call just delays exhaustion instead of preventing it.
- [Idempotency](idempotency.md) — required for safely reacting to a timeout with a retry, since the original call's actual outcome is unknown, not necessarily "failed."

## Smells that suggest this pattern

- Threads/connections observed stuck in a "waiting" state for minutes during an incident, with no upper bound on how long they'd have waited.
- A dependency outage causing the caller's entire thread pool to fill up and the caller to become unresponsive, rather than degrading and returning errors quickly.
- Incident postmortems that mention "the call just never came back" as a root cause.

## Common misuse

- No timeout set at all, relying on OS/library defaults that are often far longer (minutes, or unbounded) than the caller can actually tolerate.
- One global timeout value applied uniformly across dependencies and operations with wildly different latency profiles, picked without looking at actual p99/p999 data for each.
- Treating a client-side timeout as proof the operation didn't happen, and retrying a non-idempotent call as if the timeout guaranteed a clean no-op on the other side.
- Timeout set so tight that it fires under normal load variance, generating a steady background rate of false failures that erode trust in the actual error signal.
