# YAGNI (You Aren't Gonna Need It)

## Intent

Don't build capability for a requirement you don't have yet — build it when the requirement actually arrives.

## Problem

Speculative generality has a cost even if it's never triggered: it has to be understood, tested, and kept working through every subsequent change, and it's frequently wrong about the shape the future requirement actually takes — meaning it gets thrown away or reworked anyway, so the upfront investment bought nothing. YAGNI targets the coupling between "code that exists" and "maintenance burden," independent of whether that code is ever exercised.

## When to use

- You're adding a configuration option, extension point, or abstract base class "in case we need to support X later" and X is not on any roadmap or ticket.
- A method signature is growing extra optional parameters for hypothetical future callers that don't exist.
- Someone proposes a plugin architecture for a system with exactly one plugin.
- A database schema adds nullable columns for a feature that hasn't been designed yet.

## When NOT to use

- Don't apply YAGNI to skip a genuinely known near-term requirement that's already been discussed and scoped, just because it isn't in the current ticket — that's scope-dodging, not YAGNI. If the team has already agreed multi-currency support ships next quarter, hardcoding USD-only with string formatting that can't hold a currency code isn't YAGNI, it's creating rework.
- Don't use it to skip an extensibility point that costs almost nothing now but a lot later — e.g. defining an `enum PaymentMethod` with one value today versus hardcoding `"CREDIT_CARD"` as a magic string throughout the codebase. The enum isn't speculative generality, it's cheap insurance against a near-certain second case.
- Don't use it to justify skipping basic API contracts (interfaces at genuine module boundaries, versioned public APIs) on the theory that "we'll add that when we need it" — some things are far more expensive to retrofit than to design in from the start (e.g. adding auth to an API that shipped with none, or adding idempotency keys to a payment endpoint after it's in production).
- Don't use it to avoid writing error handling or considering edge cases that are near-certain in production (network failures, concurrent access) — those aren't hypothetical future features, they're known operating conditions.

## Structure

Speculative (violates YAGNI):
```java
interface ExportStrategy { byte[] export(Report r); }
class PdfExportStrategy implements ExportStrategy { ... } // only one ever used
class ExportStrategyRegistry { /* register/lookup machinery for strategies that don't exist yet */ }
```

Minimal, matches actual requirement:
```java
class PdfExporter {
    byte[] export(Report report) { ... }
}
```
When a second export format is actually requested, extract the interface then — with two real implementations to generalize from, the abstraction will be shaped correctly instead of guessed.

## Consequences

- **Benefits**: less code shipped and maintained, abstractions are derived from real requirements instead of guesses (so they fit better when finally needed), faster delivery of the actual current requirement.
- **Costs**: some refactoring later is inevitable when the second real requirement lands — that refactor has a real cost and sometimes touches call sites that speculative generality would have insulated. The bet YAGNI makes is that this later, informed refactor is cheaper than paying for speculative flexibility on every feature, most of which never gets used. That bet fails if the retrofit is disproportionately expensive (see When NOT to use).

## Related patterns

- [kiss.md](kiss.md) — sibling; YAGNI decides *whether* to build a thing now, KISS decides *how simply* to build the things you do need.
- [solid.md](solid.md) (OCP) — tension partner; OCP wants extension points, YAGNI wants to defer building them until a second real case exists.
- [Premature Abstraction](../anti-patterns/premature-abstraction.md) — the concrete failure mode of ignoring YAGNI.
- [Premature Abstraction](../anti-patterns/premature-abstraction.md) — speculative generality is this same failure mode under its more classic name.

## Smells that suggest this pattern

- Config flags, feature toggles, or strategy interfaces with only one real value/implementation, and no ticket or roadmap item for a second.
- Code comments like "for future flexibility" or "in case we need to support..." with no concrete backing requirement.
- Abstract base classes with a single concrete subclass, unchanged for multiple release cycles.
- Unused parameters or return fields added "just in case."

## Common misuse

- Citing YAGNI to avoid designing a public API contract properly, then having to introduce a breaking v2 within a month because external consumers depended on the accidental shape of v1.
- Citing YAGNI to skip validating input types or writing error handling because "we don't need that yet" — a known operating condition (bad input, network failure) is not a hypothetical future feature.
- Using YAGNI as a blanket objection to any interface or seam, even ones needed purely for unit testing (e.g. refusing to inject a `Clock` and calling `Instant.now()` directly throughout, then being unable to test time-dependent logic at all).
