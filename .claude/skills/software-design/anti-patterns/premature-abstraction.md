# Premature Abstraction

## Intent

Introducing an interface, extension point, or generalized mechanism before there are enough real, concrete use cases to know what the abstraction should actually look like — designing for hypothetical future variation instead of observed variation.

## Problem

The abstraction is guessed at, not derived, so it usually doesn't fit the second real use case when it finally arrives — teams end up bending the new requirement to fit the existing interface (producing awkward parameters, unused methods, `null`/`Optional` flags to select behavior) or bypassing the abstraction entirely, leaving it as dead ceremony. Every reader now has to go through the interface to find the one real implementation, adding a navigation hop for zero payoff. Onboarding is slower because new engineers have to learn a generalized concept (`Strategy`, `Factory`, `Provider`) to understand code that has exactly one concrete behavior. Changes take longer because a "simple" edit now means touching the interface, its single implementation, and whatever wiring (DI config, factory, registry) connects them — three files for what would have been a one-file, one-method change.

## How it develops

1. A developer, having been burned before by a hardcoded value that later needed to vary (or having just read about a pattern), builds the *general* version from the start: an interface with one implementation "in case we need a second payment provider," a config-driven strategy selector for a rule that has exactly one rule today, a plugin registry for a feature with one plugin.
2. This feels like the responsible, forward-thinking choice — YAGNI is easy to violate with good intentions, because the abstraction genuinely *might* be needed, and "designing it in from the start" seems cheaper than retrofitting it later.
3. Nobody revisits the decision, because the code works and there's no forcing function (no second real use case has appeared) to reveal that the abstraction's shape was guessed, not derived.
4. When a genuine second use case does appear, it usually doesn't fit the guessed interface cleanly — the first implementation's assumptions (e.g., "a strategy always returns synchronously," "a discount is always a single flat rate") get violated by the second real case, and now the abstraction itself needs surgery, on top of implementing the new case.
5. Alternatively, the "second case" never arrives — the codebase carries an interface, a factory, and a DI binding for a concept that only ever had one implementation, indefinitely, as pure overhead that every future reader pays for.

The pattern-name version of this trap: reaching for [Strategy](../behavioral/strategy.md)/[Abstract Factory](../creational/abstract-factory.md)/[Observer](../behavioral/observer.md) because the problem superficially resembles the textbook example for the pattern, not because there's a real, current variability point.

## How to recognize it

- Interface-to-implementation ratio: an interface with exactly one implementing class, and no test double/mock that counts as a second "implementation" for a real reason (test doubles created purely to satisfy the interface's existence don't count as justification).
- Single-branch factories/registries: a `Factory`/`Registry`/`Provider` class whose selection logic (`switch`, map lookup) has exactly one case, or a config flag that has only ever been set to one value in every environment.
- Unused extension points: abstract methods, template method "hooks," or configurable strategy parameters that are always called with the same argument at every call site — `grep` for a class/interface name and check whether every constructor call passes the same concrete type.
- Generic type parameters with no actual polymorphic use: a class parameterized as `Repository<T>` used only ever as `Repository<Order>` anywhere in the codebase, with no near-term plan for a second type.
- "Just in case" naming: constructors, method names, or comments containing "in case we need," "for future," "extensible," "pluggable" attached to code with no current second use.
- High ratio of plumbing to logic: for a given concept, count files needed to trace one call end-to-end (interface, impl, factory, DI config, base class) versus files that contain actual business logic — a high plumbing-to-logic ratio for a single-variant concept is the signal.
- Churn pattern: the abstraction's interface changes shape (methods added/removed/renamed) shortly after the second real implementation is written — evidence the original design was speculative rather than derived from real cases.

## Remedy

