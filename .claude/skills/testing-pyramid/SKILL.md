---
name: testing-pyramid
description: "Reusable knowledge library on test strategy: the test pyramid and its failure mode (the ice-cream cone), Google's test-size axis (small/medium/large) as distinct from test-level naming (unit/integration/e2e), the narrow-vs-broad integration test distinction, microservice/contract testing, ISTQB boundary value analysis, and API-specific testing guidance (test environments, contract testing, zero-trust API testing, versioning). Use when deciding what kind of test a change needs, how many tests to write at each level, how to design edge cases for a validation rule, or how to test a new REST endpoint. Not a style guide for one test framework: a reference for deciding test scope, level, and technique before writing the test."
---

# Testing Strategy Knowledge Library

Reference material, not a checklist to run top to bottom. Most tasks only need
one or two of the files below. Read the decision process, then open the file
that matches the question you actually have.

## How to use this skill

1. Identify what kind of decision you're making: test *level* (unit vs
   integration vs e2e), test *size* (how much real infrastructure it needs),
   edge-case *design* (what inputs to pick), or *API-specific* concerns
   (contract, versioning, security testing).
2. Open the matching reference file below. Don't read all of them for a
   single test.
3. Apply the technique to the actual code under test, not to a textbook
   example. If a reference file's example doesn't map cleanly onto what
   you're testing, say so instead of forcing the fit.
4. Test naming and terminology have no universal industry consensus (Fowler
   makes this explicit in `practical-test-pyramid`). When a term matters,
   define it for the reader in terms of scope and speed, not just a label.

## Decision process

```
What am I testing?
  → A single class/function in isolation, collaborators replaced with
    doubles → unit test (see pyramid-and-test-sizes.md)
  → How this code talks to one real external thing (DB, filesystem, another
    module's API) → narrow integration test (see integration-and-contract-tests.md)
  → A full request/response cycle through the deployed shape of the app →
    broad integration / application / "e2e" test — justify why a narrower
    test can't give the same confidence (see pyramid-and-test-sizes.md,
    "avoiding duplication")
  → Whether two independently-changing sides of a boundary still agree on a
    contract → contract test, not a broader integration test
    (see integration-and-contract-tests.md)

How much real infrastructure does it need to be trustworthy?
  → None / in-process only → small
  → Localhost network, filesystem, multiple threads → medium
  → Real network beyond localhost, real external services → large
  (see pyramid-and-test-sizes.md — this SIZE axis is orthogonal to the LEVEL
  question above; a "unit test" that spins up a real thread pool is not
  small just because someone called it a unit test)

Does the input have an ordered domain (a range, a length, a date, a time
window, a capacity)?
  → Don't enumerate values by hand or pick round numbers. Apply Boundary
    Value Analysis (see boundary-value-analysis.md): partition the domain,
    then test the edges of each partition and their nearest neighbors.

Is this a REST endpoint / public API surface?
  → See api-testing.md for what to cover beyond the happy path: auth/authz
    edge cases, versioning/backward compatibility, contract testing via the
    OpenAPI spec, and where security testing (SAST/DAST/fuzzing) sits
    relative to unit/integration/application testing.
```

## Category index

| File | Content |
|---|---|
| [`pyramid-and-test-sizes.md`](references/pyramid-and-test-sizes.md) | Test Pyramid, ice-cream cone anti-pattern, test doubles, Google's small/medium/large test-size classification, the 70/20/10 ratio debate, why code coverage alone doesn't answer "is this enough" |
| [`integration-and-contract-tests.md`](references/integration-and-contract-tests.md) | Narrow vs broad integration tests, why "integration test" is an ambiguous term, Fowler's testing-glossary terms actually worth knowing, microservice test taxonomy (unit/integration/component/contract/e2e), consumer-driven contract testing |
| [`boundary-value-analysis.md`](references/boundary-value-analysis.md) | Equivalence partitioning, 2-value vs 3-value BVA, the boundary-vs-border distinction, step-by-step technique, the "seamless transition" trap, worked example against this repo's `EventScheduleValidator` |
| [`api-testing.md`](references/api-testing.md) | Test types by layer (unit/integration/application) for APIs specifically, ephemeral vs persistent test environments, test data handling, contract testing via OpenAPI, zero-trust API testing (broken auth/authz, BOLA, replay), SAST/DAST/IAST placement, versioning and backward compatibility |
| [`istqb-glossary.md`](references/istqb-glossary.md) | What ISTQB is and why its glossary is a useful tie-breaker, terms worth borrowing when a discussion about test scope gets stuck on vocabulary |

## Scope note

Like `../software-design/`, this library is deliberately agent-agnostic
content under `.claude/skills/testing-pyramid/`. It's general testing-theory
reference material, not a description of this repo's test setup — for that,
see `test-writer`'s own conventions section, which maps these concepts
onto SIGA's actual stack (JUnit 5, AssertJ, Mockito, Testcontainers,
MockMvc, Timefold `ConstraintVerifier`, Spring Modulith `ModularityTests`).

## Sources

- Fowler, "The Practical Test Pyramid" — martinfowler.com/articles/practical-test-pyramid.html
- Fowler, "TestPyramid" (bliki) — martinfowler.com/bliki/TestPyramid.html
- Fowler, "IntegrationTest" (bliki) — martinfowler.com/bliki/IntegrationTest.html
- Fowler, testing guide index — martinfowler.com/testing/
- Fowler, "Testing Strategies in a Microservice Architecture" — martinfowler.com/articles/microservice-testing/
- Google Testing Blog, "How Much Testing is Enough?" (2021) — testing.googleblog.com/2021/06/how-much-testing-is-enough.html
- Google Testing Blog, "Just Say No to More End-to-End Tests" (2015) — testing.googleblog.com/2015/04/just-say-no-to-more-end-to-end-tests.html
- Google Testing Blog, "Test Sizes" (2010) — testing.googleblog.com/2010/12/test-sizes.html
- ISTQB, "What We Do" — istqb.org/what-we-do/
- Hamburg & Roman, "Boundary Value Analysis According to the ISTQB Foundation Level Syllabus" (2025) — istqb.org (white paper)
- U.S. DoD CTO, "API Technical Guidance — Minimum Viable Capability Release (MVCR) 3" (2026), section 7 (Testing) and Appendix A — cto.mil (technical guidance PDF)
