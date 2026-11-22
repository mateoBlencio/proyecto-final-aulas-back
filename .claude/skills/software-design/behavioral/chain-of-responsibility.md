# Chain of Responsibility

## Intent

Pass a request along a sequence of candidate handlers until one of them handles it, without the sender knowing in advance which handler will (or whether any will).

## Problem

A request needs to be handled by one of several possible handlers, but the sender shouldn't be coupled to a specific handler or to the logic of picking one. Hardcoding "try handler A, if it can't, try B, if it can't, try C" as an if/else chain inside the sender couples the sender to every handler's existence and to the order between them, and makes adding/removing/reordering handlers a change to the sender itself.

## When to use

- A request can be handled by one of several handlers, and which one applies depends on runtime conditions the sender shouldn't need to know about (e.g., support ticket routing by severity/team, middleware pipelines, validation rules where the first failing rule wins).
- You want to add, remove, or reorder handlers without touching the sender or the other handlers (e.g., a plugin-style pipeline of interceptors).
- Multiple handlers might each want a chance to act on the request (logging, auth, then business logic) before it's considered "done," and each handler decides independently whether to pass it along.
- The set of handlers, or their order, is configured at startup/deploy time rather than fixed at compile time.

## When NOT to use

- There are 2-3 fixed handlers, the order never changes, and it's unlikely to grow. A straightforward sequence of `if` checks or a small ordered list of function calls is clearer than building a linked-handler chain with `setNext()` wiring.
- Exactly one handler will always handle the request based on a value you can look up directly (e.g., a type/enum key). A `Map<Type, Handler>` dispatch table is simpler and O(1) instead of walking a chain — there's no "responsibility" being distributed, just routing.
- You need every handler to run and see the *original* request with guaranteed full visibility of what earlier handlers did — that's more naturally a pipeline/decorator stack with explicit composition, not a chain where handlers may silently stop propagation.
- Debuggability matters and the chain is long or dynamically assembled at runtime from configuration — tracing "which handler actually processed this, and why did it stop propagating there" becomes a live debugging exercise instead of a `grep`.

**Failure scenarios:**
1. An HTTP middleware chain grows to 15 handlers assembled dynamically from a config file, each conditionally calling (or not calling) `next()`. A production bug where auth headers stop being validated is eventually traced to handler #7 silently swallowing the chain because of an early `return` added six months prior for an unrelated fix — nothing in the code makes it obvious that a handler can silently terminate the whole chain without the sender knowing.
2. A validation library assigns one handler per rule and chains them, but two rules need to see *each other's* results to decide (a discount rule needs to know if a shipping rule already invalidated the order) — the chain's contract of "each handler only sees the request, decides independently" doesn't support that, forcing awkward shared mutable state smuggled through the request object.
3. A rules chain for loan approval accretes to 20+ handlers, each with subtly different early-return conditions, until nobody can answer "will this specific loan get approved" without stepping through the debugger for that exact chain of conditions — the chain has become an undocumented decision tree that's harder to reason about than the equivalent explicit rules engine or decision table would have been.

## When NOT to use vs. Command

Chain of Responsibility and [Command](command.md) both wrap "how to handle a request" as an object, but solve different problems — see the callout in command.md. In short: Command binds a request to one specific, known receiver at construction time and hands it to an invoker that just triggers it; Chain of Responsibility doesn't know who (if anyone) will handle the request until it has walked the chain. Don't reach for a chain when you already know the exact handler at the call site — that's a Command or a direct call, not routing.

## Structure

- **Handler** (interface): declares `handle(request)` and holds a reference to the next handler.
- **ConcreteHandler**: decides whether it can handle the request; if not (or in addition), forwards to the next handler.
- **Client**: builds the chain and sends the request to its head, without knowing which handler will process it.

```java
abstract class SupportHandler {
    protected SupportHandler next;

    SupportHandler setNext(SupportHandler next) { this.next = next; return next; }

    void handle(Ticket ticket) {
        if (canHandle(ticket)) {
            resolve(ticket);
        } else if (next != null) {
            next.handle(ticket);
        } else {
            throw new UnhandledTicketException(ticket);
        }
    }

    abstract boolean canHandle(Ticket ticket);
    abstract void resolve(Ticket ticket);
}

class Tier1Support extends SupportHandler {
    boolean canHandle(Ticket t) { return t.severity() == Severity.LOW; }
    void resolve(Ticket t) { /* ... */ }
}
```

## Consequences

**Benefits:**
- Sender is decoupled from the specific handler that ends up processing the request.
- Handlers can be added, removed, or reordered independently of the sender and of each other.
- Each handler has a single, focused responsibility — easy to test in isolation.

**Costs:**
- No guarantee a request gets handled at all unless the chain enforces a fallback/default handler — silent drops are a real failure mode if a chain isn't terminated properly.
- Debugging "who handled this, and why did it stop here" requires walking the chain at runtime; nothing at the call site tells you which handler will fire.
- Performance: a long chain means a request may traverse many handlers before being handled, each doing at least a cheap eligibility check.
- Order-dependent bugs are easy to introduce silently — reordering two handlers can change which one "wins" for edge-case requests, with no compiler or type-level signal that it happened.

## Related patterns

- [Command](command.md) — see callout above; Command fixes the receiver at construction, Chain of Responsibility resolves it dynamically by traversal.
- [Pub-Sub](../integration/pub-sub.md) — differs in that Chain of Responsibility typically expects exactly one (or a bounded, ordered subset of) handler to act and can stop propagation, while pub-sub fans out to all independent subscribers with no "stop here" semantics and no shared ordering contract.
- [Decorator](../structural/decorator.md) — structurally similar (a chain of wrapped objects forwarding calls), but Decorator's intent is *adding* behavior around every call, not choosing exactly one handler among alternatives — every decorator in the stack runs, whereas a Chain of Responsibility handler can stop the chain.
- [Template Method](template-method.md) — can define the shared `handle()` skeleton (check eligibility, then either resolve or forward) that concrete handlers customize just the eligibility/resolve steps for.

## Smells that suggest this pattern

- A single method with a long `if (canHandleA) ... else if (canHandleB) ... else if (canHandleC)` chain that keeps growing every time a new case type is added, and the sender has to be edited each time.
- Middleware-like code duplicated at multiple entry points because there's no shared, composable pipeline mechanism.
- A "which validator/rule applies here" decision embedded directly in business logic, making it hard to test rules independently or add a new one without touching the orchestrating method.

## Common misuse

- Building a chain for a fixed, small, non-growing set of cases better served by a lookup table or a simple `if` — pure ceremony over a `switch`.
- Allowing handlers to silently swallow requests (no fallback handler, no logging when nothing matched) so failures manifest as "nothing happened" rather than a clear error.
- Letting the chain's handlers share and mutate a common context object as an escape hatch for cross-handler communication that the pattern doesn't naturally support — a sign the problem doesn't fit Chain of Responsibility and needs a different structure (explicit pipeline, rules engine, or plain sequential calls).