- Inline the abstraction back to a concrete class/function when there's still only one implementation and no near-term plan for a second — this is a valid, low-risk refactor (most IDEs support "inline interface"/"collapse hierarchy" mechanically) and immediately removes a navigation hop and a file.
- Apply [YAGNI](../principles/kiss.md) as the default stance: build the concrete version first; introduce the interface/strategy/factory only when a second real, concrete requirement exists — not when it seems plausible.
- If the abstraction is already serving a real second case, but was clearly guessed at (evidenced by awkward bends to fit the second case), it's worth revisiting its shape now that two real data points exist, rather than continuing to force-fit future cases into the first guess.
- When genuinely unsure whether a second variant is coming "soon" (this sprint/quarter) versus "eventually," default to concrete and defer — the cost of retrofitting an abstraction once the second case actually lands is usually lower than the ongoing cost of carrying speculative ceremony that may never pay off.
- Where a real variability point does exist and is confirmed by ≥2 concrete cases, the standard patterns apply normally: [Strategy](../behavioral/strategy.md) for interchangeable algorithms, [Factory Method](../creational/factory-method.md)/[Abstract Factory](../creational/abstract-factory.md) for object creation that varies, [Template Method](../behavioral/template-method.md) when most logic is shared and only steps vary.
- Cost: inlining a premature abstraction is usually cheap (one interface, one impl, direct callers) — but if the abstraction has spread (multiple call sites depend on the interface type, it's part of a public API, or it's wired through DI in many places), removing it can itself be a multi-file change; weigh that removal cost against just leaving a harmless, unused-but-stable abstraction in place if it isn't actively causing confusion or breakage.

## When the "fix" is worse than the disease

- The abstraction was added deliberately to satisfy a real, known-but-not-yet-implemented requirement with a committed timeline (e.g., "the second payment provider integration is scheduled for next sprint, contract already signed"). This isn't speculation — it's staged work, and inlining it now just means redoing the abstraction in two weeks. The distinguishing question is "do we know a second case is coming, with real specifics" vs. "a second case seems plausible."
- Test-only "premature abstraction": an interface with one production implementation but a real second implementation that's a test double substituting for an expensive external dependency (DB, HTTP client, message queue). This is legitimate [Dependency Inversion](../principles/solid.md) for testability, not speculative generality — the "second implementation" existing purely for tests is a valid reason, provided the tests actually exercise meaningfully different behavior through it (not just a no-op stub nobody asserts against).
- Public library/API code, where "premature" is measured differently than in application code — a library with external consumers genuinely cannot add parameters or change signatures later without a breaking major version, so some amount of extensibility (config objects instead of parameter lists, extension points) that looks premature by internal-app standards is a reasonable hedge against the much higher cost of a breaking change post-release.
- Domain boundaries in a modular system (e.g., a Spring Modulith module's public interface) where the "single implementation today" is intentional insulation, not speculation — the interface exists so other modules depend on a stable contract rather than reaching into internals, even though there's only one implementation. This is [Facade](../structural/facade.md)/module-boundary design, not premature abstraction, because the payoff (decoupling, independent evolution, preventing [Circular Dependency](circular-dependency.md)) is being realized immediately, not hypothetically.

## Related patterns

- [Shotgun Surgery](shotgun-surgery.md) — the opposite failure mode; under-abstracting on the first or second duplication is shotgun surgery, over-abstracting before any real duplication exists is premature abstraction. The dividing line is usually "the third occurrence" — extract on the third real, confirmed-similar case, not the first.
- [Circular Dependency](circular-dependency.md) — see its "when the fix is worse than the disease" section for the mirror case: sometimes introducing a boundary (to fix a cycle) is itself premature if the two modules never evolve independently.
- [God Object](god-object.md) — the opposite extreme on the same axis; premature abstraction over-splits before it's warranted, a god object under-splits long after it's warranted.
- [YAGNI](../principles/kiss.md), [KISS](../principles/kiss.md) — the principles this anti-pattern violates.
- [Strategy](../behavioral/strategy.md), [Factory Method](../creational/factory-method.md), [Abstract Factory](../creational/abstract-factory.md) — legitimate once a real second variant exists; each file's own "When NOT to use" section covers the premature-application case for that specific pattern.

## Real-world example shape

```java
// Only one implementation exists, and no second is planned.
public interface DiscountStrategy {
    BigDecimal apply(BigDecimal price, Order order);
}

public class StandardDiscountStrategy implements DiscountStrategy {
    public BigDecimal apply(BigDecimal price, Order order) {
        return price.multiply(BigDecimal.valueOf(0.9)); // flat 10%, per current spec
    }
}

@Configuration
class DiscountConfig {
    @Bean
    DiscountStrategy discountStrategy() {
        return new StandardDiscountStrategy(); // only ever this one bean
    }
}

class Checkout {
    private final DiscountStrategy discountStrategy; // injected, always the same impl
    Checkout(DiscountStrategy discountStrategy) { this.discountStrategy = discountStrategy; }
}
```

An interface, one implementation, and a DI wiring layer for logic that is, in reality, `price.multiply(0.9)` — three files and one indirection hop standing in for one line.
