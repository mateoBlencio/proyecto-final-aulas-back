# Adapter

## Intent

Convert the interface of an existing class into another interface clients expect, letting classes with incompatible interfaces work together without modifying either side.

## Problem

You have working code (a class, a third-party library, a legacy module) whose interface doesn't match what your calling code expects, and you can't or shouldn't change the source — it's external, generated, or used elsewhere in a way you can't break.

## When to use

- Integrating a third-party library whose API shape doesn't match the interface your application code is written against.
- Making legacy code work with new code during an incremental migration, without touching the legacy implementation.
- Unifying several existing classes with different interfaces behind one interface your client code already depends on.
- Wrapping a generated client (OpenAPI/gRPC stub) to match an internal port/interface your domain code expects.
- Swapping one vendor for another (e.g., changing payment processors) where both vendors' SDKs need to satisfy the same internal `PaymentGateway` port, so the swap is invisible to callers.
- Testing code that depends on a third-party class that's hard to mock directly — adapt it behind your own interface, then mock that interface in tests.

## When NOT to use

- You own both interfaces and can simply change one to match the other. Adapter is a workaround for code you can't change, not a default architectural layer.
- The mismatch is trivial — a single renamed method or reordered argument. A direct call-site conversion is simpler than a whole class.
- You notice adapters wrapping other adapters. That's a sign the interface design itself is wrong; fix it upstream instead of stacking indirection.
- You're tempted to wrap your own DTOs into your own domain objects over and over via ad hoc adapters, when the real fix is making the DTOs and domain types compatible from the start (e.g., via a mapping library or just better initial design).

## Structure

`Target` is the interface the client expects. `Adaptee` is the existing class with the incompatible interface. `Adapter` implements `Target` and translates calls to `Adaptee` (object adapter, via composition — preferred in Java since there's no multiple inheritance).

```java
interface PaymentGateway {              // Target
    void charge(String customerId, long amountCents);
}

class LegacyBillingSdk {                // Adaptee — can't modify
    void submitTransaction(String acct, double amountDollars) { /* ... */ }
}

class LegacyBillingAdapter implements PaymentGateway {
    private final LegacyBillingSdk sdk;

    LegacyBillingAdapter(LegacyBillingSdk sdk) { this.sdk = sdk; }

    @Override
    public void charge(String customerId, long amountCents) {
        sdk.submitTransaction(customerId, amountCents / 100.0);
    }
}
```

## Consequences

**Benefits:** decouples client code from the adaptee's interface; keeps the adaptee's source untouched (safe for third-party/legacy code); each adapter has a single, clear translation responsibility; enables reuse of existing code that would otherwise be incompatible.

**Costs:** one more class per adaptation; the mismatch it hides may itself indicate a deeper design problem worth fixing at the source; a long chain of adapters wrapping adapters becomes hard to trace and debug; runtime cost of an extra indirection layer, usually negligible but not zero on hot paths; if the adaptee's semantics don't map cleanly onto the target interface (different error models, different concurrency guarantees), the adapter can paper over real behavioral mismatches instead of surfacing them.

## Related patterns

- [Facade](facade.md) — Facade defines a *new*, simpler interface over *multiple* subsystem classes; Adapter reshapes *one* existing interface to match what a client already expects. Different cardinality and different purpose (simplify vs. reconcile).
- [Decorator](decorator.md) — Decorator preserves the component's interface and adds behavior; Adapter changes the interface and doesn't add behavior.
- [Bridge](bridge.md) — Bridge is designed upfront so an abstraction and its implementation can vary independently; Adapter is applied after the fact to reconcile two interfaces that weren't designed together.

## Smells that suggest this pattern

- Call sites littered with manual field-by-field translation between an external type and an internal type.
- Repeated conditional logic branching on which vendor/library API shape you're talking to.
- Domain or application layer code directly importing and calling third-party SDK types.
- The same translation snippet (unit conversion, date format, null-handling) copy-pasted at every call site that touches the external type.
- Tests mock a third-party SDK class directly rather than a narrow interface you control.

## Common misuse

- Using an "Adapter" to add new functionality that the wrapped class doesn't have — that's Decorator's job, not Adapter's. Adapter only reshapes the interface.
- Writing a full Adapter class + interface pair for a class you fully own and control, when a simple rename or refactor of the original class would remove the mismatch entirely.
- A "God Adapter" that wraps an entire external SDK exposing every single method 1:1 with no real translation happening — if there's no reshaping or coordination logic, you don't need a class, just use the SDK directly.
