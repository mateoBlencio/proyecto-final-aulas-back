# SOLID

## Intent

Five design guidelines for organizing classes and modules so that change in one part of a system doesn't force cascading change elsewhere. Each addresses a different axis of coupling.

## Problem

Software rots because responsibilities get tangled: one class does five jobs, one change to a base type breaks three subtypes, one interface forces implementors to stub out methods they don't need, one high-level module reaches directly into a low-level implementation detail. SOLID is five separate answers to five separate coupling problems — not one unified theory.

## When to use

- **SRP**: a class has multiple reasons to change (e.g. a `User` class that also handles persistence and email formatting).
- **OCP**: you keep editing the same `switch`/`if-else` chain every time a new case is added (new payment method, new report type).
- **LSP**: a subclass overrides a method to throw `UnsupportedOperationException` or silently do nothing to satisfy a signature.
- **ISP**: an interface has methods that most implementors stub out or throw `NotImplementedException` on.
- **DIP**: a service directly `new`s a concrete infrastructure class (a specific DB driver, HTTP client) instead of depending on an abstraction.

## When NOT to use

- **SRP** taken too far produces classes with one public method each and no cohesion — "a reason to change" is not "a single method"; grouping genuinely related behavior (e.g. all validation rules for one entity) in one class is fine.
- **OCP** applied preemptively means building a plugin/strategy architecture for a dimension that has never actually varied. If there's one tax calculation method and no second one on the horizon, an `abstract TaxStrategy` is speculative.
- **LSP** purism can block legitimate narrowing of preconditions in domain modeling (e.g. a `ReadOnlyRepository` intentionally not supporting `save` is a different interface, not an LSP violation — don't force an inheritance hierarchy to route around this).
- **ISP** taken to the extreme yields one interface per method, forcing consumers to depend on ten fragments instead of one cohesive contract they actually always use together.
- **DIP** injected everywhere — including for concrete, stable, side-effect-free value types (a `Money` class, a `Clock`, a date formatter) — adds an interface and a constructor parameter with zero payoff since there will only ever be one implementation.

## Structure

```java
// SRP: split persistence out of the domain object
class Invoice { BigDecimal total() { ... } }
class InvoiceRepository { void save(Invoice i) { ... } }

// OCP: extend via new implementation, not new branches
interface DiscountPolicy { BigDecimal apply(BigDecimal price); }
class SeasonalDiscount implements DiscountPolicy { ... }

// LSP: subtype honors the base contract's expectations
interface Shape { double area(); }
class Square implements Shape { ... } // no surprise exceptions

// ISP: narrow, role-specific interfaces
interface Readable { byte[] read(); }
interface Writable { void write(byte[] data); }

// DIP: depend on abstraction, inject concrete detail
class OrderService {
    OrderService(PaymentGateway gateway) { this.gateway = gateway; }
}
```

## Consequences

- **Benefits**: isolated change (modify one policy without touching callers), easier unit testing (interfaces are mockable), fewer regressions when adding variants.
- **Costs**: more files and indirection per concept; a reader has to jump through an interface to find the real implementation; over-application produces "interface for every class" codebases that are harder to navigate than a few concrete classes would be. Each of the five principles is a trade of upfront design cost for later flexibility — that flexibility is only worth it if the change dimension it protects actually gets exercised.

## Related patterns

- [Strategy](../behavioral/strategy.md) — the usual mechanism for OCP.
- Dependency Injection — the usual mechanism for DIP: pass the abstraction in (constructor/setter) instead of constructing the concrete dependency inside the class.
- [Template Method](../behavioral/template-method.md) — related to LSP-safe extension points.
- [God Object](../anti-patterns/god-object.md) — the SRP failure mode.
- [Premature Abstraction](../anti-patterns/premature-abstraction.md) — the OCP-taken-too-far failure mode.
- [yagni.md](yagni.md), [kiss.md](kiss.md) — counterweights to over-applying OCP/DIP.

## Smells that suggest this pattern

- A class name with "and" or "Manager"/"Helper"/"Utils" doing unrelated things (SRP).
- Repeated `if (type == X)` blocks scattered across the codebase (OCP).
- Overridden methods that throw or no-op instead of implementing the contract (LSP).
- Implementations with `throw new UnsupportedOperationException()` (ISP).
- `new ConcreteThing()` calls buried inside business logic classes (DIP).

## Common misuse

- Adding an interface to every class "for testability" even when there is exactly one implementation and no test ever swaps it — this is DIP cargo-culting, not DIP.
- Splitting a cohesive class into five collaborator classes because each method is technically "a different responsibility," destroying locality of reasoning (SRP over-application).
- Building a `Factory` + `Strategy` + `Visitor` stack to satisfy OCP for a requirement that changes once a year, when a single `if` would be reread by every future maintainer in five seconds.
- Declaring `IUserService` for every `UserService` as a blanket team convention — an interface with one implementation and no plan for a second is not abstraction, it's ceremony.
