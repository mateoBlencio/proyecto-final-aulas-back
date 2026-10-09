# The Test Pyramid, and the Size Axis Underneath It

## The pyramid, in one paragraph

Fowler's core claim (`TestPyramid` bliki): write tests at different levels of
granularity, and write a lot more of the cheap, fast, low-level ones than the
expensive, slow, high-level ones. The shape is a pyramid, not a rectangle,
because cost and fragility both increase as you move up.

## The ice-cream cone anti-pattern

The inverted shape — most tests running end-to-end through the UI or a full
deployed stack, few or no unit tests underneath — is the "ice-cream cone."
Fowler's diagnosis of why it fails:

- **Brittle**: a UI or endpoint-shape change breaks many tests at once.
- **Expensive to write and maintain.**
- **Slow**: minutes to hours instead of milliseconds to seconds, which
  delays feedback and encourages batching changes instead of testing
  continuously.
- **Hard to run headless** in a deployment pipeline.

The one stated exception: if your high-level tests are fast, reliable, and
cheap to change, you don't strictly need more lower-level tests underneath
them. That's rare in practice — most teams that believe this haven't measured
their e2e suite's flake rate.

## The three pyramid layers (Fowler's "Practical Test Pyramid")

1. **Unit tests** — smallest scope, largest quantity, fastest. Test the
   public interface of one class/function; don't test private methods (if
   you feel the need to, that's a design smell, not a testing gap). One
   condition per test. Arrange-Act-Assert.
2. **Integration tests** — moderate quantity. Test one integration point at
   a time (DB, filesystem, another service, a queue). See
   `integration-and-contract-tests.md` for the narrow-vs-broad distinction,
   which matters more than the pyramid diagram lets on.
3. **UI / end-to-end tests** — smallest quantity, slowest. Reserve for a
   handful of essential user journeys, not for re-checking edge cases
   already covered lower down.

### Test doubles, by name

| Type | Does | Typical use |
|---|---|---|
| Stub | Returns canned answers | Replace a slow/external dependency |
| Mock | Records and verifies interactions occurred | Assert a collaborator was called correctly |
| Fake | Working, simplified implementation | In-memory DB, in-process test server |

### The rule that actually prevents duplication

If a higher-level test fails but no lower-level test does, that's a gap: add
a lower-level test that would have caught it, and consider whether the
higher-level test is now redundant. Push tests as far down the pyramid as
they can go without losing what they're actually checking (e.g. HTTP-level
concerns genuinely can't be tested by a unit test).

## The size axis (Google, "Test Sizes", 2010) — orthogonal to the level axis

Fowler's article explicitly credits Google's approach here: classify tests
by **size** (how much of the real world they touch) separately from **type**
(what they check). A test can be a "unit test" by intent and still be a
large test if it spins up threads, hits the filesystem, or talks to a real
service — size is about the infrastructure a test is allowed to touch, not
about what the test author called it.

- **Small**: single process, no network, no real filesystem, no sleeping,
  no real external services — everything else is mocked/stubbed/faked.
  Deterministic, runs in milliseconds to low seconds. This is what most
  "unit tests" should actually be.
- **Medium**: can talk to `localhost`, can touch the real filesystem, can
  use multiple threads/processes on one machine. Runtime budget: seconds to
  roughly a minute.
- **Large**: can talk to real network hosts beyond localhost, spans
  multiple machines, hits real external services. Slowest, least
  deterministic, reserved for what genuinely needs that scope.

This size classification later became explicit tooling (e.g. Bazel's
`size` test attribute: small/medium/large/enormous, each with an escalating
timeout). The point isn't to adopt that tooling — it's to ask, for any test
you're about to write, "what does this actually need to touch to be true?"
before reaching for `@SpringBootTest` out of habit.

## The 70/20/10 ratio, and why it's a heuristic, not a law

Google Testing Blog's "Just Say No to More End-to-End Tests" (2015) argues
for roughly 70% unit / 20% integration / 10% end-to-end, and documents the
concrete failure modes of skewing that ratio toward e2e:

- **Flakiness** from environment and infrastructure noise, not from real
  bugs — the classic false failure that trains people to ignore red builds.
- **Slow feedback** — a suite that takes hours defeats the purpose of
  having a suite.
- **Ownership ambiguity** — an e2e test spanning several services/modules
  often has no clear owner when it breaks.

The fix isn't "delete all e2e tests" — it's investing in unit/integration
first, and keeping e2e tests scoped to the few workflows where nothing lower
down can give the same confidence. Treat the 70/20/10 split as a shape to
aim for, not a number to hit exactly; the right ratio depends on how much of
your risk actually lives at integration boundaries.

## "How much testing is enough?" — coverage is a weak signal

Google Testing Blog (2021) and general practice converge on the same
caveat: code coverage tells you what has *no* tests, not whether the tests
that exist are any good. A line can be "covered" by a test with a tautological
assertion and contribute nothing to defect detection (see this repo's
CLAUDE.md rule on "verificación real" — the same idea, stated as a project
rule). Treat coverage percentage as a floor-finder, not a quality metric;
decide sufficiency by risk (what breaks in production, what's expensive to
get wrong) rather than by chasing a coverage number.
