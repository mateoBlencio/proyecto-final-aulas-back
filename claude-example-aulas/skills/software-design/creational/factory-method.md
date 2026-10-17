# Factory Method

## Intent

Defer object instantiation to a subclass or a designated creation method, so the calling code depends on an interface/abstract type rather than a concrete constructor.

## Problem

Client code needs to create objects, but the concrete type to instantiate depends on context (config, subclass, runtime input) that the client shouldn't have to know about. Hard-coding `new ConcreteProduct()` in the client couples it to that concrete class and duplicates the selection logic anywhere else an instance is needed.

## When to use

- A class can't anticipate which concrete subtype of an object it needs to create — that decision belongs to a subclass or a pluggable strategy.
- You want to centralize object-creation logic that currently is copy-pasted at every call site (`new` scattered through the codebase, each with the same if/else on type).
- A framework/library needs to let users override what gets created (e.g. Spring's `BeanFactory`, a `TransportFactory` that a subclass overrides to produce `HttpTransport` vs `WebSocketTransport`).
- You're introducing a new implementation of an existing abstraction and need construction to happen through one seam, not N call sites.

## When NOT to use

- There is exactly one concrete implementation today and no concrete plan for a second. A `UserFactory` that only ever returns `User` is indirection with no payoff — a constructor or a `static create(...)` method is enough.
- The "variation" is really just optional/default parameters. `new Connection(host, port, timeoutMs)` doesn't need a factory hierarchy; it needs a builder or default arguments.
- Failure scenario: a team builds `AbstractCreator`/`ConcreteCreator` for every DTO in the codebase "for consistency," producing a parallel class hierarchy that must be updated in lockstep with the domain model, for types that are never polymorphically created.
- Failure scenario: the selection logic is trivial and unlikely to grow (e.g. always creating a `JsonParser`), but a factory method is added anyway, forcing every caller through an extra layer of indirection and an extra unit test double for no behavioral gain.
- The object graph is complex with many optional parts — that's a construction-order problem, not a subtype-selection problem; see [Builder](builder.md).
- In a DI framework (Spring), if the "factory" only ever wires together the app's own beans, plain `@Bean` methods or constructor injection already solve this — a bespoke Factory Method hierarchy duplicates what the container gives you for free.

## Structure

Participants: `Product` (interface), `ConcreteProduct` (implementation), `Creator` (declares `factoryMethod()`, may contain other logic that uses the product), `ConcreteCreator` (overrides `factoryMethod()` to return a specific `ConcreteProduct`).

```java
interface Notifier {
    void send(String message);
}

abstract class NotificationDispatcher {
    abstract Notifier createNotifier();

    void dispatch(String message) {
        Notifier notifier = createNotifier();
        notifier.send(message);
    }
}

class EmailDispatcher extends NotificationDispatcher {
    @Override
    Notifier createNotifier() {
        return new EmailNotifier();
    }
}

class SmsDispatcher extends NotificationDispatcher {
    @Override
    Notifier createNotifier() {
        return new SmsNotifier();
    }
}
```

In Spring-style code this is often flattened into a single method that switches on a config value or a `Map<Type, Supplier<Notifier>>` registry, rather than a subclass hierarchy — same intent, less ceremony.

## Consequences

Benefits: decouples client code from concrete classes; adding a new product type means adding a new creator/branch, not touching existing client code (Open/Closed); centralizes creation logic and any invariants around it (validation, caching, pooling).

Costs: introduces a class (or at least a method-level indirection) purely for construction, which is one more thing to navigate when reading the code; a full subclass-per-product hierarchy can get deep and parallel to the product hierarchy itself, doubling the number of types for N variants; overuse makes "where is this actually constructed" harder to trace via IDE go-to-definition alone.

## Related patterns

- [Abstract Factory](abstract-factory.md) — often implemented using multiple Factory Methods, one per product in the family.
- [Builder](builder.md) — for constructing one complex object step by step, not for selecting among subtypes.
- [Prototype](prototype.md) — an alternative when creation should proceed by cloning a preconfigured instance instead of subclassing a creator.
- [Template Method](../behavioral/template-method.md) — Factory Method is a specialization of Template Method where the varying step is "which object to create."
- [Dependency Inversion](../principles/solid.md) — Factory Method is a common way to satisfy DIP at the construction boundary.

## Smells that suggest this pattern

- Repeated `if (type == X) return new A(); else if (type == Y) return new B();` blocks scattered across multiple call sites.
- A client class importing and directly instantiating many concrete classes from a single interface's hierarchy.
- Adding a new implementation of an interface requires touching several unrelated files that all construct instances of that interface.
- Unit tests that can't substitute a fake implementation because construction is inlined with `new` deep in business logic.

## Common misuse

- Building a `Creator`/`ConcreteCreator` class pair for a single, unlikely-to-change implementation "in case we need another one later" (speculative generality/YAGNI violation).
- Using Factory Method where a simple constructor overload or static factory method (`Product.of(...)`) would do — reach for the pattern only when the *selection* logic itself has real branching or is meant to be overridden by callers, not just to avoid the word `new`.
- Wrapping dependency-injection-managed beans in a hand-rolled factory method, duplicating what `@Bean`/`@Component` + constructor injection already provides.
