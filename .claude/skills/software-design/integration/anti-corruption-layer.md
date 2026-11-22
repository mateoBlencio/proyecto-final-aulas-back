# Anti-Corruption Layer (ACL)

## Intent

Isolate your domain model from a foreign model (a legacy system, a third-party API, another team's bounded context) by translating between them at a single, explicit boundary, so the foreign model's concepts and quirks never leak into your own code.

## Problem

When you integrate with a foreign system directly — calling its API, mapping its response DTOs straight onto your domain objects, reusing its enums — its modeling decisions become yours by osmosis. Its quirks (a `status` field that means something subtly different than yours, a required field your domain doesn't have, an ID scheme tied to its internal implementation) spread through your codebase wherever that data flows. Over time your domain model degrades into an ad-hoc mirror of the foreign system's model, littered with fields and branches that only make sense in terms of *its* rules, not yours.

## When to use

- Integrating with a legacy system whose model is known to be messy, inconsistent, or encodes historical decisions that don't map cleanly onto your domain (e.g., a mainframe billing system where `accountType = "9"` means something no one fully remembers).
- Integrating with a third-party API (payment processor, shipping carrier, CRM) whose data model you don't control and which can change its shape/semantics on its own release schedule, independent of yours.
- Two bounded contexts (in the DDD sense) within your own organization have genuinely different models for what's nominally "the same" concept (e.g., "Customer" means something different to Billing than to Support), and you want to prevent one team's model churn from forcing changes in the other's.
- You've already observed foreign-system concepts (its enums, its null-means-something-weird conventions, its ID format) appearing directly in your domain layer, and it's causing bugs or confusion.

## When NOT to use

- Built preemptively against a system that isn't actually messy yet, "just in case" it diverges later. If the foreign model already cleanly matches your domain's needs, a translation layer adds a maintenance cost (a mapper to write and update) for a divergence that may never materialize — YAGNI applies here as much as anywhere.
- The "foreign" system is actually another module you fully control within the same codebase, and the concepts genuinely are the same thing. An ACL between two internal modules that agree on the model is just needless indirection — use a shared kernel or a direct dependency instead.
- The integration is a single, narrow, stable call (e.g., one geocoding lookup: address in, lat/long out) where a foreign model can't realistically "corrupt" much because there's barely any model to corrupt. A translation function at the call site is enough; a formal ACL with its own types and mapping layer is ceremony.

**Failure scenarios:**
1. A team builds a full ACL — dedicated translation classes, its own DTOs, a facade interface — for a single third-party address-validation API call that returns `{valid: bool, normalized: string}`. The mapping logic is one line; the ACL scaffolding around it (interface, adapter, translator, its own test suite) is 200 lines that all have to be maintained for a shape that will never meaningfully diverge from the domain's own `Address` concept.
2. A team builds an ACL "preemptively" in front of an internal service they don't yet know will change, spending a sprint on translation types for a model that turns out to match their domain closely. Eighteen months later the internal service's model still hasn't diverged, and the ACL is pure indirection every new engineer has to learn to trace through, for a corruption risk that never materialized.

## Structure

```
Your Domain  ◀──  Translator/Facade  ◀──  Foreign System's API/Model
(YourOrder)        (ACL boundary)          (VendorPurchaseOrderDTO)
```

```java
// Foreign shape — don't let this leak past the ACL
class VendorPurchaseOrderDto {
    String po_status;      // vendor's own status codes: "A", "S", "X"...
    String vendor_cust_id; // vendor's internal customer identifier
}

// Your domain's own model — clean, expressed in your ubiquitous language
record Order(OrderId id, OrderStatus status, CustomerId customerId) {}

// The ACL: the only place that knows both models exist
@Component
class VendorOrderAntiCorruptionLayer {

    Order toDomain(VendorPurchaseOrderDto dto) {
        return new Order(
            OrderId.of(dto.vendor_cust_id()),      // translate identity scheme
            translateStatus(dto.po_status()),       // translate vendor codes to your enum
            resolveCustomerId(dto.vendor_cust_id())
        );
    }

    private OrderStatus translateStatus(String vendorCode) {
        return switch (vendorCode) {
            case "A" -> OrderStatus.ACTIVE;
            case "S" -> OrderStatus.SHIPPED;
            case "X" -> OrderStatus.CANCELLED;
            default -> throw new UnknownVendorStatusException(vendorCode);
        };
    }
}
```

Every other class in the domain only ever sees `Order`/`OrderStatus` — never `VendorPurchaseOrderDto` or `po_status`.

## Consequences

**Benefits:**
- Domain model stays expressed in your own ubiquitous language, not contaminated by a foreign system's terminology or quirks.
- The foreign system can change its API/model and only the ACL's translator needs updating — the rest of the domain is insulated.
- Makes foreign-system quirks (weird nulls, inconsistent enums, legacy ID formats) visible and explicit in one place instead of scattered as ad-hoc workarounds throughout the codebase.
- Easier to eventually replace the foreign system entirely — only the ACL implementation changes, since nothing else depends on its model directly.

**Costs:**
- A real translation layer to write, test, and maintain — every field mapping is code that can have bugs, and every foreign-model change requires an ACL update.
- Extra indirection: engineers have to learn there are two models and where the boundary is, which has a learning-curve cost, especially for a team unfamiliar with the pattern.
- Risk of building it too early against a system that turns out not to be messy (see failure scenarios) — the translation layer then exists for no real reason, adding a hop to trace for zero corruption prevented.
- Can hide performance costs (extra object allocation/mapping on every call) if the domain model and foreign model are large and deeply nested.

## Related patterns

- [Adapter](../structural/adapter.md) — an ACL is often *implemented with* an Adapter (or several), but ACL is the broader DDD/architectural concept of protecting a bounded context's model; Adapter is the narrower structural mechanism (making one interface conform to another) that ACL uses to do it.
- [Facade](../structural/facade.md) — ACL's public-facing side often looks like a Facade (a simplified interface over the foreign system), but the defining feature of ACL is the explicit *model translation*, not just interface simplification.
- [API Gateway](api-gateway.md) / [Backend for Frontend](backend-for-frontend.md) — different direction: those shape *your* system's interface toward external callers; ACL shapes an *external* system's model to protect your internal domain. Don't conflate outbound protection (ACL) with inbound interface design (Gateway/BFF).

## Smells that suggest this pattern

- Domain model littered with fields, enums, or null-handling that only make sense in terms of a third-party API's model (e.g., a `vendorStatusCode` field sitting next to your own `OrderStatus` enum, kept "just in case").
- Business logic with conditionals branching on a foreign system's raw values (`if (dto.getPoStatus().equals("A"))`) scattered across multiple classes instead of centralized in one translation point.
- A schema change in a third-party API breaking code in several unrelated parts of your codebase, because that API's DTO was passed around directly instead of being translated at the boundary.
- Domain objects whose identity is literally the foreign system's ID format, making it impossible to reason about your own domain without knowing the foreign system's conventions.

## Common misuse

- Building an ACL preemptively against a system that isn't actually messy yet (see failure scenarios) — wait for a real, observed divergence or a known-messy legacy system before investing in the translation layer.
- Building the ACL but leaking the foreign DTO through it anyway (e.g., an ACL method that returns the vendor DTO directly for "convenience" in one call site) — this defeats the entire purpose; the ACL boundary has to be enforced with no exceptions, or the corruption happens through the hole.
- Making the ACL a dumping ground for unrelated integration logic (retries, caching, auth) that belongs to [Retry](../distributed/retry.md)/infrastructure concerns rather than model translation — keep the ACL focused on translation, not general-purpose "everything related to this vendor" logic.
