# API-Specific Testing Guidance

Source: U.S. DoD CTO, "API Technical Guidance — Minimum Viable Capability
Release (MVCR) 3" (2026), section 7 (Testing), section 4.3.6, and Appendix A.
Written for defense systems, but the testing-methodology content (sections
7.1–7.6) is generic REST/HTTP API guidance, not DoD-specific policy — that's
the part reused here. Applies directly to SIGA: 21 controllers, a Spring
Boot REST API with an OpenAPI spec (`OpenApiConfig`,
`OpenApiSecurityConfig`), consumed by a separate frontend.

## The three layers this guidance uses for API testing

1. **Unit testing**: same as general unit testing, but the input-space
   strategy matters more for APIs because inputs are attacker-controlled.
   Two complementary strategies for dividing the input space:
   - **Deterministic**: equivalence partitioning + boundary value analysis
     (see `boundary-value-analysis.md`) — pick representative values per
     partition, then hit the boundaries.
   - **Non-deterministic**: fuzz testing — generate/mutate inputs
     automatically. Tools that consume an OpenAPI spec directly (e.g.
     RESTler, Dredd) can generate and execute test cases from SIGA's
     existing spec without hand-writing them.
2. **Integration testing**: beyond "does it call the DB correctly," API
   integration tests should specifically cover distributed-system failure
   modes: concurrency (multiple consumers hitting the same endpoint at
   once), fault tolerance, and failure recovery (does the API return the
   right error code and signal when it's in an error state, even if actual
   recovery is manual). Two named techniques:
   - **Stress testing**: push load parameters past the expected operating
     range to find where the system breaks and how.
   - **Graceful degradation testing**: verify the system fails in a
     "least surprising" way to callers when it can't fulfill a request,
     rather than corrupting state or hanging.
3. **Application testing** (a.k.a. end-to-end for an API): verifies the
   deployed API against a representative environment and test data,
   covering both the happy path and a few deliberately-bad paths. This is
   the layer where SIGA's `*IntegrationTest` suite
   (`@SpringBootTest` + MockMvc + Testcontainers) actually sits — it's
   closer to "application testing" in this taxonomy than to a narrow
   integration test, since it exercises the real HTTP layer against a real
   Postgres instance.

## Test environments: ephemeral vs. persistent

- **Ephemeral**: spun up for the test run, torn down after. Lower cost,
  avoids configuration drift, but pays a setup cost every run. Testcontainers
  is exactly this pattern for SIGA's Postgres-backed integration tests.
- **Persistent**: long-lived, pre-provisioned. Faster to start a test run
  against, but needs ongoing configuration management to avoid drifting
  from a known state, and a stale persistent environment can make a test
  pass or fail for reasons unrelated to the code under test.

## Test data

Keep it realistic (same format/shape as production data) without leaking
real PII/PHI or other sensitive data. When test data needs to be generated
rather than sampled, make sure whoever's using it can actually trust it
represents real-world shapes — synthetic data that's too clean hides bugs
that only show up with messy real input.

## Contract testing via OpenAPI

An API is a contract between provider and consumer: preconditions (what the
caller must supply), postconditions (what's guaranteed after the call),
invariants (what never changes regardless of the operation). OpenAPI
captures preconditions and postconditions well (input schemas, response
codes, response schemas) but doesn't have a way to express invariants (e.g.
"GET requests are idempotent") beyond a text description — so an OpenAPI
spec is a load-bearing contract, but not a complete one. Where an endpoint
has an invariant OpenAPI can't express, say so explicitly in the endpoint's
Javadoc/description rather than relying on the spec to carry it.

## Zero-trust / security testing, and where each type belongs

Relevant OWASP API Top 10 risks called out here: broken authentication,
broken object-level authorization (BOLA — can a caller manipulate an ID in
the request to access another user's/room's/allocation's data), and replay
attacks (a captured, expired credential reused later).

- Broken-auth and broken-authz test cases belong at the **unit level** when
  the check is local to one class, and at the **integration level** when
  auth/authz is delegated to a central service or filter chain (which is
  the recommended pattern, and matches SIGA's `auth` module).
- Replay-attack coverage: verify tokens/keys are short-lived and scoped to
  the current session — this is a job for `business-rules` +
  `diff-reviewer` on the `auth` module specifically, not something to
  improvise per-endpoint.
- **SAST/DAST** run continuously in CI/CD; **IAST** (SAST+DAST combined,
  analyzing runtime behavior) sits closer to the application-testing layer.
  Manual penetration testing runs at a slower cadence (deployment,
  monthly/quarterly) — not something `test-writer` is expected to
  perform, but worth flagging as a gap if a change touches `auth`/RBAC and
  no such cadence exists yet for this project.
- Common API vulnerability classes worth having explicit negative tests
  for, beyond BOLA: injection (SQL/XML/command), insufficient
  authorization checks, insecure error handling that leaks internal state,
  and insecure data transmission (verify TLS is actually enforced where the
  endpoint claims it is, not assumed).

## Versioning and backward compatibility

Semantic versioning (major.minor.patch): major = breaking change,
minor = backward-compatible new functionality, patch = backward-compatible
fix. When a change to an existing endpoint would break an existing
integration, that's a signal the change needs either a new API version, a
backward-compatible alternative (new optional field instead of a changed
required one), or an explicit deprecation path — not a silent breaking
change to a shared endpoint. This is a design-time decision, not something
`test-writer` can decide alone; when a test change reveals that a fix
requires an actual breaking change to a public endpoint, flag it to the
user rather than writing a test that assumes backward compatibility isn't a
concern.
