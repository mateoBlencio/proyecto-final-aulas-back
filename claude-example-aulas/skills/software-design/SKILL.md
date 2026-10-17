---
name: software-design
description: "Reusable knowledge library of software design patterns, principles, and anti-patterns spanning GoF, architectural, concurrency, distributed-systems, and integration patterns. Use when designing or modifying code, choosing between architectural approaches, evaluating whether a pattern fits a problem, or diagnosing a code smell to find the corresponding refactor/pattern. Not a list to memorize: a reference to consult and a process for deciding when (and when not) to apply a pattern."
---

# Software Design Knowledge Library

This is a reference library, not a checklist. Most of the value is in the
**decision process** below; the individual pattern files are reference
material you consult once you know what you're looking for, not something to
read end-to-end or apply reflexively.

## How to use this skill

When designing or modifying code:

1. Analyze the problem first, in isolation from any pattern name.
2. Identify responsibilities, coupling, variability, lifecycle, and
   communication needs (see decision process below).
3. Consider whether a known pattern fits: check the category index and open
   the specific file(s) that look relevant.
4. Read "When NOT to use" before "When to use". Most misapplied patterns were
   applied for the excitement of using them, not because the problem needed
   them.
5. Do **not** introduce a pattern just because it exists or because the
   problem superficially resembles its textbook example.
6. Before implementing, state briefly:
   - what problem you're solving,
   - what pattern(s) you considered,
   - why you chose (or discarded) each,
   - what trade-offs the choice introduces.
7. Prefer the simplest solution that solves the actual problem. A pattern
   that adds indirection without removing a real variability point is a net
   loss.

## Decision process

Work through these questions before reaching for a pattern name. Most of
them map directly to a pattern category.

```
Problem
  → What varies, and what should stay fixed? (→ creational / behavioral)
  → What is tightly coupled that shouldn't be? (→ structural)
  → What has a lifecycle or identity that needs managing? (→ creational)
  → What communicates with what, and how? (→ behavioral / integration)
  → Where do the architectural boundaries sit? (→ architectural)
  → What runs concurrently, and what state do they share? (→ concurrency)
  → What crosses a process/network boundary, and how can it fail? (→ distributed)
  → What external system are we integrating with? (→ integration)
Candidates
  → List 2-3 patterns that could plausibly apply.
  → For each: what it buys you, what it costs you.
Decision
  → Pick the simplest option that resolves the actual variability/coupling
    identified above. If none of the candidates clearly help, don't use one.
Verify
  → After implementing, re-check: did this remove the friction it was meant
    to remove? If not, back out.
```

Example of the reasoning this produces. Not "I need interchangeable auth
strategies, therefore Strategy" but:

```
Problem: algorithm varies (auth method), selected at runtime, callers
         shouldn't care which one.
Candidates:
  - Strategy: encapsulates interchangeable algorithms, easy to add new ones,
    adds one interface + one object per variant.
  - State: rejected, we're not modeling transitions between states, just
    selecting one of several fixed algorithms.
  - Factory Method: could help construct the right strategy, but doesn't
    solve the interchangeability problem itself. It's a companion, not an
    alternative.
Decision: Strategy for the algorithm, optionally a simple factory/map to
          select which one, not a full Factory Method hierarchy given only
          3 known variants.
```

Also run the reverse direction: if you notice a **code smell**, check
`anti-patterns` for the smell and its typical remedy before reaching for a
pattern from scratch.

## Category index

| Category | Path | Contents |
|---|---|---|
| Principles | [`principles`](principles/) | SOLID, DRY, KISS, YAGNI, Separation of Concerns |
| Creational (GoF) | [`creational`](creational/) | Factory Method, Abstract Factory, Builder, Prototype, Singleton |
| Structural (GoF) | [`structural`](structural/) | Adapter, Bridge, Composite, Decorator, Facade, Flyweight, Proxy |
| Behavioral (GoF) | [`behavioral`](behavioral/) | Chain of Responsibility, Command, Iterator, Mediator, Memento, Observer, State, Strategy, Template Method, Visitor |
| Architectural | [`architectural`](architectural/) | Layered, Hexagonal, Clean Architecture, Onion, MVC, CQRS, Event-Driven |
| Concurrency | [`concurrency`](concurrency/) | Producer-Consumer, Readers-Writers, Thread Pool, Actor, Futures/Promises |
| Distributed systems / resilience | [`distributed`](distributed/) | Saga, Circuit Breaker, Retry, Bulkhead, Timeout, Idempotency, Rate Limiter |
| Integration | [`integration`](integration/) | API Gateway, Backend for Frontend, Message Broker, Pub/Sub, Outbox, Anti-Corruption Layer |
| Anti-patterns | [`anti-patterns`](anti-patterns/) | God Object, Spaghetti Code, Shotgun Surgery, Circular Dependency, Premature Abstraction |

Each pattern file follows the same structure: Intent, Problem, When to use,
When NOT to use, Structure, Consequences, Related patterns, Code examples,
Smells that suggest this pattern, Common misuse.

## Scope note

This library is deliberately agent-agnostic content under `.claude/skills/software-design/`.
It doesn't assume Claude Code specifically: the `SKILL.md` + flat reference
files layout is portable to other agent-skill formats (e.g. Antigravity) by
pointing their loader at this same directory later; no adapter exists yet.
