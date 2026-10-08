# Decorator

## Intent

Attach additional responsibilities to an individual object dynamically, without altering its class or affecting other instances of the same class.

## Problem

You need to add optional, combinable behavior to objects — and subclassing for every combination would explode: `Coffee`, `CoffeeWithMilk`, `CoffeeWithSugar`, `CoffeeWithMilkAndSugar`, and so on for every new addable behavior.

## When to use

- Behavior needs to be added, removed, or stacked in varying combinations at runtime.
- The added behavior must wrap the original object transparently, preserving its interface — I/O streams, HTTP client wrappers, caching/logging/retry wrappers around a service call.
- The set of combinations is open-ended or large enough that per-combination subclassing is infeasible.
- Different callers of the same component need different combinations of add-ons simultaneously (one caller wants caching only, another wants caching plus retry) — a single flag on the base class can't express per-call-site combinations cleanly.
- The behavior needs to be optional and pluggable in a library/framework consumers extend, without giving them access to the base class source.

## When NOT to use

- The set of combinations is small and known at compile time. A flag/parameter or a couple of static factory methods reads more clearly than a decorator class hierarchy.
- Decorators are chained so deeply that the runtime call stack becomes unreadable and undebuggable — "decorator soup."
- **Failure scenario 1:** `new LoggingDecorator(new CachingDecorator(new RetryDecorator(new AuthDecorator(new MetricsDecorator(service)))))`. Five wrapper classes for what's really a linear, ordered list of cross-cutting concerns. An explicit ordered list of interceptors/middleware is more maintainable and far easier to inspect (print the list) than nested wrapping (step through five stack frames in a debugger).
- **Failure scenario 2:** using Decorator to add a single, always-on behavior — e.g., every call to a service must be logged, full stop. That's not something that varies per instance, so it doesn't need Decorator's flexibility; put the logging directly in the class, or use a cross-cutting interceptor/AOP aspect.
- When a boolean/enum parameter would do: `save(data, boolean compress)` is simpler than `new CompressingDecorator(saver).save(data)` when there are only one or two toggles and they're unlikely to grow.

## Structure

`Component` declares the interface. `ConcreteComponent` implements the base behavior. `Decorator` is an abstract class implementing `Component` while holding a wrapped `Component`. `ConcreteDecorator`s add behavior before/after delegating to the wrapped instance.

```java
interface DataSource {                       // Component
    void write(String data);
}

class FileDataSource implements DataSource {  // ConcreteComponent
    public void write(String data) { /* write to disk */ }
}

abstract class DataSourceDecorator implements DataSource {
    protected final DataSource wrapped;
    DataSourceDecorator(DataSource wrapped) { this.wrapped = wrapped; }
}

class CompressingDecorator extends DataSourceDecorator {
    CompressingDecorator(DataSource wrapped) { super(wrapped); }
    public void write(String data) { wrapped.write(compress(data)); }
    private String compress(String data) { /* ... */ return data; }
}
```

## Consequences

**Benefits:** add or remove responsibilities at runtime; avoids subclass explosion for combinable behaviors; each decorator has a single responsibility; open for extension without modifying the wrapped class.

**Costs:** proliferation of small wrapper classes; the full behavior of an object is no longer visible at a glance — you must trace the chain; decorator ordering can matter and is easy to get wrong (compress-then-encrypt vs. encrypt-then-compress produce different results); the decorated object is a different instance than the original, which can break `==` comparisons or `instanceof`-based logic elsewhere in the codebase; stack traces through several layers of decorators are noisier to read during debugging than a single method call.

## Related patterns

- [Proxy](proxy.md) — structurally identical (wraps the same interface), but the intent differs: Proxy *controls access* to a single, fixed real subject (lazy load, remote call, permission check) and typically owns/manages that subject's lifecycle. Decorator *stacks arbitrary, combinable behavior* onto an object it's simply handed, and decorators are meant to compose freely with each other.
- [Composite](composite.md) — similar wrapping structure, but Composite's purpose is representing a part-whole tree, not layering behavior.
- [Chain of Responsibility](../behavioral/chain-of-responsibility.md) — related when a decorator chain effectively becomes a processing pipeline; Chain of Responsibility is the more explicit pattern when each link can decide to stop propagation.

## Smells that suggest this pattern

- Multiplying subclasses for every combination of optional behavior (`CoffeeWithMilk`, `CoffeeWithSugar`, `CoffeeWithMilkAndSugar`).
- Boolean-parameter explosion in a method signature trying to toggle several independent concerns at once.
- The same cross-cutting logic (logging, caching, retries) copy-pasted across multiple otherwise-unrelated classes.
- A method signature accumulates more and more optional flags over time (`fetch(id, useCache, withRetry, logged, traced)`), each one independently toggleable.
- New "variant" subclasses are added purely to combine two existing behaviors that already exist separately elsewhere in the hierarchy.

## Common misuse

- Deep decorator chains nobody can read or step through — "decorator soup." Flatten into an explicit, ordered middleware/interceptor list instead.
- Using Decorator for a single, always-applied behavior instead of just putting it in the base class or a cross-cutting interceptor.
- Confusing Decorator with Proxy because they look identical in a UML diagram — Proxy's job is access control and lifecycle management of one subject; Decorator's job is stackable behavior extension.
