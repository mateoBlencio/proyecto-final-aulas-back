# State

## Intent

Let an object change its behavior when its internal state changes, by delegating state-specific behavior to separate state objects and letting the object appear to change its class.

## Problem

An object goes through a well-defined lifecycle (an order, a connection, a document review) and its behavior legitimately differs depending on which lifecycle stage it's in — some operations are valid in one state and invalid in another, and transitions between states follow rules. Modeling this with a single class full of `if (status == PENDING) ... else if (status == PAID) ...` scattered across every method means every new state or every new operation requires touching every method, and it's easy to leave a state/operation combination unhandled.

## When to use

- An object's behavior depends on a state field, and that dependency shows up in **multiple methods**, not just one.
- Transitions between states follow explicit rules (e.g., `PENDING → PAID` is legal, `SHIPPED → PENDING` is not) and you want those rules enforced in one place rather than trusted to callers.
- The number of states is likely to grow (new order statuses, new workflow steps) and each addition currently means editing a large switch in every method.
- You want state-specific data to only exist while in that state (e.g., a `RetryingState` holding a retry counter that doesn't need to exist in `IdleState`).

## When NOT to use

- You have 2-3 states that never grow and only one method actually branches on them. A single `if` or a small `switch` is clearer than a class hierarchy — the object-per-state ceremony isn't earning its keep.
- The "states" don't actually drive different behavior, just different data (e.g., a `status` field only used for display/filtering, never branched on in logic). That's just an enum, not a State pattern candidate.
- Transitions are simple and linear with no real invariants to enforce (e.g., a checklist that just goes step 1, 2, 3 in order with no branching, no invalid transitions, no per-step behavior). A `currentStep++` counter is enough.
- You need the caller to *choose* which behavior runs, rather than the object *transitioning itself*. That's [Strategy](strategy.md) — see the distinction below.

**Failure scenarios:**
1. A `TrafficLight` class gets a full `State` pattern (`RedState`, `YellowState`, `GreenState`, a `TrafficLightState` interface, a context) for what is a fixed 3-cycle rotation with no branching logic and no external triggers — a single `next()` method with a 3-entry array or `switch` would have been ten lines instead of five files.
2. A `Ticket` workflow gets modeled with State classes for `Open`, `InProgress`, `Resolved`, but the actual business logic is a handful of stable rules ("can't reopen after 30 days closed") that never change — five classes now exist to express two conditionals, and every bug fix requires jumping across five files to trace one transition.
3. State classes end up stateless and singleton-shared, but someone adds per-instance mutable data to one state class (e.g., a timestamp) assuming each object gets its own state instance — causing cross-request data corruption when state singletons are reused across objects, because the original design assumed states were interchangeable flyweights.

## Structure

- **Context**: holds a reference to the current State object; delegates state-dependent operations to it.
- **State** (interface): declares operations whose behavior varies by state.
- **ConcreteState** (one per state): implements the operations for that state, and triggers transitions by telling the Context to switch to a new State.

```java
interface OrderState {
    OrderState pay(Order order);
    OrderState ship(Order order);
}

class PendingState implements OrderState {
    public OrderState pay(Order order) { return new PaidState(); }
    public OrderState ship(Order order) { throw new IllegalStateException("must pay first"); }
}

class PaidState implements OrderState {
    public OrderState pay(Order order) { throw new IllegalStateException("already paid"); }
    public OrderState ship(Order order) { return new ShippedState(); }
}

class Order {
    private OrderState state = new PendingState();
    void pay() { state = state.pay(this); }
    void ship() { state = state.ship(this); }
}
```

## Consequences

**Benefits:**
- Each state's behavior and valid transitions live in one class — no scattered conditionals.
- Adding a new state means adding a new class, not editing every existing method (Open/Closed).
- Illegal operations for a given state are naturally impossible or throw clearly, instead of silently no-op-ing inside a missed `if` branch.
- Transition logic is explicit and traceable (`pay()` returns the next state, rather than mutating a flag deep in a shared method).

**Costs:**
- More classes than an enum + switch for simple lifecycles — real overhead if the lifecycle is small and stable.
- The set of legal transitions is implicit in the code (spread across each state's methods) unless you also draw/document the state machine explicitly.
- If state objects are shared singletons (common for stateless states), any accidental per-instance data leaks across contexts — a real bug class specific to this pattern.
- Debugging requires tracing which state's method actually ran, similar to Strategy's dynamic-dispatch cost.

## Related patterns

- [Strategy](strategy.md) — identical UML shape (interface + implementations + a context holding a reference), different intent. **State**: the object being modeled transitions between the implementations itself, as part of its own lifecycle, and past behavior determines what operations are even valid now. **Strategy**: the caller picks the implementation up front for a given call; the implementations don't transition into each other, and there's no notion of "invalid operation for this variant."
- [Template Method](template-method.md) — fixes a step sequence in a base class; State instead swaps the whole behavior set when the object's internal state changes.
- [Memento](memento.md) — often paired with State when you need to snapshot/restore an object's state history (e.g., undo a transition), separate from the state-dependent behavior itself.
- [Observer](observer.md) — commonly used alongside State to notify interested parties when a transition happens, without State itself owning that notification logic.

## Smells that suggest this pattern

- A `status`/`state` enum field referenced in `if`/`switch` statements across 3+ methods of the same class.
- Comments like `// only valid when status == SHIPPED` guarding logic instead of the type system preventing the call.
- Bug reports of the form "we called X while in state Y and it corrupted data" — a sign invalid transitions aren't being rejected structurally.
- A boolean flag soup (`isPaid`, `isShipped`, `isCancelled`) standing in for what's actually a single mutually-exclusive state.

## Common misuse

- Applying State to what's really just a display/reporting flag with no differing behavior — that's over-engineering an enum into a class hierarchy.
- Using State but leaving the actual transition logic in the Context (a big `switch` in `Context.transition(event)`) rather than in the State classes — this keeps all the downsides (many classes) without the benefit (transition logic isn't actually distributed/encapsulated).
- Making State classes stateful singletons shared across many Context instances without realizing it — a classic concurrency/correctness bug when one state accumulates per-instance data it shouldn't.
