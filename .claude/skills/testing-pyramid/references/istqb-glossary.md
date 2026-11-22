# ISTQB, and When Its Glossary Is Worth Reaching For

## What ISTQB is

The International Software Testing Qualifications Board runs the leading
global certification scheme for software testing, built on syllabi (written
by a worldwide practitioner community), a standardized glossary, sample
exams, and a Testing Body of Knowledge. Three certification levels:

- **Foundation (CTFL)**: entry-level terminology and breadth, applicable
  across Waterfall, Agile, DevOps, and Continuous Delivery. Prerequisite for
  everything above it.
- **Advanced**: a "Core" stream (Test Management, Test Analyst, Technical
  Test Analyst, Test Automation Engineering, Agile Technical Tester, Agile
  Tester) and a "Specialist" stream (Security, Performance, Mobile,
  Automotive, Game Testing, Finance, etc.).
- **Expert**: strategic/process-improvement level, multiple certification
  paths, seven-year validity.

## Why this matters here, specifically

Fowler's own articles repeatedly point out that testing terminology has no
industry consensus — two engineers can use "integration test" to mean
different things and both be right by their own team's convention (see
`integration-and-contract-tests.md`). ISTQB's glossary exists precisely to
be an external, non-arbitrary tie-breaker when a naming disagreement is
actually blocking a decision (e.g. "is this in scope for test-writer or
does it need planner").

**Practical rule**: don't relabel this repo's existing test names or
conventions to match ISTQB vocabulary — `test-writer`'s own conventions
(unitario, `*IntegrationTest`) are already unambiguous within this project.
Reach for ISTQB terms only when a discussion about test *scope* gets stuck
on what a word means, not as a naming standard to migrate toward.

## Boundary Value Analysis's place in this scheme

BVA (see `boundary-value-analysis.md`) is one of ISTQB's black-box test
design techniques, taught at Foundation level, alongside equivalence
partitioning, decision table testing, and state transition testing. It's
the one most directly applicable to SIGA's validators: any rule with an
ordered domain (a date range, a time window, a capacity, a length) is a BVA
candidate before it's anything else.
