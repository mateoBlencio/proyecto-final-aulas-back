# CQRS (Command Query Responsibility Segregation)

## Intent

Separate the write model (commands, business invariants) from the read model (queries, display-shaped projections), allowing each to be modeled, and optionally persisted, completely independently.

## Problem

A single model serving both transactional writes (needs normalization, invariant enforcement, locking) and complex reads (needs denormalized, join-heavy, differently-indexed data shaped for display) forces compromises on both sides: write-optimized normalization slows down read queries that need to join five tables, and read-convenience indexes and denormalized columns add write-side overhead and invariant-maintenance burden that has nothing to do with business rules.

Concretely: an `Order` aggregate normalized across `orders`, `order_items`, and `customers` tables is correct for enforcing "an order must have at least one item," but a dashboard showing "top customers by order total this month" now needs a multi-table aggregate query that gets slower as both tables grow, competing for the same connection pool and locks as the writes it depends on staying fast.

## When to use

- Read and write load profiles genuinely differ by an order of magnitude or more (e.g. a 1000:1 read:write ratio) and that asymmetry is measured, not assumed.
- The read side needs several distinct denormalized projections (a dashboard view, a search index, an export format) that would otherwise require increasingly complex joins against the write schema.
- The write side has real invariants and business rules that get diluted or complicated by read-shaping concerns bolted onto the same entities (`@JsonIgnore` fights, view-only fields on write models).
- The system already uses event sourcing, or needs a strong audit trail of state transitions — CQRS is a natural fit there since the write side already produces a stream of events the read side can project from.
- Different consumers need materially different consistency guarantees for the same data — an internal analytics dashboard can tolerate minutes of staleness while the transactional API cannot, and serving both from one model forces a single, worse-for-someone consistency choice.
- The write side's domain model is complex enough (many invariants, multi-step aggregates) that read-shaping concerns are a measurable drag on understanding or changing it safely.

## When NOT to use

- The vast majority of CRUD applications — a blog, an admin panel, a typical internal tool — where one model reading and writing the same table is simpler to reason about, debug, and operate. CQRS adds eventual consistency (the read model lags the write model) for zero scaling benefit when there was no asymmetry to begin with.
- Adopting it because it's trendy or resume-driven rather than because of a measured read/write mismatch. Concrete failure: a feature with 50 daily users gets a separate Elasticsearch-backed read projection "for CQRS," and the team now debugs "why doesn't my update show up yet" eventual-consistency bugs, operates a projector process, and reconciles drift — all for a data volume a single Postgres table would handle without noticing.
- When the UI needs read-your-writes consistency immediately after an action (e.g. "show the updated balance right after a transfer") and the team hasn't designed for that. Concrete failure: a user submits a form, sees a success message, refreshes, and the change appears to have vanished because the read projection hasn't caught up yet — a support ticket generator, not a scaling win.
- When a plain read replica would solve the actual problem. A database read replica gives read scaling and eventual consistency without a different *model* or schema — far cheaper than full CQRS with a separately shaped read store. Rule out the read-replica option before reaching for CQRS.

## Structure

```
command/
  CreateOrderCommand         → intent to change state
  CreateOrderHandler         → validates, loads/creates the aggregate, persists
  Order (aggregate)          → enforces invariants, is the source of truth

query/
  OrderSummaryQuery          → a request for display-shaped data
  OrderSummaryHandler        → reads directly from the read store, no business logic
  OrderSummaryReadModel      → denormalized DTO, shaped for the consumer

projections/
  OrderProjector              → listens for domain events (OrderCreated, OrderShipped),
                                 updates the read store asynchronously
```

Sync mechanism: the command side publishes domain events after a successful write; the read side's projector consumes them and updates the read store out of band. A simpler "CQRS-lite" variant updates the read store synchronously in the same transaction, trading some throughput for avoiding eventual consistency — worth considering before committing to full async projection.

## Consequences

**Benefits:**
- Write and read models are each optimized independently — different schema, different indexing, potentially a different datastore entirely for the read side.
- The write model stays focused purely on invariants without read-shaping noise (no view-only fields, no display-formatting logic mixed into the aggregate).
- Reads scale horizontally independent of writes — add read replicas or a dedicated search index without touching the transactional path.
- Multiple read projections can be built from the same event stream (a dashboard view, a search view, an export view) without each one distorting the write model.

**Costs:**
- Eventual consistency between write and read sides is a real, user-facing property, not an implementation detail — it has to be planned for in the UI (e.g. optimistic updates, "processing" states), not just accepted as a backend concern.
- Two models to keep mentally in sync during development — a schema change on the write side often means a corresponding projection change, and it's easy for the two to drift.
- Projection/sync infrastructure has to be built, deployed, and monitored: what happens when the projector falls behind, crashes mid-batch, or processes an event twice?
- Doubled code paths for what used to be a single CRUD operation — a command handler and a query handler where one method used to suffice.
- Debugging now requires tracing across two models and an async boundary instead of following one synchronous call.

## Related patterns

- [Saga](../distributed/saga.md) — often paired with CQRS for coordinating consistency across multiple aggregates when the read side needs to reflect a multi-step process.
- [Message Broker](../integration/message-broker.md) / [Pub/Sub](../integration/pub-sub.md) — typically carries the projection events from write side to read side.
- [Outbox](../integration/outbox.md) — needed to publish projection events reliably from the same transaction as the write, avoiding the "wrote to DB but the event was lost" failure mode.
- [Event-Driven](event-driven.md) — CQRS commonly uses an event-driven mechanism to sync the read model, but the two are independent: CQRS can update the read model synchronously, and event-driven architecture is useful without any CQRS split at all.
- **Read replica** (not separately filed) — the lower-cost alternative to rule out first: same model, replicated storage, no schema divergence.

## Smells that suggest this pattern

- Read queries requiring six or more joins against the transactional schema and still performing poorly under load.
- Read-model DTOs assembled via N+1 queries against write-optimized entities because there's no shaped read path.
- Dashboard or reporting queries timing out against the same database serving transactional writes.
- Write-side entities accumulating view-specific fields and serialization annotations that exist only to satisfy a particular read use case.
- A single aggregate class whose invariant-enforcement methods and display-formatting methods are both hundreds of lines long, because both concerns share the same class.

## Common misuse

- Implementing full CQRS plus event sourcing for an internal admin tool with a handful of users because a blog post or conference talk recommended it, then discovering the projector silently falls behind and nobody notices until a user reports stale data.
- Splitting Command and Query handler classes 1:1 with existing CRUD methods without actually separating the underlying model, schema, or storage — this is CQRS in name only: two classes now exist where one did, with identical logic, and no actual read/write segregation was achieved.
- Introducing CQRS specifically to "future-proof" a system against scale that hasn't materialized and may never materialize, paying the eventual-consistency and operational cost from day one for a hypothetical.
- Building a separate read database without ever measuring whether a simpler read replica (same model, same schema) would have addressed the actual read-scaling need at a fraction of the complexity.
