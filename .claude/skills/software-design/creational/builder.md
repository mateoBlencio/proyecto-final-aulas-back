# Builder

## Intent

Separate the construction of a complex object from its representation, so the same step-by-step construction process can produce different configurations, and so callers can assemble an object with many optional parts without a combinatorial constructor explosion.

## Problem

An object has many parameters, several of them optional, some with interdependencies or validation rules that only make sense once the whole set is known. Telescoping constructors (`new Pizza(size)`, `new Pizza(size, cheese)`, `new Pizza(size, cheese, pepperoni)`, ...) or one giant constructor with 10 positional parameters are error-prone (easy to swap two same-typed args) and unreadable at call sites. Setters on a mutable object solve readability but allow constructing an object in an invalid, half-initialized state.

## When to use

- An object has 4+ parameters, a meaningful subset of which are optional, and constructor overloading would require an unreasonable number of combinations.
- Construction has ordering constraints or cross-field validation that should be enforced before the object exists (e.g. "if `retryCount > 0` then `retryDelay` must be set") — the builder's `build()` step is the natural place to validate.
- You want the constructed object to be immutable once built, but need a mutable, multi-step process to assemble it first.
- The same construction process should be able to produce different representations/configurations (classic GoF framing) — e.g. a `RequestBuilder` that can emit either a `HttpRequest` or a `curl` command string from the same fluent calls.
- Generated code (protobuf, some ORMs) already gives you builders — leverage them rather than fighting them with wrapper constructors.

## When NOT to use

- The object has 2-3 parameters, all effectively required, none optional. `new Point(x, y)` needs no builder — wrapping it in `Point.builder().x(1).y(2).build()` is pure ceremony that makes the simple case harder to read.
- Failure scenario: a `UserBuilder` is introduced for a `User(id, name, email)` value object with three mandatory fields and no optional ones or validation beyond null checks. Every call site now needs four lines instead of one, and the class ships with a redundant mutable intermediate representation for no benefit.
- The "optional" parameters can be modeled as a single cohesive sub-object instead (e.g. `ConnectionOptions`) passed as one constructor argument — this is often simpler than a builder and reusable as a value on its own.
- You need to construct many instances in a hot loop — builders often allocate an intermediate builder object per instance; if allocation pressure matters, a plain constructor or object pool may be more appropriate. Measure before assuming this matters, but don't reach for Builder in a known hot path without checking.
- Failure scenario: a builder is added to a class purely to get "fluent" call sites, but the class has no optional fields and no validation — the fluent API is aesthetic, not solving the actual telescoping-constructor problem, and now two ways exist to construct the object (constructor + builder), inviting inconsistent usage.
- In Java records / Kotlin data classes with named/default arguments, language-level named parameters often eliminate the need for Builder entirely — check the language feature before reaching for the pattern.

## Structure

Participants: `Builder` (declares step methods, usually fluent/chainable, plus `build()`), `ConcreteBuilder` (implements the steps, accumulates state), `Director` (optional — encapsulates a specific construction recipe/sequence, often omitted in modern fluent-builder style), `Product` (the resulting, often immutable, object).

```java
final class HttpRequest {
    private final String url;
    private final String method;
    private final Map<String, String> headers;
    private final Duration timeout;

    private HttpRequest(Builder b) {
        this.url = Objects.requireNonNull(b.url);
        this.method = b.method;
        this.headers = Map.copyOf(b.headers);
        this.timeout = b.timeout;
    }

    static Builder builder(String url) { return new Builder(url); }

    static final class Builder {
        private final String url;
        private String method = "GET";
        private final Map<String, String> headers = new HashMap<>();
        private Duration timeout = Duration.ofSeconds(30);

        private Builder(String url) { this.url = url; }

        Builder method(String method) { this.method = method; return this; }
        Builder header(String key, String value) { headers.put(key, value); return this; }
        Builder timeout(Duration timeout) { this.timeout = timeout; return this; }

        HttpRequest build() {
            if (timeout.isNegative()) throw new IllegalStateException("timeout must be positive");
            return new HttpRequest(this);
        }
    }
}
```

## Consequences

Benefits: readable call sites for objects with many optional parameters; enforces required fields and cross-field validation at a single `build()` chokepoint; allows the resulting object to be truly immutable; construction logic (defaults, validation) lives in one place instead of duplicated across overloaded constructors.

Costs: doubles the number of classes/fields to maintain (the object and its builder must be kept in sync); adds a small amount of ceremony and allocation for objects that didn't need it; a fluent chain that omits a required field only fails at `build()` time (runtime), not at compile time, unless a step-builder/typestate variant is used, which adds further complexity; can encourage over-parameterized objects because adding a new optional field is "free" via the builder, silently growing the object's responsibility over time.

## Related patterns

- [Factory Method](factory-method.md) — Builder addresses *how* to assemble one object step by step; Factory Method addresses *which* concrete type to instantiate. Often combined: a factory returns a preconfigured builder.
- [Abstract Factory](abstract-factory.md) — a factory can hand out different builders for different product families.
- [Prototype](prototype.md) — an alternative to Builder when most configurations are small variations of a known-good instance, cloned and tweaked rather than assembled from scratch.
- [Immutable Object / Value Object](../principles/solid.md) — Builder is the standard companion pattern for constructing immutable objects safely.

## Smells that suggest this pattern

- Multiple overloaded constructors on the same class differing only in which optional parameters are supplied ("telescoping constructor").
- A constructor with more than ~4-5 parameters, especially several of the same type (easy to transpose by mistake).
- Object construction scattered across the codebase with subtly different validation applied at each site because there's no single choke point.
- A mutable class with public setters used only during construction, then never mutated again — signals the setters should be a builder step instead, and the resulting object should be immutable.

## Common misuse

- Adding a builder to a small value object (2-3 required fields, no optional ones) purely for a "fluent" feel — extra ceremony with no telescoping problem to solve.
- Builders whose `build()` performs no validation at all — at that point it's just a slower, more verbose constructor and the pattern isn't earning its cost.
- Reusable/mutable builder instances shared across threads or across multiple `build()` calls without resetting state, causing one built object's fields to leak into the next.
- Generating a builder via IDE/lombok for every DTO in the codebase regardless of parameter count, as a house style rather than a response to an actual construction problem.
