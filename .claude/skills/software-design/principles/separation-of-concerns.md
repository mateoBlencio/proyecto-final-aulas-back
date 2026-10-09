# Separation of Concerns

## Intent

Organize a system so that each part addresses one distinct concern (a category of decision or knowledge — persistence, business rules, presentation, I/O) and concerns don't leak into each other's code.

## Problem

When concerns are mixed in one place — e.g. HTTP parsing, business validation, and SQL all in one method — a change to any one of them (switch database, add a new UI, change a business rule) risks breaking the others, because there's no boundary preventing them from depending on each other's internals. Testing is also harder: you can't test the business rule without spinning up a database or an HTTP context.

## When to use

- A method mixes request parsing, validation, business logic, and database access in one block, and a unit test for the business rule requires mocking HTTP and DB.
- Changing the UI framework or the database vendor requires touching business-rule code, not just the adapter layer.
- Two unrelated changes (a UI copy tweak, a tax rule change) keep landing in the same file and colliding in code review/merge conflicts.
- A function's parameters or a class's fields mix domain concepts (a `Money` amount) with infrastructure concepts (a `Connection` or `HttpServletRequest`).

## When NOT to use

- Don't split a small script or single-purpose tool (a one-off migration script, a small CLI) into layered `Controller`/`Service`/`Repository` packages — the separation only pays off once concerns genuinely diverge in how often and why they change; a 40-line script has no such divergence yet.
- Don't force separation between concerns that always change together. If validation logic is 100% specific to one persistence format and will never be reused or tested independently, splitting it into its own "layer" adds indirection without decoupling anything real.
- Don't use it to justify a strict N-layer architecture (controller → service → repository → DTO → entity mapper) for every CRUD endpoint when the endpoint has no business logic at all — that's ceremony, not separation, and it multiplies the files needed to trace one straightforward path.
- Don't separate concerns so finely that a single logical operation (e.g. "place an order") is scattered across eight files each doing one micro-step, forcing every reader to reconstruct the flow by jumping through all eight.

## Structure

Mixed concerns:
```java
@PostMapping("/orders")
ResponseEntity<?> createOrder(HttpServletRequest req) {
    String json = readBody(req);
    Order o = parse(json);
    if (o.getTotal() <= 0) return ResponseEntity.badRequest().build(); // business rule
    jdbcTemplate.update("INSERT INTO orders ...", o.getId(), o.getTotal()); // persistence
    return ResponseEntity.ok(toJson(o)); // presentation
}
```

Separated:
```java
@PostMapping("/orders")
ResponseEntity<OrderResponse> createOrder(@RequestBody OrderRequest req) {
    Order order = orderService.place(req.toDomain()); // business logic, testable standalone
    return ResponseEntity.ok(OrderResponse.from(order));
}
```

## Consequences

- **Benefits**: each concern can be tested, changed, and reasoned about independently; swapping an adapter (DB vendor, UI framework, message transport) doesn't touch business logic; parallel work by different engineers on different concerns doesn't collide.
- **Costs**: more files and more boilerplate to route data between layers (DTOs, mappers); a simple change that genuinely spans concerns (e.g. adding one field end-to-end) now touches N files instead of one; over-layering adds indirection that makes tracing a single request harder, not easier.

## Related patterns

- [Layered Architecture](../architectural/layered.md) — the standard structural embodiment of this principle.
- [Hexagonal Architecture](../architectural/hexagonal.md) / Ports and Adapters — separates domain from infrastructure concerns specifically.
- [solid.md](solid.md) (SRP) — SRP is separation of concerns applied at the class level; this principle is the same idea at the module/layer level.
- [MVC](../architectural/mvc.md) — separates presentation, control flow, and domain state.
- [God Object](../anti-patterns/god-object.md) — the failure mode when concerns aren't separated.
- Anemic Domain Model — a failure mode of over-separating data from behavior: fields in one class, all behavior in a disconnected "service" class.

## Smells that suggest this pattern

- A controller method containing SQL strings.
- A domain/business-rule class importing an HTTP or servlet type.
- Unit tests for business logic that require a running database or mocked HTTP layer.
- A single file that changes for unrelated reasons (a database schema tweak and a UI label change both touch it).

## Common misuse

- Introducing a `Service` layer that does nothing but forward calls 1:1 to a `Repository`, with no business logic of its own — this is a layer with no concern of its own, pure ceremony.
- Splitting a domain model into a `Data` class (fields only) and a `Logic` class (behavior only) purely to satisfy a "layers" checklist, producing an anemic domain model where behavior lives disconnected from the state it operates on.
- Applying strict separation inside a single bounded, cohesive algorithm (e.g. a parser's tokenizer and grammar rules that are tightly coupled by design) where forcing a boundary makes the two halves need to know as much about each other as before, just through a narrower, more awkward interface.
