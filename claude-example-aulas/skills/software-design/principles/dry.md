# DRY (Don't Repeat Yourself)

## Intent

Every piece of knowledge — a business rule, a policy, a fact about the domain — should have a single, authoritative representation in the system.

## Problem

Duplicated *knowledge* (not duplicated text) means every future change to that knowledge requires finding and updating every copy. Miss one and the system silently disagrees with itself — two places compute "is this order eligible for a discount" with slightly different logic after one gets patched and the other doesn't. DRY is about eliminating that class of drift, not about text length.

## When to use

- The same business rule (tax calculation, eligibility check, validation regex) is copy-pasted in two or more places and has already drifted once (one copy got a bugfix the other didn't).
- A magic number or string (a status code, a config key, a URL) is hardcoded in multiple files and changing it means grepping the whole repo.
- Two code paths encode the same domain invariant using different logic that happens to produce the same answer today.

## When NOT to use

- Two pieces of code that look structurally similar right now but represent **different domain concepts** that happen to coincide today. Example: `validateShippingAddress` and `validateBillingAddress` both check "street, city, zip non-empty" today — merging them into one `validateAddress` couples two business rules that will diverge (shipping needs a delivery-window check, billing needs a tax-jurisdiction check) and the shared function turns into an `if (type == SHIPPING)` mess.
- Test code: asserting the same setup in five tests is not duplication of knowledge, it's independent verification. A shared "DRY" test helper that all five tests call often means one bug now fails silently in all five instead of pinpointing which one broke.
- Coincidental duplication under three lines (two similar-looking validation checks, two similar loops) — abstracting these usually creates a helper with a boolean flag or three parameters just to cover both cases, which is worse than the duplication it removed.
- Cross-service or cross-module duplication where extracting a shared library creates a deployment/versioning coupling between two teams that would otherwise ship independently — the coordination cost can exceed the duplication cost.

## Structure

Before (rule duplicated, will drift):
```java
// OrderService
if (order.getTotal().compareTo(new BigDecimal("100")) > 0) applyFreeShipping(order);

// CartService
if (cart.getTotal().compareTo(new BigDecimal("100")) > 0) showFreeShippingBanner();
```

After (single source of truth):
```java
class ShippingPolicy {
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("100");
    boolean qualifiesForFreeShipping(BigDecimal total) {
        return total.compareTo(FREE_SHIPPING_THRESHOLD) > 0;
    }
}
```

## Consequences

- **Benefits**: one change point per rule, no silent drift between copies, the codebase documents where a piece of domain knowledge actually lives.
- **Costs**: the extracted abstraction adds a layer of indirection — a reader now has to jump to `ShippingPolicy` instead of reading the check inline. If the abstraction is wrong (see Common misuse), every future change to either use case has to fight the shared code instead of being a local edit.

## Related patterns

- [solid.md](solid.md) (SRP) — a well-scoped abstraction extracted for DRY should still have one reason to change.
- [Template Method](../behavioral/template-method.md), [Strategy](../behavioral/strategy.md) — mechanisms for sharing behavior without collapsing distinct policies into one branch-heavy function.
- [Premature Abstraction](../anti-patterns/premature-abstraction.md) — the standard failure mode of over-applying DRY.
- [kiss.md](kiss.md) — tension partner; sometimes the simplest thing is to repeat three lines.

## Smells that suggest this pattern

- A bugfix applied in one file and, weeks later, the same bug reported against a near-identical block elsewhere.
- Grep for a literal (a URL, a limit, a status string) returns five hits that are all supposed to mean the same thing.
- Two functions with near-identical bodies except for one hardcoded value that could be a parameter.

## Common misuse

- Merging two functions that look alike today into one parameterized function with `if (mode == A)` branches — this is duplication converted into a conditional, not eliminated; when the two cases diverge further, the shared function accretes more flags and becomes unreadable.
- Extracting a shared utility class across bounded contexts (e.g. a shared `Customer` DTO used by both the billing module and the marketing module) because the fields look the same, coupling two domains that should be allowed to evolve independently.
- DRY-ing test setup so aggressively that a single shared fixture obscures what each test actually exercises, and a change to the fixture silently changes the meaning of a dozen unrelated tests.
