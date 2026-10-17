# Layered Architecture

## Intent

Organize an application into horizontal layers — typically presentation, business/application, and persistence — where each layer only calls the layer directly below it.

## Problem

Mixing UI rendering, business rules, and database access in the same method or class makes each concern impossible to change or test independently. Layering gives every piece of code an obvious, conventional place to live, and stops (in theory) a change to the database schema from directly touching HTML rendering code.

Concretely: without any layering, a servlet or controller method that reads request parameters, runs a discount calculation, and issues a raw SQL `UPDATE` in the same 40 lines means a change to the discount rule risks breaking request parsing, and a schema change risks breaking the discount rule — three unrelated failure modes sharing one blast radius.

## When to use

- Typical CRUD-heavy business applications: web app with controllers, services, repositories.
- Teams that need a shared, widely-understood convention so new hires are productive on day one — layered architecture is the default most frameworks (Spring MVC, Rails, Django) scaffold for you.
- Moderate domain complexity where the main need is "know where to put things," not framework independence.
- Projects with a single, stable delivery mechanism (one web frontend, one DB) where multiple interchangeable infrastructures are not a real requirement.
- A short-to-medium project lifetime where the cost of a future framework migration is unlikely to ever be paid, so protecting against it has no expected value.
- The team explicitly wants to defer architectural investment until a real need (a second UI, a second datastore, a genuinely complex domain rule) actually shows up — layered is a reasonable starting point that can be evolved toward Hexagonal/Onion later if that need materializes.

## When NOT to use

- A small script or single-purpose CLI tool — three layers to change one behavior is pure ceremony when the whole program is 200 lines.
- Long-lived core business logic that must survive a framework or database migration — layered architecture, as usually implemented, lets the business layer depend directly on persistence types (JPA entities, ORM sessions), so a DB swap forces changes throughout the business layer. If framework independence is a real requirement, see [Hexagonal](hexagonal.md) or [Clean Architecture](clean-architecture.md) instead.
- When layers become pure pass-through with no logic — controller calls service calls repository, one method each, zero business rules anywhere. At that point the "layers" are three files doing the job of one, and every change requires touching all three for no isolation benefit gained.
- When business logic actually has non-trivial invariants that need protecting from infrastructure concerns — plain layered gives no mechanism (no dependency inversion) to stop persistence details leaking upward into "business" code.

## Structure

Top-down dependency direction: presentation depends on business/application, which depends on persistence, which depends on the database. Nothing depends upward.

```
com.app.web/          → controllers, DTOs for requests/responses
com.app.service/      → business/application logic, orchestrates repositories
com.app.repository/   → data access (often Spring Data / JPA repositories)
com.app.domain/       → entities — frequently also the JPA-annotated persistence
                          model, since layered doesn't mandate keeping them separate
```

Business layer classes typically call repository interfaces directly, and those interfaces are usually implemented with a specific persistence technology (JPA/Hibernate) whose annotations live right on the domain objects. There is no architectural rule forcing separation between "the domain model" and "the persistence model" — that separation, when it exists, is Hexagonal/Clean/Onion territory.

## Consequences

**Benefits:**
- Fast to build — most frameworks scaffold this structure for you, so there's no upfront design cost.
- Immediately familiar to any engineer who has touched a typical framework app; near-zero ramp-up time for the convention itself.
- Clear default location for new code — a new field, a new endpoint, a new query each have an obvious layer to land in.
- Low ceremony for simple CRUD flows compared to any dependency-inverted variant.

**Costs:**
- Lower layers' technical choices leak upward — swapping the ORM means touching the domain/business layer because they're frequently the same classes.
- Testing business logic in isolation usually means mocking a repository interface and standing up at least a partial framework context, rather than testing pure logic with plain objects.
- The boundary between layers is easy to violate under time pressure (a controller reaching straight into a repository "just this once") and nothing stops that at compile time — it's a convention, not an enforced rule.
- Cross-cutting concerns (logging, auth, transaction handling) tend to get duplicated per layer instead of centralized, since there's no architectural seam designed to hold them.
- As business logic grows, the service layer has nowhere else to go and tends to absorb everything, trending toward a God Object over the project's lifetime.

## Related patterns

- [Hexagonal](hexagonal.md), [Clean Architecture](clean-architecture.md), and [Onion](onion.md) are layered architecture with dependency inversion applied — same conceptual layers, but the business/domain layer defines interfaces that the persistence layer implements, rather than depending on persistence directly.
- [MVC](mvc.md) is orthogonal — it structures the presentation layer specifically, and typically sits on top of a layered backend.
- [Dependency Inversion](../principles/solid.md) is the principle that, applied consistently to a layered architecture, turns it into Hexagonal/Clean/Onion.
- [God Object](../anti-patterns/god-object.md) is a common failure mode of an overloaded service layer.
- [Shotgun Surgery](../anti-patterns/shotgun-surgery.md) is the typical symptom when a cross-cutting concern (auth, logging) has been duplicated per layer instead of centralized.
- [Facade](../structural/facade.md) is sometimes used to give the service layer a cleaner entry point without addressing the underlying coupling to persistence.
- **Spring Modulith aside**: this project uses Spring Modulith, which organizes code around vertical module boundaries with event-based communication between modules, not horizontal layers. A given module can still be internally layered (controller → service → repository) — Modulith constrains cross-module coupling, it doesn't replace intra-module layering.

## Smells that suggest this pattern

- Code with no separation at all between HTTP handling, business rules, and SQL in the same method — layering is the first, cheapest fix.
- A team with high onboarding churn that needs a convention everyone already knows, more than it needs architectural purity.
- A greenfield CRUD app where nobody has yet identified a change dimension (swappable infra, multiple UIs) that would justify more structure.
- A service layer method whose signature takes and returns raw JPA entities, meaning any caller — including a future second UI — is stuck with the persistence model's shape.
- A pull request review comment history full of "please don't call the repository directly from the controller," repeated across many PRs — the boundary is understood in principle but not enforced.

## Common misuse

- Adding a "layer" for every conceivable future need — DTO layer, mapper layer, service layer, manager layer, facade layer — on a project with five endpoints and no real business invariants, producing five classes and three mapping steps to perform a single CRUD write.
- Treating "layered" as synonymous with "well-architected" and never revisiting the boundary once business logic actually grows complex — teams keep adding to a bloated service layer indefinitely because "that's where business logic goes," turning it into a God Object by another name.
- Letting the presentation layer skip the business layer and call the repository directly "for a quick read," which erodes the boundary until the layers exist in name only.
- Introducing a "service" class per entity that does nothing but call the matching repository method with the same signature — a 1:1 pass-through that exists solely because "every entity needs a service" was adopted as an unquestioned rule.
- Splitting what is a single conceptual operation (e.g. "place an order") across three separate service classes purely because it touches three tables, instead of keeping the operation's logic together and letting it call three repositories.
