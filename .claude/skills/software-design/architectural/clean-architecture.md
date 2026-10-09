# Clean Architecture

## Intent

Structure the system as concentric rings — Entities, Use Cases, Interface Adapters, Frameworks & Drivers — with a strict Dependency Rule: source code dependencies only ever point inward, and inner rings know nothing about outer rings.

## Problem

Same root problem as Hexagonal — business rules coupled to infrastructure are untestable and unswappable — but Clean Architecture additionally targets systems with many distinct use cases that need explicit, individually testable boundaries, where "the domain" alone isn't a fine enough unit of isolation.

Concretely: in a large domain with thirty operations, a single "OrderService" class accumulates all thirty as methods, and testing "cancel order" pulls in setup for the other twenty-nine through shared class state. Clean Architecture forces each into its own Interactor with its own explicit inputs and outputs, so the thirty operations are thirty independently testable units instead of one large one.

## When to use

- The system has many independent, individually meaningful use cases (not just "an order domain" but distinct operations: PlaceOrder, CancelOrder, ApplyDiscount, each with its own rules and its own testable boundary).
- Multiple delivery mechanisms are expected over the system's life (web API today, gRPC or a batch job later) and each should be able to drive the same use cases without duplicating logic.
- The team wants the Dependency Rule enforced by tooling (ArchUnit, Spring Modulith's boundary checks, ArchUnitNET, etc.), not just convention.
- Long-lived enterprise systems where the cost of a framework migration five years out is a real, budgeted concern.
- Regulatory or safety-critical domains where each use case needs to be independently reviewable, testable, and traceable to a requirement.
- A large team where explicit, named boundaries (rather than tribal-knowledge conventions) are needed to keep dozens of contributors from accidentally coupling layers.

## When NOT to use

- A CRUD app cargo-culted into the full four-ring structure: an Entity, a UseCase interactor with its own Request and Response model, a Presenter, and a ViewModel — for an operation that is, underneath all of it, `UPDATE users SET name = ? WHERE id = ?`. This produces five classes and three DTO-to-DTO mapping steps that map 1:1 with each other, adding no actual flexibility.
- A team with no automated enforcement of the Dependency Rule. Without ArchUnit-style checks in CI, the rule erodes silently — someone imports a JPA type into a Use Case "just this once" — and the team pays the full ceremony cost (rings, mapping, indirection) with none of the actual boundary-enforcement benefit that justifies it.
- Small teams or short-lived projects, for the same reasons as Hexagonal: the per-use-case scaffolding (boundary interfaces, request/response models, presenters) is expensive to write and to read, and only pays off across many use cases over a long timeline.
- When the "use case" is trivial pass-through with a single business rule — the interactor pattern adds a class and two DTOs to protect a single `if` statement.

## Structure

Dependencies point inward only; each ring only knows about the ring(s) inside it, never outside.

```
entities/            → enterprise-wide business rules, framework-free
use_cases/           → application-specific business rules
  PlaceOrderInputBoundary   (interface — what the use case does)
  PlaceOrderOutputBoundary  (interface — how results get reported back)
  PlaceOrderInteractor      (implementation, depends only on Entities + boundaries)
  PlaceOrderRequestModel / PlaceOrderResponseModel  (plain data, cross the boundary)
interface_adapters/  → Controllers (implement InputBoundary calls), Presenters
                        (implement OutputBoundary), Gateways (repository impls)
frameworks_drivers/  → web framework, DB driver, UI — outermost, most volatile
```

Data crossing a boundary uses plain structures (Request/Response models), not domain entities directly — a controller in `interface_adapters` builds a `PlaceOrderRequestModel` and calls the interactor through its `InputBoundary` interface; nothing inward-facing imports anything from `frameworks_drivers`.

## Consequences

**Benefits:**
- Extreme testability — each use case is testable in isolation with plain objects, no framework, no database, no HTTP layer.
- Genuine framework independence, and unlike Hexagonal alone, that independence is enforceable by tooling against a formally named rule (the Dependency Rule) with named rings to check.
- Explicit boundaries make it obvious where a new use case starts and ends — no ambiguity about which class owns which business rule.
- Adding a new delivery mechanism (a gRPC endpoint alongside REST) reuses every inner ring untouched.

**Costs:**
- Very high boilerplate — a system with twenty use cases can easily produce a hundred small classes (interactor, two boundary interfaces, two DTOs, presenter, per use case).
- Steep onboarding curve for engineers unfamiliar with the ring vocabulary (Entities vs Use Cases vs Interface Adapters vs Frameworks/Drivers) and the InputBoundary/OutputBoundary split.
- The ceremony is only worth it if the Dependency Rule is actually enforced (ArchUnit or equivalent) and the use case count is large enough to amortize the scaffolding cost — below some threshold of use cases, the ceremony cost per use case dominates.
- Every field added to a use case's data often means touching a Request model, a Response model, and a Presenter in addition to the Entity — a small domain change becomes a multi-file change.

## Related patterns

- [Hexagonal](hexagonal.md) is the less prescriptive sibling — ports and adapters without a mandated ring count or per-use-case Request/Response objects. Clean Architecture is Hexagonal with an opinionated, numbered ring structure and (typically) one Interactor class per use case.
- [Onion](onion.md) predates Clean Architecture and centers on the domain model and domain services rather than per-use-case interactors; Onion states "nothing at the center depends outward" without formalizing four named rings.
- [Dependency Inversion](../principles/solid.md) is the mechanism behind the Dependency Rule.
- [Command](../behavioral/command.md) — an Interactor is structurally similar to a Command object with an explicit undo-free execute step.
- **Spring Modulith aside**: Modulith's module boundaries operate at a coarser grain (whole bounded contexts communicating via events) than Clean Architecture's rings (which structure the inside of a single context). A Spring Modulith module can be internally organized with Clean Architecture's rings if its use case count justifies the ceremony; most modules in a modular monolith don't need it and are better served by simpler [Hexagonal](hexagonal.md) or even [Layered](layered.md) internals.

## Smells that suggest this pattern

- A domain with many distinct, independently-testable operations currently implemented as long methods on a handful of "God" service classes, making it impossible to test one operation without dragging in unrelated ones.
- Repeated pain from framework upgrades breaking business logic because business logic directly imports framework types.
- A team already maintaining ArchUnit-style rules for a layered or hexagonal codebase and finding the rules insufficient because use cases still aren't independently testable.
- A single service class whose test file requires mocking a dozen unrelated collaborators just to test one of its many methods.
- Product requirements consistently phrased as discrete use cases ("as a customer, I cancel an order") that map cleanly to one Interactor each, rather than as CRUD fields on a resource.

## Common misuse

- Implementing the full four-ring ceremony for a CRUD app: Entity, UseCase with its own Request/Response models, Presenter, ViewModel — five layers of DTOs that map 1:1 onto each other with no divergence ever occurring between them, adopted because "Clean Architecture" was the name recognized from a conference talk rather than because the use case count or independence requirement justified it.
- Defining boundary interfaces (InputBoundary/OutputBoundary) but instantiating the concrete Interactor directly wherever it's used, skipping the interface — the ring diagram exists in documentation but not in the actual dependency graph.
- Treating every CRUD field addition as requiring changes to five classes (Entity, Request model, Response model, Presenter, ViewModel) with no automated mapping, turning a one-line schema change into a five-file change with no corresponding safety benefit.
- Building the full ring structure up front for a greenfield project with zero use cases implemented yet, rather than letting the use case count and independence requirements justify the structure as they actually appear.
