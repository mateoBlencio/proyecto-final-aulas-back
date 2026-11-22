# Prototype

## Intent

Create new objects by copying an existing, fully-initialized instance (a "prototype") instead of instantiating a class from scratch, so callers can obtain preconfigured objects without coupling to their concrete class or repeating expensive setup.

## Problem

Sometimes the cheapest or only practical way to get a new object in a particular state is to copy one that's already in that state — either because constructing it from scratch is expensive (e.g. requires a network round-trip, a heavy computation, or database reads to reach a baseline configuration), or because the exact concrete class isn't known to the caller (only an instance is, e.g. handed to a plugin at runtime), or because you need many near-identical objects that differ only in a few fields and re-running full initialization for each is wasteful or introduces drift.

## When to use

- Creating an instance from scratch is significantly more expensive than copying an existing one (e.g. an object whose constructor loads reference data, opens a connection, or performs an expensive calculation).
- Client code only holds a reference to an object via an abstract interface and has no access to its concrete class or constructor (common in plugin architectures — `registry.getPrototype("shape").clone()`).
- You need many variants of a baseline configuration (e.g. a set of test fixtures, or per-tenant default settings) that differ only in a few fields, and want to guarantee they all start from an identical, known-good baseline rather than risk drift between several independent constructors.
- Runtime-composed object graphs (built via reflection, deserialization, or a builder) need to be duplicated with small tweaks without re-running the whole composition process.

## When NOT to use

- The object is cheap to construct and its class is known and accessible to the caller — `new Foo()` is simpler and clearer than `prototype.clone()`. Failure scenario: a codebase introduces a `Cloneable` prototype registry for simple DTOs that take microseconds to construct via their constructor; the clone infrastructure (registry lookup, deep-copy logic, `clone()` overrides) adds more code and more bugs than it removes.
- The object holds resources that shouldn't be duplicated — open file handles, DB connections, threads, locks. Cloning these either silently shares the resource (shallow copy — one object closing it breaks the other) or requires special-casing every such field, at which point the "simple copy" premise of the pattern has failed.
- Deep vs. shallow copy semantics aren't carefully thought through. Failure scenario: a `Cloneable` implementation does a shallow copy by default (Java's `Object.clone()`), and a mutable field like a `List` or nested object ends up shared between the original and the "copy" — mutating one silently corrupts the other, discovered only in production when concurrent requests using "independent" clones interfere with each other.
- Java's `Cloneable`/`clone()` in particular is widely considered a design mistake (no constructor is called, `clone()` returns `Object`, deep-copy correctness is entirely on the implementer) — prefer a copy constructor, a static `copyOf(...)` factory, or a `toBuilder()` method for the same intent with clearer semantics and type safety.
- The variation between "prototype" and "desired object" is large enough that most fields need to be overridden anyway — at that point [Builder](builder.md) expresses the construction more clearly than clone-then-mutate.

## Structure

Participants: `Prototype` (declares a `clone()`/`copy()` operation), `ConcretePrototype` (implements copying itself, deep-copying mutable internal state as needed), `Client` (obtains new objects by asking an existing instance to copy itself, not by calling `new`).

```java
interface ShapePrototype {
    ShapePrototype copy();
}

final class Polygon implements ShapePrototype {
    private final List<Point> vertices; // treated as immutable after construction
    private Color fillColor;

    Polygon(List<Point> vertices, Color fillColor) {
        this.vertices = List.copyOf(vertices); // defensive copy avoids aliasing
        this.fillColor = fillColor;
    }

    @Override
    public Polygon copy() {
        return new Polygon(this.vertices, this.fillColor); // constructor-based copy, not Object.clone()
    }

    void setFillColor(Color color) { this.fillColor = color; }
}

// usage: registry of known-good baseline shapes, cloned and tweaked per call
ShapePrototype redTriangle = registry.get("triangle").copy();
```

Preferring a copy constructor or static `copyOf` factory over `java.lang.Cloneable` sidesteps the shallow-copy pitfalls of the built-in mechanism while keeping the same intent.

## Consequences

Benefits: avoids re-running expensive initialization for objects that only need small variations; decouples client code from concrete classes when only an instance (not a class reference) is available; guarantees a consistent, known-good starting state for all copies, reducing drift between independently constructed instances.

Costs: deep-copy correctness is entirely the implementer's responsibility and is easy to get wrong, especially with mutable nested objects, circular references, or non-copyable resources (streams, connections, locks); adds an extra method/contract (`copy()`) to maintain on every participating class; can obscure where an object's state actually originated, since "cloned from X, then mutated" is a less traceable history than an explicit constructor call; Java's built-in `Cloneable` mechanism specifically is considered broken by much of the ecosystem (see Effective Java) and should generally be avoided in favor of copy constructors.

## Related patterns

- [Builder](builder.md) — an alternative when constructing variants; Builder assembles from parts, Prototype copies a whole and then adjusts. Sometimes combined: `prototype.toBuilder().withX(...).build()`.
- [Factory Method](factory-method.md) — both abstract away `new`, but Factory Method selects among known subclasses, Prototype duplicates a specific runtime instance whose class may not even be known to the caller.
- [Flyweight](../structural/flyweight.md) — opposite instinct in a sense: Flyweight shares one instance to avoid duplication where Prototype deliberately duplicates; choose based on whether the objects need independent mutable state (Prototype) or can safely share immutable state (Flyweight).
- [Immutable Object / Value Object](../principles/solid.md) — if the object were immutable, most Prototype problems (aliasing, shared mutable state) disappear; consider immutability as an alternative fix before reaching for careful deep-copy logic.

## Smells that suggest this pattern

- Repeated, expensive setup code duplicated across the codebase to reach the same baseline object configuration before diverging into variant-specific tweaks.
- Client code that only has an instance reference (e.g. from a plugin, a deserialized payload, or a registry) and needs a fresh, independent copy but has no access to the concrete constructor.
- Test fixture builders that manually re-specify every field for each test case, when most fields are identical to a "default" fixture and only 1-2 fields vary per test.

## Common misuse

- Implementing Java's `Cloneable`/`Object.clone()` without overriding it to deep-copy every mutable field, producing objects that appear independent but silently share nested mutable state.
- Reaching for a clone-based prototype registry when a constructor is cheap and directly accessible — added indirection with no performance or decoupling benefit.
- Cloning objects that wrap external resources (sockets, file handles, DB connections) and ending up with two objects racing to close/use the same underlying resource.
