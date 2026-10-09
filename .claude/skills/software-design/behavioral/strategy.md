# Strategy

## Intent

Encapsulate a family of interchangeable algorithms behind a common interface so the caller can select one at runtime without knowing its implementation details.

## Problem

You have a piece of behavior that has multiple valid implementations (a sort order, a pricing rule, a validation policy, an auth mechanism) and the choice of which one to use is a caller decision, not something the object figures out on its own over time. Hardcoding the choice with conditionals forces every call site to know about every variant and duplicates the branching logic wherever the behavior is invoked.

## When to use

- You have 3+ variants of an algorithm that differ in implementation but share a signature (e.g., `compress(bytes) -> bytes` with gzip/zstd/none).
- The set of variants is expected to grow (new payment processors, new discount rules) and you want adding one to not touch existing code.
- You want to unit test each variant in isolation, independent of the code that selects it.
- Callers need to swap the algorithm per-call or per-configuration (e.g., per-tenant serialization format).
- You're passing a function/lambda as a constructor or method argument and it's grown enough state or complexity that a named class is clearer than a closure.

## When NOT to use

- You have exactly 2 stable variants that are never going to grow (e.g., ascending vs. descending). A boolean parameter or a single `if` is clearer than a `Comparator` hierarchy — the pattern's ceremony (interface + N classes + wiring) doesn't pay for itself.
- The "algorithm" is actually a sequence of state transitions tied to the object's lifecycle (e.g., an order moving through `PENDING → PAID → SHIPPED`). That's [State](state.md), not Strategy — see the distinction below.
- The variants share 90% of their logic and differ only in one small step. Template Method (or just a parameter/callback for that one step) is a better fit than duplicating the shared logic across N strategy classes.
- You're using Strategy purely to avoid a single `if/else` that will never have a third branch. This is over-engineering for the sake of "using a pattern."
- The language has first-class functions and the "strategy" is a pure, stateless function. A `Function<T, R>` parameter or method reference is simpler than a named interface + implementing classes, unless the strategy needs to be independently discoverable (e.g., via DI/Spring bean lookup) or carries its own configuration/state.

**Failure scenarios:**
1. A team builds `DiscountStrategy` with `PercentageDiscount` and `FixedAmountDiscount` — the only two discount types the business will ever have per their own product spec — then has to maintain an interface, a factory, and a Spring `@Qualifier` wiring for what amounts to `if (isPercentage) amount * rate else amount - flatValue`. Every change now touches three files instead of one `if`.
2. A `NotificationStrategy` interface is introduced for email vs. SMS, but six months later someone needs "send email AND SMS for urgent alerts" — the interface assumed mutual exclusivity, and now there's a `CompositeNotificationStrategy` bolted on to route around the original abstraction instead of just calling two methods.

## Structure

- **Strategy** (interface): declares the algorithm's operation signature.
- **ConcreteStrategy** (one class per variant): implements the operation.
- **Context**: holds a reference to a Strategy, delegates to it, doesn't know which concrete variant it holds.

```java
interface ShippingCostStrategy {
    Money calculate(Order order);
}

class FlatRateShipping implements ShippingCostStrategy {
    public Money calculate(Order order) { return Money.of(5.00); }
}

class WeightBasedShipping implements ShippingCostStrategy {
    public Money calculate(Order order) { return order.totalWeight().multiply(0.5); }
}

class Checkout {
    private final ShippingCostStrategy shippingCost;

    Checkout(ShippingCostStrategy shippingCost) { this.shippingCost = shippingCost; }

    Money total(Order order) {
        return order.subtotal().plus(shippingCost.calculate(order));
    }
}
```

## Consequences

**Benefits:**
- New variants added without touching `Context` or existing strategies (Open/Closed).
- Each variant is independently testable.
- Eliminates conditional logic that would otherwise be duplicated at every call site.
- Variants can be swapped at runtime (config-driven, per-request, per-tenant).

**Costs:**
- More types: one interface plus one class per variant, plus whatever wires them together (factory, DI, map lookup).
- Caller must know enough to pick the right strategy, or you need a second mechanism (factory, registry) to make that choice — which itself needs justification.
- Indirection makes single-step-through debugging slightly harder: you land in the interface, not the concrete implementation, until the debugger resolves the dynamic dispatch.
- If most variants share logic, you end up copy-pasting or reaching for Template Method anyway.

## Related patterns

- [State](state.md) — same UML shape (interface + implementations + a context holding a reference), but different intent: State's implementations trigger *their own* transitions to other states as part of the object's lifecycle; Strategy's implementations are chosen by the caller and don't switch each other out. If your "strategies" are calling `context.setStrategy(next)` internally, you've actually built State.
- [Template Method](template-method.md) — inverts the relationship: fixes the algorithm's skeleton and varies only specific steps via subclassing, rather than swapping the whole algorithm.
- [Factory Method](../creational/factory-method.md) — often used alongside Strategy to construct/select the right concrete strategy; solves a different problem (object creation) and isn't a substitute for Strategy itself.
- [Command](command.md) — also encapsulates a unit of behavior behind an interface, but Command is about *when and whether* to invoke an action (queueing, undo, logging), not about choosing *which algorithm* to run.

## Smells that suggest this pattern

- A method with a long `if/else` or `switch` on a type/enum where each branch computes the same kind of result via a different algorithm, and the enum is expected to grow.
- Multiple call sites duplicating the same type-dispatch logic to decide "which way to compute X."
- A class with a boolean or enum field whose sole purpose is to select behavior inside nearly every method.

## Common misuse

- Introducing a Strategy interface for a single implementation "in case we need another one later" (YAGNI) — wait for the second real variant before abstracting.
- Using Strategy where the real requirement is composition, not selection (e.g., "apply multiple discounts") — that's closer to Decorator or Chain of Responsibility.
- Wrapping a strategy's `execute()` around implicit mutable shared state, making strategies not actually interchangeable (swapping one in breaks invariants the others relied on) — defeats the purpose of substitutability.
