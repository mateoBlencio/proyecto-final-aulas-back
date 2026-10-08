# Onion Architecture

## Intent

Center the system on a framework-free domain model, wrap it in domain services, then application services, then outermost infrastructure/UI — with a single rule: nothing at the center depends on anything further out, and infrastructure has no special status among the outer layers.

## Problem

Same underlying problem as Hexagonal and Clean Architecture: infrastructure coupling makes the domain untestable and unswappable. Onion (coined by Jeffrey Palermo, predating both) frames the fix specifically as "the database is not special" — in a typical layered app, persistence quietly becomes the thing everything else is built around; Onion insists the domain model is the center and the database is just another outer-ring detail, no more privileged than the UI.

Concretely: in a layered app, "what tables do we have" often drives "what classes do we have" — the domain model ends up shaped like the schema. Onion reverses that: the domain model is designed first, around the actual business concepts and rules, and the schema is derived from it by the persistence layer, not the other way around.

## When to use

- The domain has meaningful behavior modeled as rich domain objects (not anemic data bags) and that behavior needs to be reasoned about and tested independent of any specific database.
- The team wants a simpler mental model than Clean Architecture's four named rings — Onion gives one rule ("depend inward") without mandating per-use-case interactor/boundary/DTO scaffolding.
- Persistence technology is expected to change, or the team wants the option to test domain logic against an in-memory repository implementation without spinning up a database.
- Building a system where domain services (business logic that doesn't naturally belong to one entity) are a first-class concept distinct from application-level orchestration.
- A team that finds Clean Architecture's four named rings and per-use-case interactor scaffolding excessive, but still wants an enforced inward-dependency rule rather than layered's convention-only boundary.
- Domain logic that spans multiple aggregates or entities often enough that it needs an explicit home, rather than being awkwardly attached to whichever entity happened to need it first.

## When NOT to use

- A data-centric application where the "domain model" would just be a thin wrapper around table rows with no independent behavior — Onion's ring discipline (repository interfaces defined in the domain, implemented outward) adds indirection to protect logic that doesn't exist.
- A small, short-lived project where nobody will ever swap the persistence technology and no domain service is complex enough to need isolated unit testing — the interface-per-repository overhead isn't earning its keep.
- When the team conflates "Onion" with "yet more layers" and adds application services that do nothing but forward calls to domain services one-for-one — that's the Layered anti-pattern of pass-through indirection, wearing Onion's vocabulary.
- Concrete failure: a two-person team building an internal tool defines `IProductRepository`, `IProductDomainService`, and `ProductApplicationService`, each with one implementation and no test ever swapping any of them — three files and two interface hops to perform what is, in practice, a single `UPDATE` statement.

## Structure

Rings, innermost to outermost, each depending only on rings inside it:

```
domain_model/       → entities, value objects — no framework imports, no persistence
                        knowledge; may declare repository interfaces here
domain_services/    → business logic that spans multiple entities, still framework-free
application_services/ → orchestrates domain services and repositories for a use case,
                        handles transactions, coordinates but doesn't hold business rules
infrastructure/     → repository implementations (JPA/SQL), external service clients —
                        just another outer ring, same status as...
ui/                 → controllers, views — ...this one; neither is privileged over
                        the other, both depend inward on application_services
```

The defining move: repository interfaces live in `domain_model` or `domain_services`, and `infrastructure` implements them — dependency points inward even for the database, exactly as it would for the UI.

## Consequences

**Benefits:**
- Single, easy-to-explain rule (depend inward, the database is not special) without Clean Architecture's mandated per-use-case scaffolding.
- Domain services get a first-class home distinct from orchestration logic, so multi-entity business rules aren't forced onto a single entity that happens to own them first.
- Domain and domain services are testable without any infrastructure — no database, no HTTP context.
- The database's demotion to "just another outer ring" is a useful mental corrective on teams whose designs otherwise orbit the schema.

**Costs:**
- Less prescriptive than Clean Architecture, so teams invent their own conventions for where, e.g., validation belongs (domain service vs. application service) — which can drift without active discipline or review.
- Still requires an interface plus at least one implementation per infrastructure dependency, with the same overhead as Hexagonal for cases with only one real implementation.
- Risk of application services becoming a dumping ground if the domain-service/application-service split isn't actively enforced — they're an easy place to put "just one more call" without noticing orchestration has become business logic.
- The lack of named, numbered rings (versus Clean Architecture) makes it harder to point to a diagram and say precisely which ring a given class belongs in during review.

## Related patterns

- [Hexagonal](hexagonal.md) uses ports/adapters vocabulary and doesn't formally separate "domain services" from "application services" — Onion draws that line explicitly. Hexagonal is also usually described with exactly two sides (driving/driven); Onion is described as N concentric rings.
- [Clean Architecture](clean-architecture.md) is the more prescriptive descendant — it names four rings and typically adds per-use-case Interactor classes with Request/Response DTOs, where Onion is content with a domain/application service split and no mandated per-use-case objects.
- [Dependency Inversion](../principles/solid.md) is the mechanism that makes the repository-interface-in-the-domain move possible.
- **Spring Modulith aside**: Modulith's module-with-events boundary is a coarser, cross-context concern; Onion's rings structure the inside of one bounded context. A Spring Modulith module with a genuinely rich domain can be internally onion-shaped; a thin CRUD module gets no benefit from adding rings it doesn't need.

## Smells that suggest this pattern

- Domain entities that keep gaining persistence-framework annotations and, as a result, keep breaking whenever the ORM version changes.
- Business logic that spans multiple entities currently living in whichever entity happened to need it first, rather than in a dedicated domain service.
- Repository interfaces (if they exist at all) defined next to their implementation rather than next to the domain that uses them — the classic sign dependency hasn't actually been inverted, just renamed.
- The domain model's shape visibly mirrors the database schema (same table names as class names, same column names as field names, foreign-key IDs instead of object references) rather than reflecting the business vocabulary.
- Application services with names like `ProductManager` or `OrderHelper` that have grown to hold both orchestration and business rules, because there's no separate domain-service concept to route the business rules to.

## Common misuse

- Defining `IProductRepository` in the domain layer per convention, on a project where every repository has exactly one implementation, will only ever have one implementation, and is never faked in a test — the interface adds a jump-to-definition hop with zero payoff.
- Turning `application_services` into a second, redundant service layer that just calls `domain_services` one method at a time with no orchestration, transaction boundary, or coordination logic of its own — this is Layered's pass-through smell recurring inside Onion's vocabulary.
- Treating "Onion" as license to skip the discipline Clean Architecture would have forced (per-use-case boundaries) while still writing an interface for absolutely everything "for consistency," ending up with the boilerplate cost of Clean Architecture and none of its enforcement tooling.
- Naming a package `domain_services` and putting orchestration code there (transaction handling, DTO mapping) instead of actual multi-entity business rules, defeating the purpose of having a separate ring for it.
