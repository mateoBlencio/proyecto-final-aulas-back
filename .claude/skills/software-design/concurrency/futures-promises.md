# Futures/Promises

## Intent

Represent the eventual result of an asynchronous computation as a first-class value that can be composed, transformed, and combined without blocking the calling thread to wait for it.

## Problem

Callback-based async code (pass a function to be invoked on completion) composes badly: chaining several dependent async steps nests callback inside callback, error handling has to be threaded through every level by hand, and running two async operations concurrently and combining their results requires manual coordination state. A Future/Promise makes "a value that will exist later" a value you can hold, pass around, and chain (`.then()`, `.thenCompose()`, `.thenCombine()`) using the same composition vocabulary as synchronous code, with errors propagating through the chain instead of requiring per-callback handling.

## When to use

- Composing multiple independent async operations — call three APIs concurrently, combine their results once all three finish (`CompletableFuture.allOf`, `thenCombine`).
- Chaining dependent async steps where each depends on the previous result (fetch user, then fetch their orders, then fetch order details) without nesting callbacks.
- You need a single object representing "not done yet" that can be checked, waited on with a timeout, or cancelled — as a return type from an async API boundary.
- Bridging a callback-based library into composable code, by wrapping the callback registration in a `CompletableFuture`.

## When NOT to use

- The chain has grown five or six `.thenCompose()`/`.thenApply()` calls deep with inline lambdas and non-obvious error-handling branches — at that point it's exactly as unreadable as the callback hell it was meant to replace, just with different syntax. Extract named methods for each stage, or reconsider whether the flow is better expressed with a coroutine/virtual-thread style sequential block (`Thread.startVirtualThread` blocking calls read top-to-bottom and are just as concurrent).
- A single async call with no composition need at all — returning a `CompletableFuture<T>` and immediately calling `.get()` in the caller adds an abstraction with none of its benefit; just make (or call) a blocking method.
- Error handling is an afterthought — every `.thenApply` in a chain needs to consider whether the prior stage failed; skipping `.exceptionally()`/`.handle()` means exceptions silently propagate to a `.get()` call far from where they occurred, or worse, get swallowed if the future's result is never awaited at all.
- With virtual threads (Java 21+) or coroutines/async-await available, using raw future chaining purely for concurrency (not genuine composition of independent results) usually produces less readable code than writing straight-line blocking-looking code on a cheap thread.

## Structure

A `CompletableFuture` chains transformations and combinations; the calling thread doesn't block unless it explicitly asks to (`.get()`/`.join()`).

```java
CompletableFuture<User> userFuture = fetchUserAsync(userId);

CompletableFuture<OrderSummary> summaryFuture = userFuture
    .thenCompose(user -> fetchOrdersAsync(user.id()))       // dependent step
    .thenApply(orders -> summarize(orders))                  // pure transform
    .exceptionally(ex -> OrderSummary.empty());               // error path, not an afterthought

CompletableFuture<Void> combined = CompletableFuture.allOf(
    fetchInventoryAsync(), fetchPricingAsync()               // independent, run concurrently
);

OrderSummary result = summaryFuture.get(5, TimeUnit.SECONDS); // block only at the boundary
```

## Consequences

- **Benefits**: composition reads close to synchronous code without blocking a thread per pending operation; concurrent independent operations combine cleanly (`allOf`/`thenCombine`) instead of hand-rolled counters and latches; errors propagate through the chain via a single mechanism instead of per-callback handling.
- **Costs**: stack traces from exceptions inside async chains often show the executor's internals rather than the logical call site, making root-causing a failure slower than for synchronous code — a real and specific debuggability cost, not a hypothetical one; forgetting to handle a future's failure (never calling `.get()`, `.join()`, or attaching `.exceptionally()`) means the exception disappears silently; overly long chains with inline lambdas trade one unreadable style (nested callbacks) for another (a wall of `.thenApply` with no named stages); each stage may run on a different thread from a shared pool, so thread-local state (MDC/logging context, transaction context) doesn't automatically carry across `.thenApply` boundaries unless explicitly propagated — a common source of "logs lost context after this async hop" bugs.
- Cancellation semantics are easy to get wrong: cancelling a `Future` doesn't necessarily stop the underlying work if it's already running — it just marks the future as cancelled, and code must check for that explicitly.

## Related patterns

- [Thread Pool](thread-pool.md) — the usual execution engine backing `.thenApplyAsync()`/`.supplyAsync()` calls.
- [Actor](actor.md) — the `ask` pattern typically returns a Future representing an actor's eventual reply.
- [Circuit Breaker](../distributed/circuit-breaker.md) — often composed with futures via `.exceptionally()`/`.handle()` to fall back when an async call fails.
- [Producer-Consumer](producer-consumer.md) — an alternative composition style (a queue) for cases better modeled as a stream of items rather than a single eventual value.

## Smells that suggest this pattern

- Deeply nested callbacks (`doA(result -> doB(result2 -> doC(result3 -> ...)))`) — the textbook trigger for converting to Futures/Promises.
- Manual `CountDownLatch`/`wait`-`notify` coordination to know when several independent async operations have all completed.
- Hand-rolled polling (`while (!done) sleep(50)`) to check whether an async operation finished, instead of an event-driven completion mechanism.

## Common misuse

- Chains so long and so densely lambda'd that nobody, including the author a month later, can trace the control flow or the error path without stepping through a debugger — the exact failure mode this pattern was supposed to fix relative to callbacks.
- Calling `.get()` immediately after creating a future (`future.get()` on the next line, always) — this is synchronous code wearing an asynchronous costume, paying the API's complexity cost for zero concurrency benefit.
- Swallowing exceptions by never calling a terminal method (`.get()`, `.join()`, `.exceptionally()`) on a future at all — the failure vanishes, and the code "seems to work" until whatever depended on the result quietly never happens.
- Blocking (`.get()`) inside a `.thenApply()` stage running on a shared thread pool — this starves the pool the same way a blocked worker starves a thread pool in general, except it's harder to spot because the blocking call is hidden inside what looks like an async chain.
