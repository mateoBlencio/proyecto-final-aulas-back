# Facade

## Intent

Provide a single, simplified interface to a set of interfaces in a subsystem, making the common cases easy to use without exposing the subsystem's internal complexity.

## Problem

Client code needs a subsystem made of several interacting classes with ordering requirements, error handling, and wiring knowledge. Without a facade, every caller re-implements that sequencing and becomes tightly coupled to the subsystem's internals — any internal refactor breaks every caller.

## When to use

- The subsystem is genuinely complex (multiple classes, ordering requirements, cross-class error handling) and most callers only need a handful of common, high-level operations.
- You want callers decoupled from subsystem internals so the subsystem can be refactored without breaking them.
- You want a clear entry point / API boundary for a module — e.g., a service layer in front of several repositories, or the public surface of a bounded context in a modulith.
- You're introducing a third-party SDK made of many interacting classes and want to constrain how the rest of the codebase touches it to one narrow surface.
- You're incrementally replacing a legacy subsystem and want callers to depend on a stable facade while the internals are swapped out underneath it (strangler-fig style migration).

## When NOT to use

- The subsystem is already simple — one or two classes with an already-clean interface. An extra Facade class here is pure ceremony.
- The facade ends up exposing every operation of the subsystem 1:1. At that point it isn't simplifying anything — it's a second copy of the subsystem's full API surface with an extra hop (see Common misuse).
- **Failure scenario 1:** an `OrderFacade` grows to 40 public methods, one per underlying subsystem operation, each delegating without any real coordination or simplification. Callers now depend on the facade *and* still need to understand subsystem semantics to use it correctly — it's a God Object with extra indirection, not a simplification.
- **Failure scenario 2:** introducing a Facade in front of a single, already well-designed service class "for consistency." Every method forwards 1:1 with zero simplification, zero decoupling benefit, and one more hop to trace during debugging.

## Structure

`Facade` holds references to the subsystem classes and exposes a small number of high-level methods that coordinate calls across them. Subsystem classes remain independently accessible for callers who need finer-grained control.

```java
class OrderFacade {
    private final InventoryService inventory;
    private final PaymentService payment;
    private final ShippingService shipping;

    OrderFacade(InventoryService inventory, PaymentService payment, ShippingService shipping) {
        this.inventory = inventory;
        this.payment = payment;
        this.shipping = shipping;
    }

    // Coordinates three subsystem calls in the order this operation requires.
    void placeOrder(Order order) {
        inventory.reserve(order.items());
        payment.charge(order.customerId(), order.total());
        shipping.schedule(order);
    }
}
```

## Consequences

**Benefits:** reduces coupling between client and subsystem; simplifies the common use cases; provides a clear boundary layer, useful at module/package edges; subsystem internals can evolve without breaking facade clients.

**Costs:** can become a God Object / dumping ground if scope isn't actively managed; adds a layer that must be kept in sync as the subsystem changes; doesn't prevent callers from bypassing it and reaching into the subsystem directly, unless enforced (e.g., package-private subsystem classes, module boundaries); callers needing fine-grained control the facade doesn't expose either fall back to the subsystem directly (undermining the boundary) or push the facade toward exposing everything (undermining the simplification).

## Related patterns

- [Adapter](adapter.md) — distinguish by cardinality and purpose: Adapter reshapes *one* existing interface to match what a client expects (1:1); Facade defines a *new*, simpler interface over *multiple* subsystem classes (many:1).
- [Mediator](../behavioral/mediator.md) — Mediator centralizes communication *between* subsystem components (peer-to-peer coordination internal to the subsystem); Facade simplifies access *from outside* into the subsystem (client-facing, one direction).
- [Abstract Factory](../creational/abstract-factory.md) — can be used alongside Facade to also hide subsystem object creation, not just method coordination.

## Smells that suggest this pattern

- Client code that must call five or more subsystem methods in a specific order to accomplish one logical operation.
- The same setup/teardown sequence copy-pasted across multiple call sites.
- A module or package with many exported types where callers only ever exercise a handful of top-level operations.
- Bug fixes for "forgot to call X before Y" keep recurring across different callers of the same subsystem.
- Onboarding new engineers requires walking them through a specific multi-class call sequence just to perform one common task.

## Common misuse

- A Facade that grows into a God Object exposing the entire subsystem's API 1:1, defeating the "simplified" part of its own intent.
- Using Facade as the nominal access path while still leaking subsystem types through its method signatures (return types, exceptions) — callers remain coupled to subsystem internals regardless of the facade.
- Layering a Facade over a Facade over a Facade with no simplification happening at any level.
