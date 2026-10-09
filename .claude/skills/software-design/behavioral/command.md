# Command

## Intent

Turn a request (an action plus its parameters) into a standalone object, so it can be queued, logged, undone, or handed to code that doesn't know what the action actually does.

## Problem

You need to decouple the thing that *triggers* an action (a UI button, an API endpoint, a scheduler) from the thing that *knows how to perform* it, and you need to treat "do this thing" as data you can store, pass around, delay, retry, or reverse. A direct method call can't be queued, logged generically, or undone — it executes immediately and is gone.

## When to use

- You need to queue requests for later/async execution (job queues, task schedulers, deferred UI actions).
- You need undo/redo — each Command can store what it needs to reverse itself.
- You need to log or persist a history of operations performed (audit trail, event sourcing style replay), independent of what the operations were.
- You want to parameterize a caller with an action to take without the caller knowing what that action does (a generic "run this on click" button, a retry mechanism that just re-invokes a stored command).
- You need to support macro/composite operations — bundling several commands into one that executes them in sequence.

## When NOT to use

- The action is invoked once, synchronously, and nothing needs to queue it, log it generically, or undo it. Wrapping a single direct call in a Command object (`interface Command { void execute(); }` with one implementation) adds a class and an interface for no behavior the direct call didn't already have.
- Your language has first-class functions/lambdas and you don't need undo, serialization, or queuing metadata — a `Runnable`/`Supplier`/closure is a lighter-weight command that doesn't need a named class per action.
- You're using Command as a way to avoid naming what an operation does — turning `orderService.cancel(orderId)` into `new CancelOrderCommand(orderId).execute()` for a single call site with no queuing, undo, or logging need is indirection without payoff.

**Failure scenarios:**
1. A REST controller wraps every single service call — `GetUserCommand`, `UpdateUserCommand`, `DeleteUserCommand` — each with an `execute()` method invoked exactly once inline, no queue, no undo, no logging beyond what a normal service call already gets. The result is triple the file count of a plain controller-to-service call, and every new endpoint requires a new Command class before anyone can even see what it does.
2. Undo is bolted onto Commands that were designed for a fire-and-forget job queue — the `undo()` methods are written but never actually tested or invokable from any UI path, so they silently rot and become incorrect (they no longer actually reverse the current `execute()` logic after several unrelated changes), creating false confidence that undo works.

## Structure

- **Command** (interface): declares `execute()` (and optionally `undo()`).
- **ConcreteCommand**: binds a specific receiver and parameters, implements execute/undo by delegating to the receiver.
- **Invoker**: holds/queues/triggers commands without knowing their concrete type.
- **Receiver**: the object that actually performs the work; the Command is a thin binding to it.

```java
interface Command {
    void execute();
    void undo();
}

class AddItemCommand implements Command {
    private final Cart cart;
    private final Item item;

    AddItemCommand(Cart cart, Item item) { this.cart = cart; this.item = item; }
    public void execute() { cart.add(item); }
    public void undo() { cart.remove(item); }
}

class CommandHistory {
    private final Deque<Command> history = new ArrayDeque<>();

    void run(Command cmd) { cmd.execute(); history.push(cmd); }
    void undoLast() { if (!history.isEmpty()) history.pop().undo(); }
}
```

## Consequences

**Benefits:**
- Decouples invoker from receiver — the invoker (queue, button, scheduler) only knows the `Command` interface.
- Enables undo/redo, logging, and macro-commands as natural extensions, rather than bolted-on special cases.
- Requests become data: they can be serialized, persisted, retried, or sent across a boundary.
- Adding a new action means adding a new Command class, not modifying the invoker.

**Costs:**
- One class per action if going the classic OO route — a large API surface ends up with a large number of tiny Command classes; a lambda-based/functional command reduces but doesn't eliminate this.
- `undo()` correctness is easy to get wrong and easy to leave untested, since it's only exercised on the undo path, which is often less traveled than execute.
- Indirection cost: reading `invoker.run(command)` doesn't tell you what happens without navigating to the concrete Command class, unlike a direct, named method call.
- If commands carry mutable receiver references, queuing them for deferred execution can execute against stale state unless that's explicitly designed for.

## Related patterns

- [Chain of Responsibility](chain-of-responsibility.md) — both wrap "an action to take" as an object, but Command represents *one* fully-specified action bound to a receiver and invoked by an Invoker that doesn't interpret it; Chain of Responsibility is about *routing* a request through a sequence of handlers until one (or several) decides to handle it — the request's destination is unresolved until it travels the chain, whereas a Command's destination (its receiver) is fixed at construction.
- [Pub-Sub](../integration/pub-sub.md) / [Observer](observer.md) — differ in cardinality and intent: Command is typically one invoker driving one receiver through one action object; Observer/Pub-Sub is one event fanning out to many independent, decoupled subscribers who don't know about each other. Don't reach for a generic event bus when what you actually have is a single, specific action to perform — that's a Command (or just a method call) not a published event.
- [Memento](memento.md) — often paired with Command for undo: instead of (or in addition to) an explicit `undo()` method, a Command can capture a Memento of prior state and restore it.
- [Template Method](template-method.md) — can define the skeleton of `execute()` shared by several Commands (e.g., "validate, then do work, then log") with the receiver-specific step overridden.

## Smells that suggest this pattern

- A giant `switch (actionType)` in a controller/dispatcher that maps request types to a chunk of inline logic each — a candidate for one Command class per case.
- Ad hoc "undo the last thing" logic implemented as a stack of manually inverted operations scattered through the codebase rather than paired with the operation that created the need to undo it.
- A job/task queue storing loosely-typed dictionaries of `{type: string, params: {...}}` that get interpreted by a big dispatch function — this is Command without the type safety; formalizing it as Command objects (or a sealed type per action) catches mismatched params at compile time.

## Common misuse

- Wrapping every service method in a same-shaped `XCommand` class purely for architectural symmetry, with no queuing, undo, retry, or logging need — pure ceremony, no payoff (see failure scenario 1).
- Building `undo()` implementations that are never exercised by any real code path, letting them silently drift out of sync with `execute()` until the first real use fails.
- Using Command where a plain callback/lambda would do, in languages where functions are first-class values, purely out of habit from languages without them.
