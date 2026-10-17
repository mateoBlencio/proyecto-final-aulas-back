# Circular Dependency

## Intent

Two or more modules/classes/packages depend on each other, directly or transitively, such that neither can be understood, built, tested, or deployed independently of the other.

## Problem

Neither side of a cycle can be compiled, tested, or reasoned about in isolation — understanding module A requires already understanding module B, which requires already understanding module A. Build tools either reject the cycle outright (many module systems, e.g. Java Platform Module System, Spring Modulith's module verification) or silently allow it while making incremental compilation and dependency-based test selection impossible, since a change to either module invalidates both. Unit testing one side requires mocking or standing up the other side, and if the cycle is deep, "mocking the other side" means mocking half the system. It also blocks extraction: a module in a cycle cannot be pulled into its own deployable service or library, because it cannot be exercise-tested or built without the module it's tangled with — the two are one logical component wearing two file names. Onboarding is harder because there's no valid "read this module first, then that one" order.

## How it develops

1. Module A calls into Module B for some functionality — a normal, one-directional dependency.
2. Later, B needs a small piece of data or behavior that happens to live in A (a lookup, a callback, a shared type). Rather than moving that piece somewhere both can reach, or introducing an interface/event boundary, the fastest fix is to have B import A directly — it's one line, and the alternative (extracting a shared module, or inverting the call via an interface) looks like a bigger, scarier change for what feels like a small need.
3. The cycle compiles fine in most languages (Java package cycles, Python circular imports resolved lazily, JS/TS with bundler-level cycle tolerance), so there's no forcing function to notice — no error, no red build, just a working feature.
4. Once the cycle exists, it's self-reinforcing: the next engineer who needs *anything* from either module sees that "A and B already depend on each other" and adds one more cross-reference without hesitation, since the architectural boundary is already broken — there's no longer a norm to preserve.
5. In package/module-based architectures (e.g., a Spring Modulith setup), this often starts as a "just this once" call from one bounded context's internal class into another context's internal class, bypassing the module's public API/named interface, because the needed data is *right there* and the proper channel doesn't exist yet.

## How to recognize it

- Build/compiler errors: languages and module systems that reject cycles (Java modules, Spring Modulith's `ApplicationModules.verify()`, ES modules with certain bundler configs) will fail fast — treat these failures as the signal, not an obstacle to route around.
- Import graph analysis: run a dependency graph tool (`jdeps` for Java, `madge`/`dpdm` for JS/TS, `pydeps` for Python, ArchUnit's `freeOfCycles()` rule) and look for strongly connected components with more than one node — any SCC of size ≥2 in the package/module graph is a cycle by definition.
- Manual grep check: `grep -rl "import com.company.moduleB" src/moduleA` returning hits, combined with `grep -rl "import com.company.moduleA" src/moduleB` also returning hits, confirms a direct two-module cycle. Longer cycles (A→B→C→A) require the graph tool, not grep.
- Test setup complexity: a unit test for a class in module A that requires constructing real or mocked objects from module B, whose own constructor requires objects from module A, is a runtime symptom of the same problem.
- Package layering violations: if the codebase claims a layered architecture (e.g., `domain` should never depend on `infrastructure`), a cycle typically shows up as a "back-edge" — a lower layer importing from a layer that's supposed to sit above it.
- Deployment/extraction attempts surfacing it: an attempt to split a module into its own service or library fails because the build can't resolve without also pulling in the module it's cyclically tied to.

## Remedy

- Determine the direction the dependency *should* go, based on which module is conceptually more stable/foundational, then break the cycle by removing the wrong-direction edge.
- If B needs something from A but shouldn't depend on A directly, introduce an interface owned by B (or a shared, lower-level module) and have A implement it — this is [Dependency Inversion](../principles/solid.md) applied directly: both now depend on an abstraction instead of on each other.
- If the coupling is about needing to *react* to something happening in the other module rather than needing to *call into* it, replace the direct call with an event: A publishes, B subscribes, no compile-time dependency from A to B. This is the standard remedy in module systems like Spring Modulith (`@ApplicationModuleListener`) and is generally the right fix for cross-bounded-context coupling.
- If both modules genuinely need a shared concept (a type, a small piece of logic), extract it into a third, lower-level module that both depend on — but only if the shared piece is real and stable, not as a reflexive "extract a common module" move (see [Premature Abstraction](premature-abstraction.md)).
- Use a [Facade](../structural/facade.md) at a module boundary to ensure only the intended public surface is depended upon, making an accidental reverse-dependency (an internal class import) structurally harder to introduce again.
- Cost: untangling an established cycle is rarely a one-line fix — it typically requires redesigning which side owns the shared concept, which can mean moving code, changing constructors, and updating every call site that assumed the old direct-call structure. Do it as a deliberate, reviewed refactor with the dependency graph re-verified afterward (`ArchUnit`/`jdeps`/module-boundary tests passing), not as a quick patch — a rushed cycle-break can just move the cycle one level down (e.g., into two new sub-modules) instead of actually eliminating it.

## When the "fix" is worse than the disease

- Two classes that are conceptually one unit split across two files purely for organizational reasons (e.g., a `Node` and a `Tree` that reference each other, or a parent-child bidirectional association like `Order` ↔ `OrderLine` in the same aggregate). These aren't really "two modules depending on each other" — they're one cohesive concept, and forcing a one-directional dependency (e.g., making `OrderLine` unaware of `Order`) can make the API more awkward without buying independent testability, since neither will ever be used, tested, or deployed without the other. Cycles *within* a single aggregate/bounded context are usually fine; cycles *across* module or bounded-context boundaries usually aren't.
- Two tightly co-evolving modules where every change to one always requires a matching change to the other anyway (they're deployed together, released together, owned by the same team). In this case a cycle is an honest reflection of reality, and splitting it apart with an interface/event layer adds ceremony (a new interface, an event type, a subscription) that doesn't buy independent evolution because the modules never actually evolve independently. If untangling them would require designing a public contract for a boundary nobody actually needs yet, it may be better to merge them into one module and stop pretending there's a boundary — see [premature-abstraction.md](premature-abstraction.md) for the general trap of building a seam before it's needed.
- Cycles resolved automatically and cheaply by the language/runtime with no observed pain (e.g., mutually recursive functions within the same file, or Python's lazy import resolution handling a cycle that's never actually been a build or test problem in practice). Not every graph cycle rises to the level of an architectural problem — the bar is "does this cause a build failure, a test pain point, or block a deployment/extraction goal," not "does a graph tool flag it."

## Related patterns

- [God Object](god-object.md) — a different coupling failure (too much responsibility in one place vs. two places pulling on each other); can coexist when a god object is one side of a cycle.
- [Premature Abstraction](premature-abstraction.md) — the risk of overcorrecting a cycle by introducing an interface/event layer for a boundary that doesn't actually need independence.
- [Dependency Inversion](../principles/solid.md) (part of SOLID) — the principle that directly resolves most cross-module cycles.
- [Observer](../behavioral/observer.md), [Event-Driven](../architectural/event-driven.md) — the standard remedy for cross-module reactive coupling without a compile-time cycle.
- [Facade](../structural/facade.md) — narrows a module's public surface so only the intended dependency direction is even reachable.

## Real-world example shape

```java
// package com.siga.academic
public class Subject {
    public Commission getCommission(Long commissionId) {
        return commissionService.findById(commissionId); // depends on package com.siga.allocation
    }
}

// package com.siga.allocation
public class Commission {
    public Subject getSubject(Long subjectId) {
        return subjectService.findById(subjectId); // depends on package com.siga.academic
    }
}
```

`academic` imports `allocation` and `allocation` imports `academic` — neither package can be built, tested, or reasoned about without the other; a module boundary tool (e.g., Spring Modulith's `ApplicationModules.verify()`) would fail on this pair.
