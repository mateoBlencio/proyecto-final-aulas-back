# Retry

## Intent

Automatically re-attempt an operation that failed due to a transient condition, instead of surfacing the failure to the caller immediately.

## Problem

Distributed calls fail for reasons that are often transient — a dropped packet, a momentary overload, a leader election in progress, a brief network partition. Treating every such failure as permanent (surface it to the user, abort the workflow) is unnecessarily brittle when the same call would likely succeed a moment later. But retrying blindly — with no backoff, no bound, no consideration of what the failed operation actually did — turns a small transient blip into duplicate side effects or a self-inflicted overload.

## When to use

- The failure is plausibly transient: network blips, connection resets, HTTP 503/429, optimistic-lock/version-conflict errors, brief leader-unavailability in a cluster.
- The operation is safe to repeat — either it's naturally idempotent (a GET, a `PUT` with a full resource state) or it's been made idempotent via a dedup mechanism (see Idempotency).
- You can bound the retry (max attempts or max elapsed time) and the caller can tolerate the added latency of retrying.
- The failure signal distinguishes retryable from non-retryable cases (e.g. retry on 503/timeout, do not retry on 400/422 validation errors).

## When NOT to use

- Failure scenario: retrying a non-idempotent operation (e.g. `POST /charge-card`, `POST /send-email`) without any deduplication turns one transient failure into a duplicate charge or duplicate email — the retry "fixed" the transient error but created a worse, silent data-integrity bug. This is the single most common Retry misuse; never add Retry without checking idempotency first, and see [Idempotency](idempotency.md).
- The failure is deterministic/permanent (validation error, 404, authorization failure, malformed request) — retrying it wastes time and resources for a call that will never succeed; the fix is to fail fast and surface the error, not retry it.
- Failure scenario: a retry storm — many clients hit a struggling dependency, all fail around the same time, all retry with the same fixed delay and no jitter, and the synchronized retry wave arrives at the dependency at the same moment, amplifying the very overload that caused the failures and turning a partial degradation into a full outage.
- Retrying inside a user-facing synchronous request path without a sane overall timeout budget — three retries at 30s each turns a failing 30-second call into a 90-second hang the user experiences as the request "never responding," which is worse than a fast, clear error.
- The call already sits behind an open circuit breaker — retrying against a circuit that's open just burns attempts against a fallback/rejection path; the retry policy needs to respect breaker state, not fight it.

## Structure

Configure: max attempts (or max elapsed duration), backoff strategy (fixed, exponential, exponential + jitter), and which exceptions/status codes are retryable.

```java
RetryConfig config = RetryConfig.custom()
    .maxAttempts(4)
    .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
        Duration.ofMillis(200), 2.0)) // base 200ms, doubling, with jitter
    .retryOnException(ex -> ex instanceof TransientApiException) // not on 4xx
    .build();

Retry retry = Retry.of("inventoryService", config);

Supplier<InventoryResult> decorated = Retry
    .decorateSupplier(retry, () -> inventoryClient.reserve(idempotencyKey, items));
```

`idempotencyKey` is passed through so a retried call after a network failure (where the first attempt may have actually succeeded server-side) doesn't double-reserve inventory — the retry logic and the idempotency mechanism have to be designed together, not bolted on separately.

## Consequences

Benefits: absorbs transient failures without surfacing them to the caller or the user; smooths over brief dependency blips that would otherwise fail a whole workflow.

Costs: added latency on the failure path (each retry adds its backoff delay, compounding); can amplify load on an already-struggling dependency if backoff/jitter/max-attempts aren't tuned correctly (retry storms); makes root-cause diagnosis harder if retries aren't logged/traced — an operation that "succeeded" after 3 retries hides a real, worsening problem from monitoring unless retry counts are tracked as a metric; requires the underlying operation to be idempotent, which is a design commitment, not a Retry-library setting.

## Related patterns

- [Idempotency](idempotency.md) — a hard prerequisite for retrying any non-read operation; Retry without Idempotency is the most common cause of duplicate side effects in distributed systems.
- [Timeout](timeout.md) — each retry attempt needs its own bounded timeout, or a single hung attempt blocks the whole retry budget. "Retry + Idempotency + Timeout" is a standard trio around any call to an unreliable dependency.
- [Circuit Breaker](circuit-breaker.md) — retry policy should stop attempting once a circuit is open; the two must coordinate, not operate independently.
- [Rate Limiter](rate-limiter.md) — on the receiving side, protects a service from being overwhelmed by many callers' retries; on the calling side, exponential backoff with jitter is the client-side analog.

## Smells that suggest this pattern

- User-facing errors for failures that, on a support ticket or log review, turn out to have been single transient blips (one dropped connection, one 503) that would have succeeded on a second attempt.
- A workflow that fully aborts and requires manual re-triggering after a downstream dependency's momentary hiccup.

## Common misuse

- Retrying a POST/charge/send-notification call with no idempotency key, no dedup check, and no compensating check for "did the first attempt actually succeed server-side before it appeared to fail" — the classic duplicate-charge bug.
- Fixed-delay retry with no jitter across many client instances, causing synchronized retry storms against a recovering dependency (thundering herd).
- Unbounded or very high max-attempt counts with no overall timeout budget, so a persistently-failing call retries for minutes inside a request a user is actively waiting on.
- Retrying on all exceptions indiscriminately, including validation/auth errors that will never succeed no matter how many times they're retried.
