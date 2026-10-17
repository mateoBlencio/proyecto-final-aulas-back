# API Gateway

## Intent

Provide a single entry point that sits in front of a set of backend services, handling cross-cutting concerns (routing, auth, rate limiting, TLS termination) once instead of in every service.

## Problem

When clients talk directly to N backend services, every client has to know the network location of every service and reimplement the same cross-cutting concerns (authentication, TLS, rate limiting, request logging, retries) against each one. Adding a service means updating every client's routing table. Changing an auth scheme means updating every service. The concerns are cross-cutting but the implementation is duplicated per service per client.

## When to use

- Multiple client types (web, mobile, partner APIs) call into 5+ backend services and each currently re-implements auth/TLS/rate-limiting itself.
- You need to enforce a uniform policy (auth, quota, request validation) across services without touching each service's code.
- Backend services are being decomposed (monolith → microservices) and you want to keep a stable external contract while the internal topology changes underneath it.
- You need centralized observability (single place to log/trace every inbound request) across services owned by different teams.
- Clients should not know internal service topology, ports, or which service owns which endpoint.

## When NOT to use

- A single client talking to a single service. The gateway is pure overhead here — another network hop, another process to deploy/monitor/patch, and another single point of failure, for zero cross-cutting benefit.
- Fewer than a handful of internal services with one client type and no plan to add more. You can put auth/rate-limiting as a shared library or a sidecar/reverse-proxy config (e.g., plain nginx) without standing up a full gateway service with its own routing rules and release cycle.

**Failure scenarios:**
1. A team introduces a gateway in front of exactly one monolith because "that's how microservices are done," then discovers every request now takes an extra hop through a service that has its own deploy pipeline, its own on-call rotation, and its own outages — with no service decomposition to justify it. The monolith could have handled auth and rate-limiting in a filter/interceptor for a fraction of the operational cost.
2. A startup with one mobile client and three internal services stands up a gateway "for the future," then that future never arrives — three years later the gateway is a single hardcoded proxy config nobody remembers how to change safely, and it's the first thing that breaks in every incident because it's the one component every request must pass through.

## Structure

```
                 ┌─────────────┐
Client ───────▶  │ API Gateway │
                 │  - auth     │──▶ Order Service
                 │  - rate     │──▶ Inventory Service
                 │    limit    │──▶ Shipping Service
                 │  - routing  │
                 └─────────────┘
```

```java
@RestController
class GatewayRouteFilter extends OncePerRequestFilter {

    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
        if (!authService.isValid(req.getHeader("Authorization"))) {
            res.setStatus(401);
            return;
        }
        if (!rateLimiter.tryAcquire(clientId(req))) {
            res.setStatus(429);
            return;
        }
        chain.doFilter(req, res); // forwarded to the routed backend service
    }
}
```

In Spring Cloud Gateway, routing itself is declarative config (path → backend URI), with filters like the above applied per route rather than per service.

## Consequences

**Benefits:**
- Cross-cutting concerns implemented and audited once, not N times.
- Backend services can change internal topology without breaking clients.
- Single choke point for observability (logging, tracing, metrics) across all inbound traffic.
- Client simplification: one host/port to know about.

**Costs:**
- Extra network hop on every request — added latency, and now a component whose availability gates every other service's availability.
- Operational surface: the gateway is infrastructure you deploy, scale, patch, and put on-call for, separately from the services behind it.
- Single point of failure unless deployed with its own redundancy — an outage here takes down everything behind it, even if individual services are healthy.
- Temptation to accrete business logic into the gateway (see Common misuse) turns it into an undocumented second monolith.

## Related patterns

- [Backend for Frontend](backend-for-frontend.md) — a BFF is client-specific (one per client type), while an API Gateway is shared across all clients. They can coexist: gateway handles global cross-cutting concerns, BFFs behind it shape responses per client.
- [Facade](../structural/facade.md) — an API Gateway is essentially a Facade at the network/infrastructure layer rather than the code layer; same intent (simplify a complex subsystem behind one interface), different boundary.
- [Circuit Breaker](../distributed/circuit-breaker.md) and [Rate Limiter](../distributed/rate-limiter.md) — commonly implemented as gateway filters/plugins rather than duplicated in each backend service.
- [Anti-Corruption Layer](anti-corruption-layer.md) — different concern: ACL translates between domain models across a boundary, a gateway routes/secures traffic across a boundary. A gateway is not a substitute for an ACL when the actual problem is model mismatch.

## Smells that suggest this pattern

- Every service independently validates the same JWT, with copy-pasted auth-filter code across repositories.
- Clients maintain a hardcoded list of service hostnames/ports that changes whenever infrastructure changes.
- Rate limiting is implemented (or missing) inconsistently, service by service.
- No single place to see "what requests hit our system and did they succeed" without aggregating logs from N services.

## Common misuse

- Letting the gateway accumulate business logic (e.g., order-total calculation, discount rules) because "it's already in the request path." This turns the gateway into an undocumented second monolith that every service secretly depends on, defeating the purpose of decomposing services in the first place.
- Using the gateway as a workaround for services that haven't defined proper contracts — routing/transforming payloads in gateway config instead of fixing the service's API. This hides the real problem and makes the gateway config an untested, unversioned translation layer.
- One gateway config file per environment maintained by hand with no tests, so a routing change silently breaks a path in production because nothing validated it beforehand.
