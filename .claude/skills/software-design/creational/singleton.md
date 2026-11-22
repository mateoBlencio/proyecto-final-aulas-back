# Singleton

## Intent

Ensure a class has exactly one instance and provide a single, well-known global access point to it.

## Problem

Some things genuinely are singular in a running process — a hardware interface, a single connection pool's coordinator, an application-wide configuration snapshot loaded once at startup. The Singleton pattern guarantees exactly one instance exists and gives every part of the program the same access point to it, instead of each caller constructing its own (potentially inconsistent) instance or threading a reference through every layer manually.

## When to use

- There is a hard, real-world constraint that only one instance can correctly exist — e.g. a single hardware device driver, a license manager tracking global usage count, a single writer to an append-only log file where concurrent writers would corrupt it.
- The instance is genuinely stateless or its state is meant to be process-global and shared read-only (e.g. an immutable configuration object loaded once).
- You're implementing a well-known language/framework-level single-instance construct where the framework itself manages the lifecycle (a Spring `@Bean` with default `singleton` scope, a DI container's registration) — in this case the "pattern" is really just container-scoped lifecycle management, not the classic GoF static-instance-with-private-constructor.

## When NOT to use

- **This is the most misapplied pattern in the catalog — treat "when NOT to use" as the default and "when to use" as the narrow exception.**
- As a way to avoid passing a dependency through constructors — this is disguised global mutable state, not a legitimate single-instance constraint. Failure scenario: a `DatabaseConnection.getInstance()` singleton is reached for because threading a `Connection` through three layers of constructors feels tedious; six months later, tests can't run in parallel because they all share one static connection, and mocking it out for a unit test requires reflection hacks or a static-state reset in `@BeforeEach`.
- When it silently makes the codebase untestable. Singletons with mutable state persist between test runs unless explicitly reset, causing test-order-dependent failures ("test B fails only when run after test A") that are notoriously hard to diagnose. If you find yourself adding a `resetForTests()` static method, that's a strong signal the pattern is actively hurting the codebase.
- When the "one instance" constraint is actually just "one instance per application," which DI containers already give you via singleton-scoped beans — using a hand-rolled static-instance Singleton on top of (or instead of) the container duplicates lifecycle management the container already does, and hides the dependency from constructor signatures, making it invisible to readers and to the container's own dependency graph.
- When multiple instances might legitimately be needed later (multi-tenant configs, multiple environments in the same process for testing, multiple named caches) — a hard-coded Singleton makes that change expensive because every call site references the static accessor directly instead of an injected reference.
- Failure scenario: a `Logger.getInstance()` singleton accumulates app-wide state (a request-scoped correlation ID, say) because "there's only one logger" was true when it was written; later, concurrent requests overwrite each other's correlation ID because the "one instance" assumption never accounted for concurrent per-request state.
- In multi-threaded/concurrent contexts, a Singleton with mutable state is a shared-mutable-state bug magnet: every access needs synchronization or must be proven safe, and that safety analysis has to be redone every time a new field is added — see [concurrency](../concurrency/).

## Structure

Participants: `Singleton` (holds the sole instance, exposes a static access point, controls its own instantiation).

```java
final class AppConfig {
    private static volatile AppConfig instance;
    private final Map<String, String> settings;

    private AppConfig(Map<String, String> settings) {
        this.settings = Map.copyOf(settings);
    }

    static AppConfig getInstance() {
        AppConfig result = instance;
        if (result == null) {
            synchronized (AppConfig.class) {
                if (instance == null) {
                    instance = result = new AppConfig(loadFromDisk());
                }
            }
        }
        return result;
    }

    String get(String key) { return settings.get(key); }
}
```

In Spring, the equivalent — and generally preferable — form is:

```java
@Component // default scope is singleton, container-managed
class AppConfig {
    // constructor injection, no static accessor, testable via a new instance per test
}
```

The container form keeps the dependency visible in constructor signatures and lets tests instantiate a fresh copy per test without any global state to reset.

## Consequences

Benefits: guarantees a single point of truth for genuinely singular resources; avoids redundant expensive initialization (e.g. loading config twice); gives every caller the same access point without threading a reference through every layer.

Costs: introduces global mutable state, which breaks the ability to reason about a function's behavior from its inputs alone; makes unit testing significantly harder — tests can't easily substitute a fake/mock instance, can leak state between test runs, and can't run in parallel if the singleton has mutable state; hides a real dependency behind a static accessor, so it's invisible in constructor signatures and in the class's public contract, making the dependency graph harder to see; hard-codes "exactly one, forever" into the design, which is expensive to walk back if a legitimate need for more than one instance appears later; in concurrent code, requires careful synchronization on both creation (lazy init races) and every subsequent mutable access.

## Related patterns

- [Dependency Injection / Dependency Inversion](../principles/solid.md) — the standard antidote to Singleton-as-global-state: let a container manage the single instance's lifecycle and inject it explicitly, keeping the dependency visible and swappable in tests.
- [Factory Method](factory-method.md) — sometimes conflated with Singleton, but Factory Method is about *which type* to construct, not about limiting instance count.
- [Monostate](../anti-patterns/premature-abstraction.md) — a close cousin (all instances share static state instead of there being one instance) with largely the same testability problems; not an improvement over Singleton.
- [God Object](../anti-patterns/god-object.md) — Singletons have a tendency to accumulate unrelated responsibilities over time because they're the most convenient global access point in the codebase; watch for this drift.

## Smells that suggest this pattern

- Genuinely rare: a hardware resource, OS-level handle, or external system that structurally cannot support more than one concurrent writer/owner in the same process.
- A configuration object that is loaded once at startup, never mutated, and needs to be visible broadly — though even here, DI-managed single-scope beans usually cover this without a hand-rolled Singleton.

## Common misuse

- Using `getInstance()` purely to avoid constructor parameter threading — the single most common misuse. The fix is passing the dependency explicitly (constructor injection), not eliminating the "hassle" via a static accessor.
- Storing request-scoped or otherwise transient state on a process-wide Singleton, causing cross-request/cross-thread data leakage.
- Reaching for a hand-rolled static-instance Singleton in a codebase that already has a DI container managing singleton-scoped beans — this duplicates lifecycle management the container provides and bypasses its dependency graph and test-friendliness.
- Adding a `reset()`/`resetForTests()` static method to make a Singleton testable — a strong signal the design should not be a Singleton in the first place.
