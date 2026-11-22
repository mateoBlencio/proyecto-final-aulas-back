# Backend for Frontend (BFF)

## Intent

Give each distinct client type (web, mobile, third-party partner) its own backend layer that shapes responses and aggregates calls specifically for that client's needs, instead of forcing all clients through one general-purpose API.

## Problem

A single general-purpose API trying to serve a rich web dashboard, a bandwidth-constrained mobile app, and a partner integration ends up either over-fetching for the client that needs less, under-fetching for the client that needs more, or growing conditional response-shaping logic (`if (clientType == MOBILE) ...`) inside the shared backend. The clients' actual data and interaction needs diverge enough that one contract can't serve all of them well.

## When to use

- Mobile needs a small, denormalized payload (battery/bandwidth constrained) while the web dashboard needs a richer, more normalized one from the same underlying services — and this difference is causing the shared API to grow client-conditional branches.
- A client needs to aggregate data from 3+ backend services into one screen, and doing that aggregation client-side means multiple round trips over a slow/mobile network.
- Different client teams (mobile team, web team) want to iterate on their API contract independently without coordinating a shared API's release schedule with each other.
- A partner-facing API needs a different auth model, versioning policy, or data shape than the internal web client, and bolting both onto one API creates conflicting constraints.

## When NOT to use

- The clients' needs aren't actually that different — same data shape, same aggregation, minor cosmetic differences. Building a BFF per client here creates N near-identical backends that all need to be updated in lockstep every time a shared field changes, which is strictly worse than one API with a shared DTO.
- Only one client exists (or ever will). A BFF for a single client is just... the backend. Naming it "BFF" doesn't add value, and if a second client shows up later, the BFF can be introduced then, against real requirements instead of guessed ones.
- The team building the BFFs is small and would end up owning 3+ near-duplicate services with the same bug needing three near-identical fixes. If the underlying divergence is small, a single API with optional/versioned fields or GraphQL field selection may solve the same problem with one codebase.

**Failure scenarios:**
1. A company builds a mobile-BFF, a web-BFF, and a partner-BFF, each independently calling the same five downstream services and independently reimplementing the same retry/timeout logic. A bug in how one BFF handles a downstream timeout gets fixed there, but the identical bug in the other two BFFs ships to production unnoticed for months because nobody thought to check the "other" backend that does "the same thing."
2. A team builds a BFF per client "for scalability" when the web and mobile clients actually render the same screens with the same data — the BFFs end up as two 95%-identical GraphQL resolvers, and every schema change requires two PRs, two deploys, and two rounds of QA, doubling the maintenance cost for zero product benefit.

## Structure

```
Mobile App ───▶ Mobile-BFF  ─┐
                              ├──▶ Orders Service
Web App    ───▶ Web-BFF     ─┤──▶ Inventory Service
                              └──▶ Pricing Service
Partner    ───▶ Partner-BFF ──▶ Orders Service (subset, different auth)
```

```java
@RestController
@RequestMapping("/mobile/orders")
class MobileOrderBffController {

    // Mobile needs one lightweight call; downstream requires two services.
    // Aggregation lives here, not duplicated in the mobile client.
    @GetMapping("/{id}")
    MobileOrderSummary get(@PathVariable String id) {
        Order order = ordersClient.get(id);
        ShippingEta eta = shippingClient.estimateFor(order.id());
        return new MobileOrderSummary(order.id(), order.status(), eta.days()); // denormalized, mobile-only shape
    }
}
```

The web-BFF would call the same two services but return the full `Order` plus line items and pricing breakdown the mobile summary screen never shows.

## Consequences

**Benefits:**
- Each client gets a payload shaped exactly for its screen/use case — no over-fetching, no client-side conditional parsing.
- Aggregation across services happens server-side, near the data, instead of as multiple slow round trips from the client.
- Client teams can iterate on their contract independently of other clients' release cycles.

**Costs:**
- N backends to deploy, monitor, and secure instead of one — each is a real service with its own on-call burden.
- Duplicated logic risk: the same retry/auth/error-handling code tends to get copy-pasted across BFFs and drift out of sync (see failure scenario 1).
- Shared downstream services now have multiple callers with different contracts, which complicates changing those services' APIs (breaking one BFF's assumption while fixing another's).
- Team boundaries matter here — a BFF works best when the team that owns the client also owns its BFF; if one team owns all BFFs, most of the "independent iteration" benefit disappears.

## Related patterns

- [API Gateway](api-gateway.md) — often deployed in front of the BFFs to handle shared concerns (auth, TLS, global rate limiting) that don't vary by client; the BFF then does client-specific shaping behind it. Gateway = one shared entry point; BFF = one backend per client type. Don't conflate them.
- [Facade](../structural/facade.md) — a BFF is a Facade at the service layer, scoped to one client type, with its own deployable lifecycle rather than just a code-level wrapper.
- [Anti-Corruption Layer](anti-corruption-layer.md) — different problem: ACL protects your domain model from a foreign one; BFF shapes your existing domain model for a specific client's presentation needs.

## Smells that suggest this pattern

- A single API controller riddled with `if (clientType == ...)` branches shaping the response differently per caller.
- Mobile client code doing three sequential API calls and manually stitching the results together to render one screen.
- A shared DTO with a dozen optional fields, most of which are null for any given client, because it's trying to serve everyone at once.

## Common misuse

- Spinning up a BFF per client "by convention" without checking whether the clients' actual needs diverge — see failure scenario 2.
- Letting a BFF become a place where business rules live permanently (e.g., pricing logic implemented only in the mobile-BFF) instead of staying a thin aggregation/shaping layer over services that own the actual logic — this creates business-logic drift between BFFs.
- Sharing one BFF across clients with genuinely different needs "to avoid building two" — this just recreates the original one-size-fits-all API problem under a different name.
