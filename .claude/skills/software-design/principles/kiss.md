# KISS (Keep It Simple, Stupid)

## Intent

Prefer the simplest design that correctly solves the current problem over a more general or clever one.

## Problem

Unnecessary complexity — extra layers, extra configurability, extra abstraction — raises the cost of every future reader having to understand the system before they can safely change it. Complexity that doesn't pay for itself in solved problems is pure cost: more code paths to test, more ways to misuse the API, more surface area for bugs.

## When to use

- You're about to introduce a design pattern, framework, or configuration layer to solve a problem that a straightforward function or class already solves.
- The "flexible" version of a solution has never actually been asked to flex (no second implementation, no second caller with different needs) since it was written.
- Code review reveals that explaining the abstraction takes longer than explaining the direct approach would have.
- Two engineers independently misuse an API because its "simple" surface actually requires understanding four collaborating classes first.

## When NOT to use

- Don't invoke KISS to justify skipping error handling, input validation, or tests — "simple" means simple *design*, not simple in the sense of incomplete or unsafe.
- Don't use it to avoid necessary domain complexity. A pricing engine with genuinely complex tax rules across jurisdictions cannot be flattened into a single function just to look simple — the complexity is inherent to the problem (essential complexity), not accidental. Forcing it into one function makes it simple-looking but hard to verify.
- Don't use it to reject an abstraction that's already paying for itself with two or more real call sites just because a single-call-site version would read shorter — that's premature de-abstraction, not simplicity.
- Don't use "the simplest thing" as an excuse for a hardcoded, unmaintainable one-off in a codebase where the same shape of problem recurs constantly (e.g. hand-rolling a one-off retry loop when the codebase has five other retry loops and a `RetryPolicy` utility already exists).

## Structure

Over-engineered:
```java
interface NotificationStrategy { void notify(User u, String msg); }
interface NotificationStrategyFactory { NotificationStrategy create(String type); }
class NotificationStrategyFactoryImpl implements NotificationStrategyFactory {
    public NotificationStrategy create(String type) {
        return switch (type) { case "email" -> new EmailNotificationStrategy(); ... };
    }
}
```

Simple, same behavior, one caller, one notification type today:
```java
class EmailNotifier {
    void notify(User user, String message) { emailClient.send(user.email(), message); }
}
```

## Consequences

- **Benefits**: less code to read, test, and maintain; fewer places for bugs to hide; onboarding a new engineer is faster; changes are localized because there's less indirection to trace through.
- **Costs**: a genuinely simple design sometimes has to be refactored into a more general one later, when a second real requirement appears — that refactor has a real cost, but it's a cost paid once, with real information, instead of speculatively up front on every feature.

## Related patterns

- [yagni.md](yagni.md) — close sibling; YAGNI is about *when* to build something, KISS is about *how* to build it once you do.
- [dry.md](dry.md) — tension partner; sometimes duplication is simpler than the abstraction that removes it.
- [Premature Abstraction](../anti-patterns/premature-abstraction.md) — the typical KISS violation.
- [God Object](../anti-patterns/god-object.md) — the opposite failure (under-structuring, not over-structuring), worth distinguishing from healthy simplicity.

## Smells that suggest this pattern

- A class hierarchy with one concrete subclass per abstract base.
- A configuration file with fifteen options, twelve of which have never been changed from their default.
- A "generic" utility function that only one caller invokes, with parameters that exist only for that one caller.
- Needing a diagram to explain how a single feature's request flows through the layers.

## Common misuse

- "Simple for me to write" mistaken for "simple to read" — a clever one-liner using obscure language features is not KISS, it's optimizing for the wrong reader.
- Using KISS to avoid writing tests ("the code is simple, it obviously works") — simplicity of design and correctness are different axes.
- Flattening genuinely distinct responsibilities into one function to reduce file count, producing a function that is short in isolation but does five unrelated things, which is a SRP violation wearing KISS as a justification.
