# Observer

## Intent

Let a subject notify a dynamic set of interested parties when its state changes, without the subject knowing anything concrete about who those parties are.

## Problem

One object's state change needs to trigger reactions in other, unrelated objects (update a UI, invalidate a cache, log an audit trail, send a notification) and the set of reactions may grow over time or vary by deployment. Hardcoding every reaction as a direct call from the subject couples it to every consumer and forces the subject to change every time a new reaction is needed.

## When to use

- Multiple, independent parts of the system need to react to the same state change, and that set of reactors can grow without the subject needing to know about it.
- You want to decouple the component that detects/produces an event from the components that react to it (publisher doesn't need to import/depend on subscriber implementations).
- The relationship is fundamentally "one change, several independent reactions" rather than "one change, one specific next step" (which would just be a direct call or return value).
- In-process, synchronous fan-out where all observers must run before the triggering call returns (e.g., validation observers that can veto a change).

## When NOT to use

- There's exactly one consumer of the event, now and for the foreseeable future. A direct method call is simpler, easier to step through in a debugger, and doesn't need an interface plus registration machinery.
- The reactions must happen in a specific, guaranteed order with data flowing between them, or a later step depends on an earlier one's result. Observer implies independence between observers; if B needs A's output, you actually want a plain sequential call chain or a pipeline, not fan-out notification.
- You need cross-process or cross-service delivery, durability, retries, or ordering guarantees. That's a message broker / [Pub-Sub](../integration/pub-sub.md) concern, not in-process Observer — reusing Observer's shape for that will silently drop events on crash/restart with no delivery guarantee.
- The chain of "who reacts to what" has grown deep enough (observer triggers a change that notifies another observer, which triggers another change...) that you can no longer predict what a state change will cascade into by reading the code. This needs to be flattened back into explicit calls or replaced with a mediator.

**Failure scenarios:**
1. An `Order.setStatus(SHIPPED)` fires an observer that updates inventory, which fires its own observer that recalculates a shipment forecast, which fires an observer that invalidates a caching layer that a completely unrelated reporting module depends on. A bug in the reporting numbers takes a full afternoon to trace back to the original `setStatus` call three notification hops away — nobody could `grep` for "who calls this" because the call happened via a listener registered in a different module's startup code.
2. A synchronous `PropertyChangeListener` on a UI model is used as a substitute for a straightforward two-line method call between two classes that will only ever have this one relationship — adding an interface, a registration call, and a listener implementation for what's really `a.onChange(() -> b.refresh())` used exactly once, making a trivial call site require jumping through indirection to understand.
3. An observer registered once at startup is never unregistered; the subject holds a strong reference to it, and the object owning the observer is supposed to be garbage collected/disposed but leaks for the process lifetime — a classic Observer memory-leak bug, especially in UI frameworks and long-lived singletons.

## Structure

- **Subject**: maintains a list of observers, exposes subscribe/unsubscribe, and notifies them on change — without depending on concrete observer types.
- **Observer** (interface): declares the update hook.
- **ConcreteObserver**: implements the reaction.

```java
interface OrderObserver {
    void onStatusChanged(Order order, OrderStatus newStatus);
}

class Order {
    private final List<OrderObserver> observers = new ArrayList<>();
    private OrderStatus status;

    void subscribe(OrderObserver o) { observers.add(o); }

    void setStatus(OrderStatus newStatus) {
        this.status = newStatus;
        for (OrderObserver o : observers) o.onStatusChanged(this, newStatus);
    }
}

class InventoryUpdater implements OrderObserver {
    public void onStatusChanged(Order order, OrderStatus s) {
        if (s == OrderStatus.SHIPPED) inventory.decrement(order.items());
    }
}
```

## Consequences

**Benefits:**
- Subject and observers are decoupled — subject only knows the interface, not concrete reactor classes.
- New reactions plug in without modifying the subject (Open/Closed).
- Supports fan-out to an arbitrary, runtime-configurable number of listeners.

**Costs:**
- Notification order is often unspecified or implementation-dependent; code that implicitly relies on ordering is fragile.
- Cascading notifications (observer triggers another subject's notification) make control flow hard to trace statically — a real debugging cost, not hypothetical.
- Exception handling gets murky: does one observer throwing stop the others, or should the subject swallow and continue? This must be an explicit decision, not an accident.
- Memory leaks from forgotten unsubscription are a known, recurring failure mode, particularly in long-lived subjects or UI component trees.
- Synchronous Observer blocks the subject's calling thread for the total time of all observers combined — a single slow observer degrades the whole notification.

## Related patterns

- [Mediator](mediator.md) — centralizes what would otherwise be many-to-many Observer relationships into one coordinator; reach for Mediator when Observer chains get tangled enough that no single place shows "what happens after X."
- [Pub-Sub](../integration/pub-sub.md) — the distributed/cross-process analogue of Observer, with a broker, delivery guarantees, and (usually) async, durable, ordered-or-not delivery. Observer is in-process and synchronous by default; don't reach for Observer's plain shape when you actually need the broker's guarantees.
- [Command](command.md) — can be used to make the "reaction" itself a first-class, queueable, undoable object rather than a direct interface callback, when observers need to be deferred or logged rather than run immediately.
- [State](state.md) — often notifies observers on transition, but State's core concern (what behavior applies now) is separate from Observer's (who gets told about it).

## Smells that suggest this pattern

- A method that, besides its main job, also directly calls into 3+ unrelated modules to "let them know" something changed — each call is a hardcoded dependency that Observer would decouple.
- Every time a new "also do X when Y happens" requirement comes in, the same core method gets a new line added to it — signals the reaction list should be externally registrable instead of hardcoded.
- Polling loops that repeatedly check "did X change?" instead of being told when it did.

## Common misuse

- Using Observer for a simple, synchronous, single-consumer call chain where a direct method call would be just as decoupled (an interface at the call site achieves that) but far easier to read and step through — Observer adds registration ceremony without adding real decoupling value when there's only ever one listener.
- Letting observer chains cascade multiple levels deep, turning a single state change into an untraceable ripple across the codebase — the root cause of "nobody knows why this fires" bugs.
- Using Observer/listener registration as a workaround for circular dependencies between two classes, instead of fixing the actual layering problem that created the cycle.
