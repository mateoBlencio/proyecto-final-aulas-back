# Rate Limiter

## Intent

Cap the rate at which requests are accepted (inbound) or issued (outbound) over time, so a burst of traffic can't overwhelm a system's real capacity.

## Problem

A service (or a downstream dependency) has finite capacity — CPU, database connections, downstream quota — but nothing about HTTP or a message queue inherently prevents callers from sending requests faster than that capacity can absorb. Without a limiter, a traffic spike (legitimate burst, retry storm, misbehaving client, abusive/scraping client) degrades or crashes the service for everyone, including well-behaved callers, because there's no mechanism separating "requests received" from "requests the system can actually handle right now."

## When to use

- Exposing a public or partner-facing API where callers are not fully trusted to self-limit, and unbounded request volume could degrade service for other tenants.
- Protecting a downstream dependency with a known hard quota (a paid third-party API with a contracted requests/second cap, a database with a known connection ceiling).
- Enforcing fairness across tenants in a multi-tenant system — capping one tenant's request rate so it can't starve others sharing the same infrastructure.
- Client-side: capping outbound call rate to a dependency proactively, instead of reactively discovering its limit via 429s and Circuit Breaker trips.

## When NOT to use

- Failure scenario: limits are copy-pasted from another service's config ("it worked there") without measuring this service's actual capacity — a limit set too low throttles legitimate traffic well below real capacity (leaving performance on the table and generating support tickets), or set too high provides no real protection because it was never validated against a load test.
- Internal, trusted, low-cardinality call paths (e.g. two services within the same team's deployment, both scaled together) where the real problem, if any, is capacity planning, not abuse/fairness — a rate limiter here adds a rejection path and a tuning burden for a scenario that isn't actually about bounding untrusted demand.
- Failure scenario: a rate limiter is applied per-instance in a horizontally scaled service without a shared/distributed counter — each instance independently allows N req/s, so the *effective* system-wide limit is N × instance-count and silently grows/shrinks every time the deployment autoscales, defeating the stated limit.
- Using a rate limiter as a substitute for actually fixing a capacity problem — e.g. throttling requests to a database that's underprovisioned, instead of scaling or optimizing it; the limiter hides the symptom (errors) while the underlying capacity problem persists and gets discovered later at a worse time.

## Structure

Common algorithms: **token bucket** (allows bursts up to bucket size, refills at a steady rate — usually the best default), **sliding window** (smooths rate over a rolling window, avoids edge-of-window bursts that fixed windows allow), **leaky bucket** (enforces a strictly steady outflow rate, no bursts).

```java
RateLimiterConfig config = RateLimiterConfig.custom()
    .limitForPeriod(50)                       // 50 requests
    .limitRefreshPeriod(Duration.ofSeconds(1)) // per second
    .timeoutDuration(Duration.ofMillis(25))    // max wait for a permit before rejecting
    .build();

RateLimiter limiter = RateLimiter.of("partnerApiInbound", config);

Supplier<Response> decorated = RateLimiter
    .decorateSupplier(limiter, () -> handler.process(request));

Try.ofSupplier(decorated)
    .recover(RequestNotPermitted.class, ex -> Response.tooManyRequests(retryAfterHeader(limiter)));
```

For multi-instance deployments, the counter must live somewhere shared (Redis with `INCR`+`EXPIRE`, a gateway-level limiter) — an in-process limiter like the one above only bounds per-instance rate, not system-wide rate.

## Consequences

Benefits: protects capacity-constrained resources from being overwhelmed by legitimate bursts, retries, or abusive clients; makes capacity limits explicit and enforced rather than implicit and discovered via an outage; enables fair sharing across tenants/callers.

Costs: rejects requests that may be entirely legitimate if the limit is mis-tuned, producing false-positive throttling; adds latency/complexity to every request path (permit check); a shared/distributed limiter (needed for correctness across instances) adds a dependency (Redis or equivalent) and a new failure mode — if the limiter's own store is unavailable, the policy has to decide fail-open (risk overload) or fail-closed (reject everything) explicitly; needs per-scope configuration (per-tenant, per-endpoint, per-API-key) that must be revisited as real traffic and capacity change, or it silently drifts out of sync with reality.

## Related patterns

- [Bulkhead](bulkhead.md) — Rate Limiter caps request *rate* over time, Bulkhead caps concurrent *in-flight* requests; a system under bursty load often needs both.
- [Circuit Breaker](circuit-breaker.md) — Rate Limiter proactively shapes load before it becomes a problem; Circuit Breaker reacts after failures are observed. Client-side rate limiting on outbound calls is often a better first line of defense than waiting for a breaker to trip on 429s.
- [Retry](retry.md) — retries against a rate-limited endpoint must respect `Retry-After`/backoff, or the retry logic itself becomes the source of the overload the limiter exists to prevent.

## Smells that suggest this pattern

- An outage traced back to a single client (internal or external) sending an unbounded burst of requests, with no mechanism that would have capped it before it hit the database/downstream API.
- A third-party API returning 429s in production because outbound call volume was never bounded client-side to match the contracted quota.
- One noisy tenant in a multi-tenant system degrading response times for all other tenants during their peak usage.

## Common misuse

- Limits set by guesswork or copied from an unrelated service, never validated against this service's actual measured capacity via load testing.
- Per-instance limiting in a horizontally scaled deployment with no shared counter, so the real system-wide limit silently scales with instance count instead of staying fixed at the intended value.
- No differentiated response for throttled requests — clients get a bare rejection with no `Retry-After` guidance, so well-behaved clients can't back off intelligently and end up retrying immediately, compounding the load the limiter was trying to shed.
- Rate limiting used to paper over an underprovisioned dependency instead of fixing the actual capacity problem, deferring the real fix while degrading the client experience in the meantime.
