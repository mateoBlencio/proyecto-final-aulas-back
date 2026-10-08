# Integration Tests Are an Ambiguous Term — Be Specific

## Fowler's definition, and why the term causes arguments

`IntegrationTest` (bliki): a test that checks whether independently
developed units of software work correctly when connected to each other.
Fowler flags this as one of the most disputed terms in testing, for three
reasons:

1. **Historical conflation**: 1980s waterfall practice bundled "does module
   A work with module B" and "does the whole system work" under the same
   name.
2. **Solitary vs. sociable unit tests**: teams that isolate every
   collaborator with a test double call anything that doesn't do that an
   "integration test"; teams that allow real collaborators in a unit test
   draw the line somewhere else entirely.
3. Different teams simply prioritize different scopes and never noticed
   they mean different things by the same word.

## Narrow vs. broad — the distinction that actually matters

- **Narrow integration test**: exercises the interaction code for *one*
  integration point (e.g. this repository class against a real Postgres via
  Testcontainers), using test doubles for everything else. Fast, focused,
  comparable to a unit test in feedback speed.
- **Broad integration test**: needs live, working versions of most/all
  collaborating services. Expensive infrastructure, exercises real
  cross-service code paths, closer to a system test.

Fowler's recommendation: when the scope is broad, call it a **system test**
or **end-to-end test** instead of "integration test" — reserve "integration
test" (explicitly "narrow integration test" if there's any doubt) for the
one-integration-point case. Pair narrow integration tests with **contract
tests** to get confidence about the broader system without paying for a
broad integration test on every run.

## Glossary terms worth knowing (from `martinfowler.com/testing/`)

Only pull these in when a term genuinely disambiguates a discussion — don't
relabel existing SIGA tests to match this vocabulary for its own sake.

- **Component test**: tests a substantial part of the system in isolation,
  driven through its own API, with its own dependencies stubbed. Bigger
  than a unit test, smaller than a full end-to-end test.
- **Contract test**: verifies that a provider and a consumer of an
  interface still agree on its shape and behavior, using recorded
  expectations rather than a live counterpart.
- **Subcutaneous test**: exercises the system just below the UI layer —
  relevant to a backend-only project like SIGA, whose "subcutaneous" layer
  is effectively the REST API itself, since there's no UI in this codebase.
- **Story test / acceptance test**: verifies a feature from the user's
  perspective, orthogonal to pyramid level — you can write an acceptance
  test as a single broad e2e test or as several lower-level tests that
  together cover the same acceptance criteria.
- **Business-facing test**: written so a non-programmer (a domain expert)
  can read and validate it. Relevant when a rule under test has an
  authoritative business definition — see `business-rules` for SIGA's
  named business rules (baja lógica, no modificar el pasado, etc.).

## Microservice test taxonomy (Fowler, "Testing Strategies in a Microservice
Architecture") — useful even without microservices

The article's taxonomy, from smallest to largest scope: unit, (narrow)
integration, component, contract, end-to-end. The reason it applies to a
modular monolith like SIGA even without service boundaries: Spring Modulith
enforces the same kind of boundary between `auth`, `academic`, `allocation`,
`events`, `space`, etc. that a microservice boundary enforces between
services — `ModularityTests` is effectively checking that boundary the way a
package/dependency scanner would in a polyglot microservice fleet.

**Consumer-driven contract testing (CDC)**, in brief: the consumer of an
interface writes tests that encode its expectations, publishes them (e.g. as
Pact files), and the provider runs those tests in its own CI to catch
breaking changes before they ship. This is the pattern to reach for when two
independently-changing sides need confidence without a broad integration
test — but it assumes two independently-changing sides. In SIGA today, the
provider and consumer of an inter-module Java interface are compiled and
tested together in the same build, so a CDC pipeline (separate pact files,
separate verification stage) doesn't buy anything over a straightforward
narrow integration/unit test against that interface — the compiler already
enforces the contract's shape, and `ModularityTests` enforces the boundary.
The one real inter-party contract in this codebase is the HTTP API consumed
by a separate frontend, where the OpenAPI spec (see `OpenApiConfig`) plays
the contract's role; see `api-testing.md` for what testing that boundary
looks like in practice.
