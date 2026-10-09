# Circuit Breaker

## Intent

Stop calling a dependency that is failing or too slow, fail fast instead, and periodically test whether it has recovered — so one broken dependency doesn't exhaust callers' resources or cascade into a system-wide outage.

## Problem

When a downstream dependency starts failing or degrading (timeouts, errors, high latency), callers that keep calling it anyway tie up threads/connections waiting on a call that's unlikely to succeed. Under load this exhausts the caller's own resource pool (threads, connections), which then makes the caller itself slow or unavailable to *its* callers — a single failing dependency cascades upward through the call graph and takes down services that don't even directly depend on the original failure.

## When to use

- Calling a remote/external dependency (another service, a third-party API, a database) that can fail or degrade independently of the caller, and repeated failed calls have a real cost (thread/connection exhaustion, latency budget burn).
- The dependency's failure mode is the kind that clusters in time (an outage, overload, network partition) rather than isolated independent errors — where "stop trying for a bit" is a meaningful response.
- There's a real difference in cost between "call and wait for a slow failure" and "fail immediately" — e.g. a call with a multi-second timeout that, under an outage, would be hit thousands of times per second.
- You can define a meaningful fallback or degraded behavior when the circuit is open (cached value, default, queued-for-later, user-facing degraded state).

## When NOT to use

- Failure scenario: a circuit breaker is added in front of a call with no meaningful fallback — the code still ends up throwing/erroring when the circuit is open, just faster and with less information than the original exception. All that changed is *how* the caller fails; the caller is still broken, and now there's a second failure mode (circuit state) to reason about on top of the original one.
- The dependency's failures are independent, low-volume, and not correlated with real dependency health (e.g. an occasional validation error on a subset of requests) — the breaker will never trip meaningfully, or worse, will trip on noise unrelated to actual downstream health.
- Failure scenario: thresholds are copied from another service's config without validating this call's actual traffic volume — a breaker tuned for 10,000 req/min trips (or never trips) nonsensically on a call that gets 20 req/min, because the sliding window never fills with enough samples to reach the trip threshold, or trips on a single blip because the window is tiny.
- In-process/local calls with no network boundary — there's no partial-failure mode a circuit breaker protects against; the call either works or throws deterministically.
- Failure scenario: a breaker trips on a short blip (one deploy-triggered 20-second latency spike) and then stays open for its full reset timeout, rejecting healthy traffic for minutes after the dependency already recovered, because the half-open probe interval was tuned too conservatively for how often the team actually expected transient blips.

## Structure

Three states: **Closed** (calls pass through, failures counted) → trips to **Open** (calls fail fast / go to fallback, no calls reach the dependency) → after a timeout, **Half-Open** (a limited number of trial calls are allowed through) → closes again on success or reopens on failure.

```java
CircuitBreakerConfig config = CircuitBreakerConfig.custom()
    .failureRateThreshold(50)                       // trip at 50% failures
    .slidingWindowSize(20)                           // over the last 20 calls
    .waitDurationInOpenState(Duration.ofSeconds(30))  // stay open 30s
    .permittedNumberOfCallsInHalfOpenState(5)         // probe with 5 calls
    .build();

CircuitBreaker breaker = CircuitBreaker.of("paymentsGateway", config);

Supplier<PaymentResult> decorated = CircuitBreaker
    .decorateSupplier(breaker, () -> paymentsClient.charge(request));

Try<PaymentResult> result = Try.ofSupplier(decorated)
    .recover(CallNotPermittedException.class, ex -> PaymentResult.deferred(request));
```

`CallNotPermittedException` is thrown immediately when the circuit is open — no call reaches `paymentsClient`. `PaymentResult.deferred(...)` is the meaningful fallback (e.g. queue for async retry); without one, this decoration only changes the exception type the caller has to handle.

## Consequences

Benefits: prevents cascading failure by shedding load to a known-bad dependency; fails fast, freeing threads/connections for other work; gives the failing dependency room to recover instead of being hammered by retries.

Costs: added latency and complexity in the happy path (every call goes through the breaker's bookkeeping); false positives — a legitimate blip trips the breaker and rejects healthy traffic until the next half-open probe; false negatives — a breaker tuned too loosely lets a degraded dependency keep getting hit; requires real fallback logic, which is often the hardest part to design well; silent failure mode if unmonitored — a breaker stuck open looks like "no errors" in some naive dashboards while actually serving 100% fallback/degraded responses. Circuit state (open/closed/half-open transitions, rejection counts) must be exported as metrics and alerted on, or an open circuit goes unnoticed until someone asks why a feature has been degraded for an hour.

## Related patterns

- [Timeout](timeout.md) — a circuit breaker without a bounded timeout on the underlying call can't reliably count "failures" (a hang isn't a failure until it times out); the two are almost always paired.
- [Bulkhead](bulkhead.md) — commonly combined: Bulkhead limits concurrent calls to a dependency, Circuit Breaker stops calling it altogether once it's clearly unhealthy. "Circuit Breaker + Bulkhead + Timeout" is a standard trio around any external call.
- [Retry](retry.md) — retries should generally stop, not continue, once a circuit is open; retrying against an open circuit just burns the fallback path repeatedly.
- [Rate Limiter](rate-limiter.md) — shapes outbound call volume proactively; Circuit Breaker reacts to observed failures. Different triggers, complementary effect.

## Smells that suggest this pattern

- One slow/failing downstream dependency taking down an otherwise healthy service because callers pile up waiting on it (thread pool exhaustion, connection pool exhaustion).
- Dashboards showing latency/error spikes on services that don't directly call the failing dependency, several hops away in the call graph.
- Manual "someone SSHs in and disables the integration" runbooks — that's a circuit breaker being operated by hand.

## Common misuse

- Wrapping a call in a circuit breaker as a checkbox ("all external calls must have a circuit breaker") without defining what happens on open — the caller still errors, just via a different exception type, at implementation cost with no resilience gain.
- Copy-pasting threshold/window config from another service without checking this call's actual request volume and latency profile, producing a breaker that never trips or trips on noise.
- Treating an open circuit as an acceptable steady state rather than an incident — no alert fires, and the fallback silently serves degraded results for days.
- Putting the circuit breaker around a fast, reliable in-process call "for consistency" with other integration code — pure overhead, nothing to protect against.
