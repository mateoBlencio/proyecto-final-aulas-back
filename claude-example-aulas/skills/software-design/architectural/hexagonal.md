# Hexagonal Architecture (Ports and Adapters)

## Intent

Isolate the domain/business logic from every external actor — UI, database, message queue, third-party API — behind interfaces ("ports") that the domain defines and outside code ("adapters") implements or calls.

## Problem

When domain logic is directly coupled to a specific web framework or ORM, you cannot unit-test business rules without a running database or web context, cannot swap infrastructure without rewriting the domain, and the direction of dependency (domain depends on infrastructure) is backwards from what the business actually depends on for correctness.

Concretely: a pricing rule that lives inside a `@Service` class calling `@Autowired JpaOrderRepository` cannot be tested without a Spring context and a database connection, and swapping from Postgres to a document store means editing the class that contains the pricing rule itself, not just the class that talks to the database.

## When to use

- The domain has real, non-trivial business rules worth protecting and testing in isolation from infrastructure.
- Multiple driving actors need the same use case — a REST API, a CLI, and a message consumer all need to place an order the same way.
- Infrastructure is genuinely expected to change or vary — different databases per deployment, a payment gateway that will be swapped, a test double that must stand in for a real dependency.
- You need to unit-test business logic fast, without Spring context, HTTP server, or database.
- The core logic is expected to significantly outlive whatever framework currently wraps it.
- Compliance or audit requirements demand that business rules be independently verifiable without depending on how they're wired to infrastructure.
- The team already has a natural boundary they want to enforce with tooling, and hexagonal's two-sided (driving/driven) split is simple enough to check automatically.

## When NOT to use

- A CRUD service where each operation maps close to 1:1 to a table row — wrapping `save(order)` in a `OrderPort` interface with one implementation buys nothing; there is no second implementation ever coming and no logic to protect.
- A short-lived or small-team project — a hackathon prototype or a script slated for rewrite in six months pays the ports/adapters tax (an interface plus an implementation for every external dependency, mapping at every boundary) for a payoff that never materializes.
- When "the domain" is actually just an orchestration of database queries — a reporting endpoint that joins five tables and formats the result has no business rule to isolate; hexagonal ceremony around it is theater, not protection.
- When the team will define ports but never actually swap or fake the adapters — if the interface always has exactly one implementation and the concrete class is autowired directly everywhere anyway, the inversion exists on paper only.

## Structure

Dependency direction: everything points inward, toward the domain. The domain module has zero imports of Spring, JPA, HTTP clients, or any framework type.

```
domain/            → entities, value objects, business rules
                      ports: OrderRepository (interface), PaymentGateway (interface)
application/        → use cases, implemented purely against ports
adapters/in/web/     → REST controllers — driving side, calls into application use cases
adapters/in/cli/     → CLI commands — another driving adapter for the same use cases
adapters/out/persistence/ → JPA implementation of OrderRepository — driven side
adapters/out/payment/     → Stripe implementation of PaymentGateway — driven side
```

Inbound ("driving") adapters call into the application; outbound ("driven") adapters implement interfaces the domain declares. The domain never imports an adapter package.

## Consequences

**Benefits:**
- Domain logic is testable with plain unit tests and fake port implementations — no Spring context, no database, no HTTP server needed.
- Infrastructure is swappable without touching the domain — a new payment provider is a new adapter, not a domain change.
- Dependency inversion is enforced at the module/package boundary, not just by convention — a compile-time (or build-tool-checked) guarantee that the domain never imports infrastructure types.
- Multiple driving adapters (REST, CLI, message consumer) can reuse the same use case without duplicating logic.

**Costs:**
- More files — an interface plus at least one implementation per external dependency, even where only one implementation will ever exist.
- Mapping between domain models and persistence/DTO models at every boundary crossing adds code that has no business logic in it, just translation.
- The team must actively resist the pull to leak JPA annotations, HTTP types, or serialization concerns into the domain — this takes discipline that erodes without review or tooling.
- Onboarding cost for developers used to a "just call the repository" layered style, who now have to learn where ports are declared versus where they're implemented, and why that split exists.
- Initial velocity is slower than layered for the first few features, since the port/adapter split has to be built before any business logic can run end to end.

## Related patterns

- [Clean Architecture](clean-architecture.md) and [Onion](onion.md) are close variants — see each file for the specific deltas. In short: Hexagonal is the least prescriptive of the three (ports and adapters, no mandated ring count, no required per-use-case request/response objects — a port can simply take a domain object). Clean Architecture adds explicit concentric rings and typically a dedicated Interactor class per use case with its own Request/Response models. Onion predates Clean Architecture, centers on the domain model plus domain services with no "ports/adapters" vocabulary, and states the rule as "nothing in the center depends outward" rather than defining symmetric ports.
- [Dependency Inversion](../principles/solid.md) is the principle Hexagonal enforces structurally.
- [Anti-Corruption Layer](../integration/anti-corruption-layer.md) is a special case of an outbound adapter that also translates between the domain's model and a foreign system's model.
- **Spring Modulith aside**: this project uses Spring Modulith for module boundaries, which is a different axis than hexagonal rings. Modulith constrains *cross-module* coupling — module A doesn't call module B's internals, only reacts to its published events. Hexagonal constrains a *single* module's internal dependency direction (domain versus adapters). The two compose: each Spring Modulith module can be internally structured hexagonally.

## Smells that suggest this pattern

- Domain entities annotated with `@Entity`/`@Table`, or business logic importing `HttpServletRequest` or a specific HTTP client type.
- Unable to write a unit test for a use case without booting a Spring context or a test database.
- SQL construction or JSON (de)serialization logic living in the same class as calculation or validation rules.
- A recurring need to run the same business operation from more than one entry point (REST + scheduled job + message listener) with logic currently duplicated across all three.
- Test suites that need `@SpringBootTest` (full context) for what should be a pure logic test, because the class under test can't be instantiated without a database connection.
- A domain concept (e.g. "available inventory") whose calculation logic is duplicated in two adapters because there's no single domain object both routes through.

## Common misuse

- Wrapping a thin CRUD API in full ports/adapters "because it's best practice": `OrderPort` → `OrderPortImpl` → `OrderRepository` → JPA, three hops to perform `save(order)`, with no swappable implementation ever built and no test ever exercising a fake.
- Declaring the port interface but injecting the concrete adapter class directly in practice (`@Autowired OrderRepositoryImpl` instead of the `OrderRepository` interface) — the port exists syntactically but dependency inversion doesn't exist in the actual object graph.
- Treating every adapter boundary as needing a full DTO mapping layer even when the domain object itself would serialize fine — adding a translation step that exists only to satisfy the pattern, not to protect against an actual foreign model shape.
- Defining a port for a dependency that will never plausibly have a second implementation (a company-internal clock utility, a UUID generator) purely to keep a "one port per dependency" rule, rather than reserving ports for genuine variability points.
