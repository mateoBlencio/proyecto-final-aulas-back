# Mediator

## Intent

Centralize how a set of objects interact into one coordinator object, so the objects reference the mediator instead of each other directly, replacing many-to-many coupling with many-to-one.

## Problem

When a group of objects needs to communicate (UI widgets reacting to each other, workflow steps triggering one another), letting each object hold direct references to every other object it needs to talk to produces a tangled web of dependencies — N objects can end up with O(N²) potential connections, and adding one more participant means touching every existing object that needs to know about it. No single place shows the overall interaction logic; it's smeared across all the participants.

## When to use

- A group of objects (typically UI components, or workflow participants) needs to coordinate, and the coordination logic itself is non-trivial (multiple conditions, ordering, cross-cutting rules).
- You're seeing direct references between many pairs of objects that each need to know about several others, and adding a new participant currently means editing several existing classes.
- You want the interaction/coordination logic to live in one place that can be read top-to-bottom to understand "what happens when X changes," rather than reconstructed by tracing calls across many classes.
- The participants should be reusable/testable independently, without dragging in the whole web of collaborators they'd otherwise reference directly.

## When NOT to use

- Only two or three objects are involved and their interaction is simple and stable (e.g., "when checkbox A is unchecked, disable field B"). A Mediator class for two participants is often more code than the direct reference it replaces.
- The "mediator" ends up as a thin pass-through that just forwards calls 1:1 between participants with no actual coordination logic — at that point it's an unnecessary hop, not a decoupling win.
- The interactions are naturally hierarchical/one-directional (a parent orchestrating well-defined children) — plain composition and direct calls from the parent are simpler than introducing a peer-level Mediator.
- The mediator is at risk of becoming a God Object: as more participants and rules pile in, all system knowledge concentrates into one class that grows without bound and becomes the single hardest thing in the codebase to change safely.

**Failure scenarios:**
1. A large form UI's `FormMediator` starts with five widgets and clean coordination logic, but over two years accumulates rules for forty widgets, each with several conditional branches referencing widget-specific details — the Mediator has become a 3,000-line God Object that everyone is afraid to touch, exactly the kind of centralized coupling the pattern was meant to avoid, just relocated rather than removed.
2. A Mediator is introduced for two chat participants that only ever exchange messages directly — `sendMessage(from, to, text)` routed through a `ChatMediator` singleton adds a hop and a lookup for a relationship that was already simple and bidirectional; the abstraction pays for itself only once a third, fourth, and fifth participant type shows up needing shared broadcast/routing rules.

## Structure

- **Mediator** (interface): declares communication methods used by Colleagues.
- **ConcreteMediator**: implements the coordination logic, holds references to the Colleagues it coordinates.
- **Colleague**: holds a reference to the Mediator (not to other Colleagues), and calls it instead of calling peers directly.

```java
interface DialogMediator {
    void notify(Component sender, String event);
}

class LoginDialog implements DialogMediator {
    private final Checkbox rememberMe = new Checkbox(this);
    private final TextField username = new TextField(this);
    private final Button submit = new Button(this);

    public void notify(Component sender, String event) {
        if (sender == rememberMe && event.equals("toggled")) {
            username.setPersistenceEnabled(rememberMe.isChecked());
        }
        if (sender == submit && event.equals("clicked")) {
            submit.setEnabled(!username.text().isEmpty());
        }
    }
}
```

## Consequences

**Benefits:**
- Replaces many-to-many object references with many-to-one, reducing the number of connections that need to change when a participant is added or removed.
- Coordination logic is readable in one place instead of scattered across every participant's code.
- Colleagues become simpler and more independently reusable/testable since they no longer hold references to each other.

**Costs:**
- The Mediator itself can grow unboundedly complex as more participants and rules are added, becoming a God Object and a single point of change-risk (see failure scenario 1) — the coupling didn't disappear, it moved and concentrated.
- Debugging requires understanding the Mediator's full rule set to predict what a given event triggers, rather than following a direct call — a form of the same traceability cost Observer has.
- Overkill for a small, stable number of participants with simple interactions — the indirection isn't free.

## Related patterns

- [Observer](observer.md) — often the mechanism Colleagues use to notify the Mediator of events (Colleague fires an event, Mediator observes it); the difference is that Observer's subject doesn't coordinate cross-observer logic, while Mediator explicitly centralizes decisions about what should happen across multiple participants in response.
- [Facade](../structural/facade.md) — both provide a single point of contact reducing coupling to a subsystem, but Facade simplifies a *one-directional* call into a subsystem (client calls facade, facade calls subsystem, no coordination of peer-to-peer interaction); Mediator coordinates *bidirectional*, ongoing interaction among peers that also call back into the mediator.
- [Chain of Responsibility](chain-of-responsibility.md) — routes a request through a sequence of independent handlers; Mediator instead centralizes explicit coordination logic in one object that knows about all participants, rather than passing the request along a chain of handlers unaware of each other.

## Smells that suggest this pattern

- A set of classes each holding direct references to several sibling classes, where adding a new sibling means editing multiple existing classes to wire it in.
- UI event handlers that directly reach into other widgets/components' internals to update them in response to a change, duplicated across multiple handlers for related widgets.
- Coordination logic ("if A changes, update B, unless C is also true, in which case update D instead") duplicated or inconsistently implemented across several participant classes.

## Common misuse

- Introducing a Mediator for two participants with a simple, stable relationship — the direct reference it replaces was clearer (see failure scenario 2).
- Letting the Mediator accrete every cross-cutting rule in the system without bound until it becomes a God Object nobody can safely modify — needs to be split (e.g., by feature area) once it grows past a manageable size, the same way any oversized class should be.
- Using Mediator as a dumping ground for business logic that doesn't actually involve coordinating multiple objects, just because "the mediator is already there."
