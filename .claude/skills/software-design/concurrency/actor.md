# Actor

## Intent

Encapsulate mutable state inside isolated units (actors) that communicate only via asynchronous messages, eliminating shared-memory data races by construction instead of by locking discipline.

## Problem

Locks correctly guard shared mutable state, but only if every access path remembers to take the lock — one missed `synchronized` and you have a data race that may not show up for months. As the number of shared fields and threads touching them grows, reasoning about which locks protect which state, and in what order (to avoid deadlock), becomes exponentially harder. The Actor model sidesteps this by removing shared mutable state entirely: each actor owns its state exclusively, processes one message at a time from its own inbox, and the only way to affect another actor's state is to send it a message and let it decide what to do.

## When to use

- State that's mutated by many concurrent sources but conceptually "belongs" to one entity (a user's session, a game entity, a device connection, an order's state machine) — model the entity as an actor and route all mutation through its mailbox.
- You're building a system with many independent, stateful, long-lived entities that need to process events sequentially per-entity but concurrently across entities (thousands of IoT device sessions, per-connection protocol state).
- Lock-based code protecting this state has already produced hard-to-reproduce race conditions or deadlocks from lock-ordering mistakes, and the state naturally partitions by entity.
- You need location transparency — the actor abstraction extends cleanly from in-process to distributed (Akka, Erlang/OTP processes) without changing the programming model.

## When NOT to use

- The problem has no real concurrent access to begin with — wrapping a single-threaded batch job or a straightforward request/response service in an actor framework adds message-passing overhead, mailbox latency, and a new mental model (ask patterns, timeouts, supervision trees) for zero concurrency benefit. This is the single most common actor misuse: reaching for Akka because it's "the concurrency pattern" on code that was never contended.
- Your team has no experience with the model and the deadline doesn't allow for the learning curve — actor systems have their own failure modes (mailbox overflow, dead letters, ask-pattern timeout tuning, supervision strategy design) that are unfamiliar territory and easy to get wrong under time pressure.
- The workload is fundamentally a data-parallel computation (map/reduce over a large collection) — a thread pool or parallel stream expresses this far more directly than modeling it as message-passing actors.
- Strict, low-latency request/response with a result needed synchronously — actors are message-passing and asynchronous by nature; forcing synchronous semantics on top (blocking `ask` with timeouts everywhere) fights the model and adds latency for no isolation benefit when a simple synchronized method would do.

## Structure

Each actor has a private mailbox and processes messages one at a time, single-threaded from its own point of view, so no lock is ever needed inside it.

```java
class CounterActor {
    private int count = 0; // never accessed except by this actor's own thread
    private final BlockingQueue<Message> mailbox = new LinkedBlockingQueue<>();

    void start() {
        Thread.startVirtualThread(() -> {
            while (true) {
                Message msg = mailbox.take(); // sequential processing, no locks
                switch (msg) {
                    case Increment i -> count++;
                    case GetCount g -> g.replyTo().complete(count);
                }
            }
        });
    }

    void tell(Message msg) { mailbox.offer(msg); } // fire-and-forget send
}
```

## Consequences

- **Benefits**: no shared-mutable-state races by construction — there's nothing to forget to lock; failure isolation (one actor crashing doesn't corrupt another's state); the model scales naturally from in-process to distributed systems (same mental model for a local actor and a remote one).
- **Costs**: message-passing overhead per interaction, even for what would be a nanosecond field read in a lock-based design; debugging shifts from "which thread corrupted this field" to "why did this message never arrive / arrive out of order / time out" — a different but not necessarily easier class of bug, and one that's just as hard to reproduce deterministically; unbounded mailboxes reintroduce the same OOM risk as unbounded queues elsewhere; deadlock is traded for a different hazard — two actors `ask`-ing each other synchronously with blocking waits can still deadlock, and a slow actor can back up its mailbox indefinitely if nothing bounds it or sheds load.
- Supervision and error handling need explicit design (what happens when an actor's message-processing loop throws?) — this doesn't happen automatically and is easy to leave as an afterthought.

## Related patterns

- [Producer-Consumer](producer-consumer.md) — an actor's mailbox is a producer-consumer queue with exactly one consumer (the actor itself).
- [Futures/Promises](futures-promises.md) — the `ask` pattern (request-response with actors) is typically implemented by returning a `Future`/`CompletableFuture` that completes when the reply arrives.
- [Command](../behavioral/command.md) — messages sent to an actor are effectively Command objects.
- Immutable messages — messages should be immutable value objects so they're safe to hand across thread boundaries without synchronization.

## Smells that suggest this pattern

- A class with mutable fields guarded by a `synchronized` keyword on nearly every method, where the class represents one identifiable entity (a session, a connection, a game piece) rather than a generic shared resource.
- Recurring deadlocks or races traced back to inconsistent lock-acquisition order across multiple locks protecting related state.
- Code that already resembles a hand-rolled single-threaded event loop with a queue in front of it (someone independently reinvented the actor's mailbox).

## Common misuse

- Adopting an actor framework project-wide as a default architecture, then writing actors that are `ask`ed synchronously everywhere — this recreates lock-based request/response with worse ergonomics and higher latency, none of the isolation benefit realized because the code never leverages fire-and-forget messaging.
- Actors with unbounded mailboxes under bursty load — the mailbox becomes an unbounded queue by another name, and OOMs the same way an unbounded `BlockingQueue` would.
- Splitting genuinely single-threaded, non-contended logic into multiple communicating actors purely for architectural uniformity — this is complexity added to satisfy a pattern, not a problem.
- No supervision strategy defined, so an unhandled exception in one actor's processing loop silently kills that actor and its mailbox drains into nowhere, with no visibility that it happened.
