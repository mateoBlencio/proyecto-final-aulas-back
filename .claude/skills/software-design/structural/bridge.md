# Bridge

## Intent

Decouple an abstraction from its implementation so the two can vary and evolve independently.

## Problem

A class has two (or more) genuinely orthogonal dimensions of variation — e.g., a shape that can be rendered by different renderers, a message that can be sent over different channels. Modeling both dimensions with inheritance produces a combinatorial explosion of subclasses: `RedCircle`, `BlueCircle`, `RedSquare`, `BlueSquare`, and so on, one class per combination.

## When to use

- Two dimensions genuinely vary independently, and you need to combine any abstraction with any implementation at runtime (e.g., `Shape` × `Renderer`, `Notification` × `DeliveryChannel`).
- You want to avoid permanently binding an abstraction to one implementation, so the implementation can be swapped without touching abstraction code (and vice versa).
- Subclassing along both axes is already producing (or about to produce) a naming pattern like `PdfInvoiceExporter`, `CsvInvoiceExporter`, `PdfReceiptExporter`, `CsvReceiptExporter`.
- A library needs to support multiple platform-specific backends (e.g., a windowing toolkit abstraction over different OS drawing APIs) that must both be extensible independently by different teams.
- You need to bind the implementation at runtime or via configuration/DI rather than at compile time through inheritance.

## When NOT to use

- Only one implementation exists and there's no concrete second one on the roadmap. Bridge adds an abstraction/implementor split for a variability that doesn't exist yet — pure YAGNI cost with no present benefit.
- The two "dimensions" aren't actually orthogonal — if changing the implementation always forces a change in the abstraction too, they're really one axis; plain inheritance or composition is simpler and more honest.
- **Failure scenario 1:** a `PaymentProcessor` with exactly one payment gateway gets split into `PaymentProcessor` (abstraction) + `PaymentGatewayImplementor` (implementor) interfaces "to future-proof for a second gateway." No second gateway materializes for years; the codebase carries double the types and double the indirection for a hypothetical that never paid off.
- **Failure scenario 2:** a `Shape` hierarchy is bridged against a `Renderer` implementor because "we might add a PDF renderer later," when the app only ever renders to SVG. Every new shape now requires touching two hierarchies instead of one, for a variability axis that was speculative.

## Structure

`Abstraction` holds a reference to an `Implementor` and delegates the low-level work to it. `RefinedAbstraction` extends `Abstraction` with more specific behavior. `ConcreteImplementorA/B` implement `Implementor` differently.

```java
interface Renderer {                         // Implementor
    void renderCircle(double radius);
}

class SvgRenderer implements Renderer {
    public void renderCircle(double radius) { /* emit SVG */ }
}

class RasterRenderer implements Renderer {
    public void renderCircle(double radius) { /* rasterize pixels */ }
}

abstract class Shape {                        // Abstraction
    protected final Renderer renderer;
    Shape(Renderer renderer) { this.renderer = renderer; }
    abstract void draw();
}

class Circle extends Shape {                  // RefinedAbstraction
    private final double radius;
    Circle(Renderer renderer, double radius) { super(renderer); this.radius = radius; }
    void draw() { renderer.renderCircle(radius); }
}
```

## Consequences

**Benefits:** avoids subclass explosion across two independent axes; implementation swappable at runtime; abstraction and implementation evolve and test independently (mock the implementor); new implementations don't require touching abstraction code.

**Costs:** more types and indirection upfront, even before any real payoff is realized; a reader looking for "the code" has to jump between abstraction and implementor; the pattern only pays off if the two axes were correctly identified as orthogonal — getting that wrong is expensive to unwind later; onboarding cost is higher since new contributors must understand the split before they can confidently modify either side.

## Related patterns

- [Adapter](adapter.md) — Adapter is retrofitted after the fact to reconcile two interfaces that already exist and weren't designed together; Bridge is designed upfront so abstraction and implementation can diverge on purpose.
- [Strategy](../behavioral/strategy.md) — structurally similar (object composition to vary behavior), but Strategy varies a single algorithm at one point; Bridge deliberately maintains two parallel hierarchies (abstraction and implementation) that both may grow.
- [Abstract Factory](../creational/abstract-factory.md) — often used to construct the correct `Implementor` for a given `Abstraction`, without hardcoding the pairing.

## Smells that suggest this pattern

- Parallel class hierarchies with a naming pattern crossing two concerns, e.g. `RedCircle`/`BlueSquare`.
- A `switch` or `instanceof` chain combining two independent enum-like dimensions in one place.
- Subclasses that differ only in "how" something is done, never in "what" is being modeled.
- Adding a new backend/implementation requires touching every existing abstraction subclass instead of just adding one new implementor class.
- Test doubles for "how" logic can't be swapped in without also swapping "what" logic, because the two are fused into one class.

## Common misuse

- Introducing Bridge speculatively for a hypothetical second implementation that isn't concretely planned — a YAGNI violation that doubles the type count for no current benefit.
- Calling any single injected dependency a "Bridge" — plain constructor injection of one collaborator is not Bridge; Bridge specifically maintains two hierarchies (abstraction and implementor) that both can have multiple concrete variants.
