# Spaghetti Code

## Intent

Code with no discernible control-flow structure — branches, loops, and jumps tangle together so that tracing what happens for a given input requires following execution rather than reading the shape of the code.

## Problem

Bugs get fixed by patching the specific symptom because nobody can trust that a structural change won't break some other path that shares the same tangled logic. Adding a new case means finding the right spot among a dozen nested conditionals, and it's easy to miss one of the three other places the same condition is (almost, but not quite) duplicated. Onboarding is slow because there's no "read the method signature and trust the abstraction" shortcut — every reviewer has to mentally execute the code to know what it does. Test coverage is typically low or misleading, because each test has to set up a huge number of preconditions to reach a specific branch, so most branches simply aren't tested. Deploys are fragile because a change intended for one code path silently affects a sibling path that shares mutable state or a shared conditional.

## How it develops

1. A function starts as a straightforward sequence of steps for the common case.
2. An edge case appears — add an `if`. Another edge case — add an `else if` inside the first branch. Each addition is locally the smallest possible diff.
3. Under deadline pressure, the "clean" fix (extract the edge case into its own function, or restructure the conditional) feels riskier than just adding one more branch to code that already works for the main case — nobody wants to be the one who breaks the happy path while refactoring around a deadline.
4. State that should be local (a flag, an accumulator) gets hoisted to a wider scope because two nested loops both need to read/write it, and passing it as a parameter felt like more ceremony than just reaching for the outer variable.
5. Copy-paste compounds it: instead of extracting the tangled block into a function, a near-duplicate is pasted a few lines down with one condition tweaked, because understanding the original well enough to parameterize it looks harder than duplicating and adjusting.
6. By the time the function is a few hundred lines with six levels of nesting, nobody can confidently untangle it in one sitting, so every subsequent change is another patch on top rather than a rewrite — the sunk cost of understanding compounds the same way the code did.

## How to recognize it

- Cyclomatic complexity: a method with cyclomatic complexity above ~10-15 (most static analyzers, e.g. SonarQube/PMD, flag this by default) is a strong candidate.
- Nesting depth: more than 3-4 levels of nested `if`/`for`/`while` in one method.
- Method length combined with branch density: 100+ line methods where a large fraction of lines are conditionals rather than straight-line calls.
- `goto`-equivalents: heavy use of early `return`/`continue`/`break` combined with boolean flags that simulate jumps (`boolean shouldSkip = true; ... if (shouldSkip) continue;` scattered across a loop body).
- Shared mutable state across branches: variables declared before a conditional block and mutated inside multiple, non-obviously-related branches, then read again after the block.
- Duplicate-but-not-quite blocks: near-identical code blocks (detectable with copy-paste/clone detectors like PMD CPD or jscpd) that differ by one condition or literal — a sign that branching logic was duplicated instead of parameterized.
- Low test coverage concentrated in one file, or tests that require a long, brittle setup sequence to reach a specific branch.
- Difficulty describing the method in one sentence — if you can't summarize what it does without "and" and "but if X then" more than twice, it's a candidate.

## Remedy

- Extract guard clauses first — invert deeply nested conditionals into early returns to flatten the structure before touching the actual logic. This is low-risk and immediately reduces nesting depth.
- Extract named functions for each cohesive block, even before deciding on a bigger structural pattern — a well-named function replaces a comment and turns a paragraph of code into a single readable line at the call site.
- If the branching is dispatching on a type/enum to select an algorithm, replace it with [Strategy](../behavioral/strategy.md) — one class per branch, selected via a map or factory instead of an `if` chain.
- If the branching represents an object moving through a lifecycle (`PENDING → APPROVED → SHIPPED`), that's a [State](../behavioral/state.md) machine, not conditional logic scattered through one method — model the states explicitly.
- If the tangle comes from a long sequential process with optional steps, consider [Chain of Responsibility](../behavioral/chain-of-responsibility.md) or a pipeline of small, independently testable steps.
- Reduce shared mutable state: convert loop-scoped flags/accumulators into return values or small immutable records passed between extracted functions.
- Cost: untangling spaghetti code by rewriting from scratch is high-risk without a safety net — write characterization tests (tests that pin down current behavior, even if that behavior is itself questionable) *before* refactoring, so you can tell a behavior change from a refactor. Skipping this step and refactoring "by eye" is how spaghetti code turns into spaghetti code with new bugs.

## When the "fix" is worse than the disease

- A one-off data migration script or a build tool config file that runs once, is thrown away after use, and that nobody will maintain — investing in extracting clean abstractions here is pure overhead. Get it correct, not clean.
- Performance-critical hot-path code where the "tangled" structure is actually a deliberately flattened, branch-predictor-friendly implementation (common in parsers, codecs, game engines). Splitting it into many small polymorphic calls can hurt performance meaningfully (virtual dispatch, loss of inlining) for gains that are purely aesthetic. Profile before restructuring code that looks spaghetti-like but sits in a hot loop.
- Regulatory or scientific code that encodes a genuinely irreducible decision table (e.g., tax brackets with many jurisdiction-specific exceptions) — the branching complexity reflects real-world complexity, not poor structure. Restructuring it into polymorphic classes per branch can make it *harder* to verify against the source regulation, whose own structure is a flat table of conditions. Sometimes a well-commented, exhaustively-tested `if` chain that mirrors the external spec's structure is the most maintainable option, even if it scores badly on complexity metrics.

## Related patterns

- [God Object](god-object.md) — often co-occurs; a god object's individual methods are frequently spaghetti because there's no pressure toward decomposition at any level.
- [Premature Abstraction](premature-abstraction.md) — the overcorrection risk; don't introduce Strategy/State/Chain of Responsibility for a 2-branch conditional that will never grow.
- [Strategy](../behavioral/strategy.md), [State](../behavioral/state.md), [Chain of Responsibility](../behavioral/chain-of-responsibility.md) — the usual structural remedies depending on what the branching represents.
- [KISS](../principles/kiss.md) — the guiding principle: the goal of untangling is a simpler structure, not a maximally "patterned" one.

## Real-world example shape

```java
public String processOrder(Order order, User user, boolean isRetry) {
    String result = "";
    if (order != null) {
        if (order.getStatus().equals("PENDING")) {
            if (user.isVerified()) {
                if (!isRetry) {
                    if (order.getItems().size() > 0) {
                        for (Item item : order.getItems()) {
                            if (item.getStock() > 0) {
                                result = "OK";
                            } else {
                                if (order.hasBackorderFlag()) {
                                    result = "BACKORDER";
                                } else {
                                    result = "FAIL";
                                    break;
                                }
                            }
                        }
                    } else {
                        result = "EMPTY";
                    }
                } else {
                    result = "RETRY_BLOCKED";
                }
            } else {
                result = "UNVERIFIED";
            }
        } else {
            result = "BAD_STATUS";
        }
    }
    return result;
}
```

Six nesting levels, a shared mutable `result` written from multiple branches, and a loop whose exit condition depends on a flag set two levels up — tracing any single outcome requires reading the whole method.
