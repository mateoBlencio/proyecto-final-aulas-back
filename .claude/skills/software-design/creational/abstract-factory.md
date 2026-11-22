# Abstract Factory

## Intent

Provide an interface for creating families of related objects without specifying their concrete classes, guaranteeing that the objects produced together are mutually compatible.

## Problem

A system needs to work with several related products that must be used together as a consistent set (e.g. a UI toolkit's `Button`/`Checkbox`/`Scrollbar` must all come from the same look-and-feel; a cloud-resource layer's `Compute`/`Storage`/`Network` clients must all target the same provider). If client code picks each product independently, it's possible to accidentally mix incompatible members of different families (a Windows `Button` with a macOS `Checkbox`), and switching families requires editing every construction call site.

## When to use

- The application must support multiple families of related products (e.g. per-cloud-provider clients: AWS vs GCP vs Azure implementations of Compute/Storage/Queue) and must switch between an entire family at once, typically via config or environment.
- You need a compile-time or config-time guarantee that products used together are from the same family — mixing is a correctness bug, not just a style choice.
- The set of product *kinds* in a family is stable (rarely changes), but the set of *families* is expected to grow (new theme, new provider, new database dialect).

## When NOT to use

- There is only one product family in practice, and no second one is realistically coming. A `PaymentGatewayFactory` that only ever builds Stripe objects (charge client, refund client, webhook verifier) is premature — a straightforward set of Stripe-specific classes wired by DI is simpler and just as testable.
- Failure scenario: a team builds an `AbstractFactory` with `createA()`/`createB()`/`createC()` for "future" providers that never materialize; two years later there's still exactly one `ConcreteFactory`, and every new engineer has to trace an interface layer to find the one implementation that exists.
- The products aren't actually related/interdependent — if `Button` and `Logger` don't need to be from the same "family," you don't have an Abstract Factory problem, you have several independent Factory Method or plain DI problems being needlessly merged.
- Failure scenario: growing a family (adding one new product kind, e.g. `Tooltip`) requires touching every `ConcreteFactory` in the system, because Abstract Factory couples all product kinds to all families up front — if product kinds change more often than families, this pattern inverts the axis of change you actually have and increases the maintenance surface.
- In a Spring context, if family selection is really just "which set of `@Bean`s should be active," Spring profiles/`@ConditionalOnProperty` config classes often replace this pattern with less boilerplate.

## Structure

Participants: `AbstractFactory` (declares creation methods for each product kind), `ConcreteFactory` (one per family, implements all creation methods consistently), `AbstractProduct` (one interface per product kind), `ConcreteProduct` (family-specific implementation).

```java
interface CloudFactory {
    ComputeClient createCompute();
    StorageClient createStorage();
}

class AwsCloudFactory implements CloudFactory {
    public ComputeClient createCompute() { return new Ec2ComputeClient(); }
    public StorageClient createStorage() { return new S3StorageClient(); }
}

class GcpCloudFactory implements CloudFactory {
    public ComputeClient createCompute() { return new GceComputeClient(); }
    public StorageClient createStorage() { return new GcsStorageClient(); }
}

// client depends only on CloudFactory, never on Aws*/Gcp* concretes
class Provisioner {
    private final CloudFactory factory;
    Provisioner(CloudFactory factory) { this.factory = factory; }

    void provision() {
        factory.createCompute().launch();
        factory.createStorage().createBucket();
    }
}
```

## Consequences

Benefits: guarantees product compatibility within a family by construction; isolates concrete classes from client code entirely; swapping an entire family is a one-line change (swap the factory instance, usually via config/DI); makes it easy to add a whole new family without touching existing client code.

Costs: adding a new product *kind* to the family requires changing the `AbstractFactory` interface and every `ConcreteFactory` implementation — a ripple effect across all families; more interfaces and classes than a direct-construction approach, which is real cognitive overhead for a small number of families; can feel like ceremony when there's only one or two families and they rarely change.

## Related patterns

- [Factory Method](factory-method.md) — Abstract Factory is frequently implemented as a set of Factory Methods, one per product kind; the difference is Abstract Factory guarantees family-wide consistency, Factory Method does not.
- [Builder](builder.md) — solves a different problem (step-by-step construction of one complex object) but is sometimes combined with Abstract Factory when each family's products themselves need multi-step construction.
- [Bridge](../structural/bridge.md) — both decouple abstraction from implementation, but Bridge is about letting the two vary independently at runtime, not producing consistent families.
- [Dependency Inversion](../principles/solid.md) — Abstract Factory is a structural embodiment of depending on abstractions, not concretions, at the family level.

## Smells that suggest this pattern

- Code that constructs several related objects together, repeated across the codebase, where at each site all the objects must consistently come from the same vendor/theme/dialect.
- Bugs caused by mixing objects from different families (e.g. a repository built against a Postgres dialect handed a MySQL connection factory).
- A growing set of `if (provider == AWS) { ... } else if (provider == GCP) { ... }` blocks that each construct a matching cluster of objects.

## Common misuse

- Introducing Abstract Factory for a single-family system in anticipation of multi-tenancy or multi-provider support that isn't committed to (speculative generality).
- Using Abstract Factory when only one product varies — that's just Factory Method wearing a bigger costume.
- Letting the "family" boundary leak: a `ConcreteFactory` that creates most products from one vendor but reaches into another vendor's classes for one product kind defeats the entire guarantee the pattern exists to provide.
