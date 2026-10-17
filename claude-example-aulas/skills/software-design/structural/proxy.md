# Proxy

## Intent

Provide a surrogate or placeholder for another object to control access to it, without changing the real subject's interface or the client's code.

## Problem

You need to intercept access to an expensive, remote, or sensitive object to add lazy loading, access control, remote communication, or caching — while keeping the interface identical to the real object, so client code doesn't need to know it's talking to a proxy.

## When to use

- **Virtual proxy:** lazy-initialize an expensive object, deferring construction until it's actually needed.
- **Protection proxy:** control access based on caller permissions before delegating to the real subject.
- **Remote proxy:** represent an object that lives in another process or machine (generated gRPC/RMI stubs are this pattern in practice).
- **Caching proxy:** transparently cache results in front of a real subject.
- **Smart proxy:** add reference counting or resource-management logic (smart-pointer style) around access to a shared resource.
- **Logging/metrics proxy:** transparently record call frequency, latency, or arguments for an object without touching its implementation — useful when you can't modify third-party code directly.
- Wrapping a generated client stub (gRPC, RMI, OpenFeign) that already plays this role, and you want your application code to treat it exactly like a local implementation.

## When NOT to use

- There's no real cross-cutting concern to enforce. If the proxy just forwards every call to the real object with no lazy-load, access-control, remote-call, or caching logic, it's indirection with zero behavior.
- **Failure scenario 1:** a `UserServiceProxy` implements `UserService`, holds a `UserService` delegate, and forwards every method 1:1 "in case we need caching later." That's speculative — add the proxy when the concern actually exists, not ahead of time.
- **Failure scenario 2:** hand-rolling a full Proxy class hierarchy for lazy initialization when a `Supplier<T>` or a simple null-check-and-init does the same job in three lines. Don't build a pattern's worth of ceremony for what a language feature already covers.
- Access control is really a cross-cutting authorization concern that belongs in a security layer (interceptor, filter, AOP aspect, Spring Security), not a hand-written per-class Proxy. At scale, per-class protection proxies don't compose and duplicate the same check everywhere; a cross-cutting interceptor applies the rule once.

## Structure

`Subject` is the shared interface. `RealSubject` implements it with the real logic. `Proxy` also implements `Subject`, holds a reference to the `RealSubject` (created lazily or injected), and adds control logic before/after delegating.

```java
interface ReportGenerator {
    Report generate(String reportId);
}

class ExpensiveReportGenerator implements ReportGenerator {  // RealSubject
    public Report generate(String reportId) { /* costly computation */ return new Report(reportId); }
}

class CachingReportGeneratorProxy implements ReportGenerator {
    private final ReportGenerator delegate;
    private final Map<String, Report> cache = new ConcurrentHashMap<>();

    CachingReportGeneratorProxy(ReportGenerator delegate) { this.delegate = delegate; }

    public Report generate(String reportId) {
        return cache.computeIfAbsent(reportId, delegate::generate);
    }
}
```

## Consequences

**Benefits:** transparent to the client — same interface as the real subject; can defer expensive object creation until actually needed; adds access control, caching, or logging without touching the real subject's code; remote proxies hide network complexity behind a normal-looking interface.

**Costs:** added indirection and latency per call; another class to maintain per proxied type, unless using dynamic proxies or codegen; can mask real system behavior — a caching proxy silently returning stale data is a classic hard-to-diagnose bug; remote proxies can hide failure modes (network errors surfacing as generic exceptions) if not designed carefully; a protection proxy that fails open (silently skips the check on error) turns a security control into a false sense of safety.

## Related patterns

- [Decorator](decorator.md) — same structural shape (wraps the same interface), different intent: Proxy *controls access* to a single real subject it typically owns the lifecycle of; Decorator *adds stackable behavior* to an object it's simply handed, meant to compose freely with other decorators. If you're stacking several of these together for combinable features, you're doing Decorator, not Proxy.
- [Adapter](adapter.md) — Adapter changes the interface to match what a different client expects; Proxy keeps the *same* interface as the real subject throughout.
- [Facade](facade.md) — Facade simplifies access to *multiple* classes behind a new, smaller interface; Proxy controls access to a *single* object through the *same* interface it already had.

## Smells that suggest this pattern

- An expensive object constructed eagerly when it's rarely actually used — should be lazy.
- Repeated permission-check boilerplate scattered across call sites instead of centralized in one place.
- Hand-written network-call boilerplate mixed directly into business logic instead of hidden behind a stub/client interface.
- Ad hoc caching logic (manual `if (map.containsKey(...))` checks) copy-pasted around calls to a slow method.
- Startup time dominated by eagerly constructing objects that most requests never touch.
- Business logic directly checking `currentUser.hasPermission(...)` before every call to a sensitive object, repeated at every call site instead of enforced at one boundary.

## Common misuse

- Pass-through proxies with no actual behavior — delegation theater that adds a hop with no benefit.
- Using Proxy to add new behavior or responsibility rather than to control access — that's Decorator's job; conflating the two leads to confusing naming and unclear intent in the codebase.
- Building bespoke Proxy classes by hand for cross-cutting concerns (logging, metrics, retry) that a dynamic proxy, AOP framework, or interceptor chain would handle uniformly across many classes with far less boilerplate.
