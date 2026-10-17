# Readers-Writers

## Intent

Allow many concurrent readers to access shared data simultaneously while guaranteeing writers get exclusive access, so read-heavy workloads aren't serialized behind a single mutex when no write is happening.

## Problem

A plain mutex serializes all access — readers block other readers even though concurrent reads of unchanging data are safe. For read-heavy workloads (a config cache read thousands of times per write) that's wasted parallelism. But writes can't just run alongside reads either: a writer mutating a data structure while a reader iterates it produces torn reads, `ConcurrentModificationException`, or corrupted state. Readers-Writers coordination needs multiple readers to proceed together, a writer to have the data entirely to itself, and (usually) a policy for who wins when both are waiting, to avoid starving one side.

## When to use

- Read frequency vastly exceeds write frequency (routing tables, feature flags, configuration caches, in-memory indexes) and profiling shows read contention on a mutex is a real bottleneck.
- The protected data structure isn't naturally immutable/copy-friendly (large graph, in-place index) so copy-on-write would be too expensive per write.
- Reads need a consistent snapshot for their duration (can't have a writer mutate mid-iteration) but don't need to block each other.
- You've measured that a `synchronized`/single-lock version is CPU-bound on lock contention, not on the work itself.

## When NOT to use

- The write path is nearly as frequent as the read path — a `ReadWriteLock` degenerates toward a plain mutex under mixed load while adding more overhead (read-lock acquisition isn't free) than a `synchronized` block would have cost.
- The data is small and rarely mutated — an immutable object republished via a `volatile` reference (copy-on-write) gives lock-free reads with none of the reader/writer bookkeeping, and is simpler to reason about and impossible to get wrong with respect to starvation.
- You haven't measured lock contention at all — reaching for `ReentrantReadWriteLock` because "reads should be parallel" without profiling is solving a problem you don't know you have, at the cost of code that's harder to reason about than a plain lock.
- Write-side starvation is a real risk under read-heavy load with a non-fair lock — if a steady stream of readers can indefinitely delay a writer waiting for exclusive access, that's a correctness problem (stale data never refreshes), not just a performance one.

## Structure

A `ReadWriteLock` splits into a shared read lock (multiple holders) and an exclusive write lock (single holder, blocks all readers).

```java
private final ReadWriteLock lock = new ReentrantReadWriteLock();
private Map<String, String> config = new HashMap<>();

public String get(String key) {
    lock.readLock().lock();
    try {
        return config.get(key); // many threads can be here at once
    } finally {
        lock.readLock().unlock();
    }
}

public void reload(Map<String, String> newConfig) {
    lock.writeLock().lock();
    try {
        config = newConfig; // exclusive — no readers active during this
    } finally {
        lock.writeLock().unlock();
    }
}
```

## Consequences

- **Benefits**: read throughput scales with core count instead of serializing on one lock; writes remain fully consistent and isolated; better fit than a mutex when the read:write ratio is heavily skewed and measured.
- **Costs**: `ReadWriteLock` acquisition has more overhead than a plain `synchronized` block, so under low contention or balanced read/write it can be slower, not faster; writer starvation is possible with non-fair implementations under sustained read load; the locking protocol is more complex to reason about (two lock objects, upgrade/downgrade rules — Java's `ReentrantReadWriteLock` does not support lock upgrading from read to write without releasing first, which is a common source of deadlock when developers assume it does); bugs here are classic concurrency bugs — they reproduce only under specific timing and load, making them expensive to catch in code review or ordinary testing.
- Fairness policy is a real design decision, not a default to accept blindly: fair mode avoids starvation but reduces throughput; non-fair mode maximizes throughput but can starve writers indefinitely under continuous read pressure.

## Related patterns

- [Producer-Consumer](producer-consumer.md) — different coordination problem (handoff vs. concurrent access) but often confused with this at first glance.
- Immutable, republished snapshot (copy-on-write via a `volatile` reference) — the usual alternative that avoids locking readers entirely.
- [Thread Pool](thread-pool.md) — readers and writers are often pool threads contending on this same lock.

## Smells that suggest this pattern

- A single `synchronized` method/block wrapping both reads and writes to a data structure that is read far more often than written, with profiling showing contention on that lock.
- Manual `AtomicReference` swapping of a whole large structure on every read (a sign copy-on-write is too expensive here and real reader/writer coordination is warranted instead).
- Comments like "// TODO: this blocks readers unnecessarily" next to a plain mutex around a cache.

## Common misuse

- Reaching for `ReentrantReadWriteLock` as the default lock type "because reads should be fast," without ever measuring whether read contention was the actual bottleneck — often a plain `synchronized` block was never the problem.
- Assuming `ReentrantReadWriteLock` allows atomic upgrade from a read lock to a write lock — it doesn't; attempting to acquire the write lock while holding the read lock deadlocks the thread against itself.
- Using it to protect a small, cheaply-copyable value (a `Map` with a dozen entries, a config object) where an immutable-and-republish approach would be simpler and lock-free.
- Ignoring fairness and being surprised in production when a background writer thread (e.g. a config refresher) never gets scheduled because request-handling reader threads keep the read lock continuously occupied.
