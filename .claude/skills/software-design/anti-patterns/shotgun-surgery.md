# Shotgun Surgery

## Intent

A single conceptual change requires touching many unrelated classes/files in small ways, because a piece of knowledge or logic that should live in one place is duplicated or fragmented across the codebase.

## Problem

Small, conceptually simple changes ("add a new order status," "add a field to the pricing calculation") turn into PRs touching 15-30 files, which makes review superficial (nobody reads 25 one-line diffs carefully) and makes it easy to miss one of the spots that needed updating — producing a bug that only shows up for the one call site nobody remembered to change. Estimation becomes unreliable because a change that "should" be small (it's one concept) takes disproportionately long because of how scattered its implementation is. It also actively discourages making the change correctly: under time pressure, people update the 3 places they know about and skip the 2 they don't remember, so the codebase accumulates inconsistent partial updates over time. New team members can't predict where to make a change because the same concept doesn't have one canonical home.

## How it develops

1. A concept (an enum of order statuses, a business rule like "orders over $500 need manager approval") is introduced. It's naturally handled inline, at the one call site that needs it.
2. A second call site needs the same rule. Rather than extracting it to a shared location, it's copy-pasted — the original context is quick to reproduce, and creating a shared abstraction for "just two places" feels premature (see [Premature Abstraction](premature-abstraction.md) for the mirror-image trap).
3. A third, fourth, fifth call site each add their own copy, sometimes with small drifted variations (one checks `> 500`, another checks `>= 500`) because nobody is looking at the existing copies when writing a new one — each author only sees their own local context.
4. Nobody owns "the concept" as a first-class thing in the codebase — it exists only as a pattern repeated across many places, discoverable only by grep, not by navigating to a definition.
5. Once it's scattered across 10 places, consolidating it becomes its own project (verify all 10 copies actually agree, pick the canonical behavior, migrate every call site) — which is scarier and more time-consuming than continuing to add an 11th copy when the next call site appears.

This is frequently the result of over-applying "avoid premature abstraction" advice — teams that got burned by over-engineering earlier swing too far toward inlining everything, and never circle back once a real duplication pattern has emerged.

## How to recognize it

- Git churn / co-change analysis: files that are modified together in the same commit or PR repeatedly, especially for small, single-concept changes — `git log` history shows the same set of 8-10 files appearing together across many otherwise-unrelated commits (tools: `git log --stat`, or co-change analysis via `git-of-theseus` or similar).
- Grep-detectable duplication: the same literal (a magic number, a status string, a threshold) appears in many files with no shared constant/enum backing it — e.g., `grep -rn "\"PENDING_APPROVAL\""` returning hits in 12 files instead of one enum definition plus its usages.
- PR size pattern: PRs that are wide (many files) but shallow (few lines changed per file) for what the PR description calls "one change."
- Bug pattern: a bug fix report says "we fixed it in service A but forgot service B did the same check" — recurring "we forgot the other copy" incidents are a strong signal.
- No canonical definition: searching for where a business rule or concept is "defined" turns up no single file/class — only usages.
- Change amplification ratio: informally, the ratio of "files touched" to "distinct concepts changed" for recent PRs; consistently high ratios (a single-concept change reliably touching 5+ files) is the smell.

## Remedy

- Identify the actual concept being duplicated (a business rule, a status enum, a formatting rule) and give it one canonical home — a class, function, or constant that every call site delegates to instead of reimplementing.
- If the duplication is a business rule with variants, [Strategy](../behavioral/strategy.md) or a simple rule object can centralize it while still allowing controlled variation.
- If the duplication is about *notifying* many parts of the system when one thing changes (rather than reimplementing logic), consider [Observer](../behavioral/observer.md) or an event-driven approach ([Event-Driven](../architectural/event-driven.md)) so the "many places" become subscribers to one source of truth instead of hand-synchronized copies.
- If the scattering is across module/layer boundaries (e.g., the same validation logic reimplemented in the UI, the API layer, and the DB layer), consider whether a [Facade](../structural/facade.md) or a shared domain service should own it, with other layers calling through rather than duplicating.
- Migrate incrementally: introduce the canonical implementation, redirect one call site at a time to delegate to it, and only remove the old duplicated logic once all call sites are migrated and tests confirm equivalence. Don't do a single sweeping find-and-replace across 15 files in one PR — the whole point of the problem (many small independent changes are risky) applies just as much to the migration itself.
- Add a regression test for the consolidated logic once it exists in one place, so future changes only need to update it once and get confidence from one test suite instead of manually re-verifying every call site.

## When the "fix" is worse than the disease

- Two or three call sites that look similar today but represent genuinely different business concepts that happen to share a threshold value right now (e.g., a "$500 manager approval" rule for orders and an unrelated "$500 fraud review" rule for refunds). Merging them into one shared constant/function couples two policies that could diverge tomorrow for entirely different reasons — a change to fraud policy would then risk regressing order approval. Verify the copies are actually the *same concept*, not a coincidence, before consolidating.
- Cross-cutting formatting/boilerplate that's genuinely trivial and unlikely to change (e.g., a specific null-check pattern repeated in 10 places). Extracting a shared helper for something this small can cost more in indirection (one more file to open, one more name to remember) than it saves, especially if the 10 occurrences are stable and none has ever needed to change.
- Early-stage code where the "many places" haven't stabilized yet — if the feature area is still being actively designed and call sites are being added/removed weekly, consolidating now risks building the wrong abstraction around a shape that hasn't settled. Wait for the change pattern to repeat 3+ times with real evidence before extracting (see [Premature Abstraction](premature-abstraction.md)).
- Deliberately decoupled bounded contexts in a modular monolith or microservices architecture, where each module intentionally keeps its own copy of a small piece of shared knowledge (e.g., each service has its own `OrderStatus` enum) specifically to avoid a shared-library coupling that would force synchronized deployments. This looks like shotgun surgery from a single-repo view but is a deliberate trade-off for independent deployability — see [Anti-Corruption Layer](../integration/anti-corruption-layer.md).

## Related patterns

- [Premature Abstraction](premature-abstraction.md) — the opposite failure mode; overcorrecting shotgun surgery by extracting on the first duplication (rather than the third) risks building the wrong shared abstraction.
- [God Object](god-object.md) — the opposite concentration failure (too much in one place instead of too little); useful to check a consolidation didn't overshoot into a god object.
- [DRY](../principles/dry.md) — the principle shotgun surgery violates, with the caveat that DRY is about knowledge duplication, not incidental code similarity.
- [Observer](../behavioral/observer.md), [Event-Driven](../architectural/event-driven.md) — remedies when the scattering is about propagating a change to many interested parties rather than reimplementing logic.
- [Strategy](../behavioral/strategy.md) — remedy when the duplicated logic has legitimate variants that need a controlled extension point.

## Real-world example shape

```java
// OrderService.java
if (order.getTotal().compareTo(new BigDecimal("500")) > 0) {
    order.setRequiresApproval(true);
}

// RefundService.java
if (refund.getAmount().compareTo(new BigDecimal("500")) >= 0) { // drifted: >= vs >
    refund.setRequiresApproval(true);
}

// BulkOrderImportJob.java
if (row.getTotal().doubleValue() > 500.0) { // drifted: no shared constant, different type
    row.markForApproval();
}
```

Same business rule ("orders/refunds over $500 need approval"), three independent implementations, already drifted (`>` vs `>=`), no single place to look up or change the threshold.
