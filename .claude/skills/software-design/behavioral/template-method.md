# Template Method

## Intent

Define the fixed skeleton of an algorithm in a base class, deferring specific steps to subclasses, so the overall sequence stays consistent while individual steps vary.

## Problem

Several classes implement the same overall process (validate, then transform, then save; connect, then authenticate, then query) but differ in one or two specific steps. Duplicating the whole sequence in each class means the shared parts drift out of sync as bugs get fixed in one copy and not the others, and there's no single place enforcing that all implementations follow the same order.

## When to use

- Multiple classes share the same overall algorithm structure/order but need to customize specific, well-defined steps.
- You want to guarantee a fixed sequence (e.g., "always validate before saving," "always open a transaction before running the query and close it after") that subclasses cannot accidentally skip or reorder.
- The shared logic (logging, error handling, transaction boundaries) around the varying step is substantial enough that duplicating it per-variant would be a real maintenance burden.
- You're working in a single-inheritance OO language and the variation is naturally expressed as "override this one method," not "compose this behavior at runtime."

## When NOT to use

- The steps that vary need to be selected or swapped at runtime rather than fixed per-subclass at compile time — that's [Strategy](strategy.md) (composition) rather than Template Method (inheritance). Template Method locks the variant in via subclassing; if you need to change which variant an object uses after construction, you're fighting the pattern.
- The "shared skeleton" isn't actually shared — each implementation only nominally follows the same step names but the real logic and ordering diverge enough that forcing them into one base class produces a base class riddled with hook methods nobody consistently overrides the same way, and empty/no-op overrides everywhere.
- You need multiple, independent axes of variation (e.g., "which storage backend" AND "which validation rule," combined). Inheritance only gives you one axis; composition-based patterns (Strategy, Decorator) compose more cleanly than a deep, multi-branched subclass hierarchy trying to cover every combination.
- The language/team favors composition over inheritance as a matter of policy (common in Go, and increasingly in Java/Kotlin teams reacting against deep hierarchies) — a higher-order function taking the varying step as a parameter often achieves the same guarantee without the base-class coupling.

**Failure scenarios:**
1. A `BaseReportGenerator` template method defines `generate()` as `fetchData() → format() → export()`, and over two years, subclasses accumulate constructors that need to override `fetchData()` differently per data source *and* `export()` differently per output format *and* sometimes both — resulting in a combinatorial explosion of subclasses (`CsvSalesReport`, `PdfSalesReport`, `CsvInventoryReport`, `PdfInventoryReport`...) that composition (injecting a fetcher and an exporter) would have avoided entirely.
2. A team locks a payment processing flow into a Template Method (`validate() → charge() → notify()`) assuming all payment providers follow this exact order, then a new provider requires a step in between (`reserveFunds()`) that doesn't fit any existing hook — the base class needs modification (violating Open/Closed) or the new provider has to fake the missing step, because the skeleton was locked in too early, before enough real variants existed to know what actually varies.

## Structure

- **AbstractClass**: defines the template method (often `final`) that calls the fixed sequence of steps, some concrete (shared), some abstract or overridable (varying).
- **ConcreteClass**: overrides the varying steps (hooks), leaving the skeleton untouched.

```java
abstract class DataImportJob {
    final void run() {
        List<Row> rows = readSource();
        List<Row> valid = rows.stream().filter(this::isValid).toList();
        persist(valid);
        logSummary(rows.size(), valid.size());
    }

    abstract List<Row> readSource();
    abstract boolean isValid(Row row);

    private void persist(List<Row> rows) { repository.saveAll(rows); }
    private void logSummary(int total, int valid) { log.info("{}/{} rows imported", valid, total); }
}

class CsvImportJob extends DataImportJob {
    List<Row> readSource() { return csvReader.readAll(path); }
    boolean isValid(Row row) { return row.hasRequiredFields(); }
}
```

## Consequences

**Benefits:**
- Shared sequence and shared logic (logging, error handling, transaction boundaries) live in exactly one place.
- The order of steps is structurally enforced — subclasses can't accidentally run them out of order or skip a mandatory step.
- New variants are added by subclassing and overriding hooks, without touching the shared skeleton.

**Costs:**
- Locks variation into inheritance: a subclass can't easily change its "variant" at runtime, and can't mix and match steps from two different template hierarchies.
- If a genuinely new step is needed that the original skeleton didn't anticipate, adding it means modifying the base class — which affects every existing subclass, a real Open/Closed violation risk.
- Base classes tend to accumulate hook methods over time, and it becomes hard to tell, from a subclass alone, what the actual full sequence is without reading the base class too (the classic "yo-yo" navigation problem of deep hierarchies).
- Testing a subclass in isolation still exercises the full base-class skeleton, which can make unit tests of just "the varying step" awkward without also invoking the whole flow (or requires exposing the step as independently testable, defeating some of the encapsulation).

## Related patterns

- [Strategy](strategy.md) — the composition-based alternative: instead of subclassing to override one step, inject an object/function for the varying part. Prefer Strategy when the variant needs to be chosen or swapped at runtime, or when there are multiple independent axes of variation. Prefer Template Method when the sequence itself needs to be structurally locked in and inheritance is otherwise a natural fit.
- [Factory Method](../creational/factory-method.md) — frequently one of the "steps" inside a Template Method (the skeleton calls an overridable factory-method hook to create a needed object); Factory Method is a special case of the hook-method idea, scoped just to object creation.
- [Chain of Responsibility](chain-of-responsibility.md) — also structures a sequence of steps, but each handler decides independently whether to continue, versus Template Method's skeleton always running every step in the same base-defined order.

## Smells that suggest this pattern

- Several classes with near-identical methods that differ by only one or two lines in the middle — a sign the shared parts should move to a base class's template method and only the differing lines should be overridden.
- Copy-pasted "boilerplate" comments like `// same as XImporter but with different validation` scattered across sibling classes.
- Bug fixes that need to be applied in multiple near-duplicate classes because the shared sequence isn't actually shared in code, just shared in intent.

## Common misuse

- Forcing every conceivable variant into one base class's fixed skeleton before enough real variants exist to know what actually varies, resulting in a base class riddled with hooks that don't cleanly fit new cases (see failure scenario 2) — better to wait for 2-3 real implementations before extracting the template.
- Using Template Method where the "variation" is really about swapping strategies at runtime per request/config — this needs Strategy's composition, not inheritance's compile-time binding.
- Deep, multi-level Template Method hierarchies (subclass of a subclass of a subclass, each adding another hook) that make it necessary to read 3+ files to understand what a single concrete class actually does end to end.
