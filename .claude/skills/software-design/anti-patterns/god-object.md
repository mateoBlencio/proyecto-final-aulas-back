# God Object

## Intent

A single class (or module) that knows about or does too much — it accumulates unrelated responsibilities until it becomes the de facto center of the application, coupled to nearly everything else.

## Problem

Every change touches the god object, so every change risks breaking something unrelated to what you're actually modifying. Code review becomes guesswork because the diff's blast radius isn't visible from the diff itself. Unit testing requires standing up the entire object's dependency graph (DB, HTTP clients, caches, config) just to test one method, so tests get slow, flaky, or skipped. New team members can't hold the class's behavior in their head, so they either avoid touching it (and pile more onto whatever they *do* own) or touch it carelessly. Merge conflicts cluster there because unrelated features all land in the same file.

## How it develops

Nobody designs a god object on purpose. It accretes:

1. A class starts reasonably scoped — say, `OrderService` with `createOrder`, `cancelOrder`.
2. A deadline arrives and the "obvious" place to add `applyDiscount` is the existing service, since it already has the order data loaded. One method, low risk, ship it.
3. Someone needs to send a confirmation email after order creation. `OrderService` already has the customer's email address in scope, so `sendConfirmation` goes in too, rather than publishing an event or adding a new collaborator — that would mean touching DI wiring, writing an interface, more files for what "is just one email call."
4. Six months in, `OrderService` also validates inventory, computes tax, talks to the payment gateway, and formats invoice PDFs — each addition individually reasonable, each one avoiding "the scarier refactor" of introducing a proper collaborator.
5. Once the class is big and scary, the incentive to add a 40th method there instead of extracting a new class gets *stronger*, not weaker — nobody wants to be the one who touches its constructor or risks a regression in a class with no clear ownership boundary.

The forces are always locally rational: avoid new files/interfaces for "just one small thing," reuse data already in scope, avoid the risk of restructuring something that currently works.

## How to recognize it

- Line count: a class file that's grown past ~500-800 lines (language-dependent; a Java service class over 600 lines is a strong signal) with no natural internal grouping.
- Method count: 20+ public methods on one class, especially spanning multiple nouns (`Order`, `Payment`, `Invoice`, `Email` all showing up as concerns in one `OrderService`).
- Constructor/dependency count: 10+ injected collaborators (repositories, clients, other services) — a rough proxy for how many unrelated things the class touches.
- Import/field diversity: fields or imports spanning multiple unrelated domains (`PaymentGateway`, `PdfRenderer`, `EmailClient`, `TaxCalculator` all injected into the same class).
- Git churn: `git log --follow` on the file shows commits from many different feature areas and many different authors, with unrelated tickets/PR titles referencing the same file every sprint.
- Class name is generic: `Manager`, `Service`, `Helper`, `Utils`, `Processor`, `Handler` with no qualifier — these names tend to become dumping grounds because nothing in the name constrains what belongs there.
- "Reasons to change" count (informal SRP check): can you name more than one business reason ("pricing rules changed," "email provider changed," "tax law changed") that would each independently force an edit to this class? Two or more is the signal.

## Remedy

- Identify the distinct responsibilities first (don't guess — list every public method and group by "what actor/reason would want this to change").
- Extract one collaborator at a time, starting with the responsibility that's easiest to isolate and has the fewest internal dependencies on the rest of the class. Each extraction should leave the system green (tests passing) before starting the next.
- Use [Facade](../structural/facade.md) if external callers need a stable single entry point while the internals get decomposed behind it — this lets you extract incrementally without a coordinated big-bang change to every call site.
- Apply [Single Responsibility](../principles/solid.md) as the target state, not a slogan — each extracted class should have exactly one reason to change.
- If the responsibilities vary independently (e.g., discount calculation has several algorithms), consider [Strategy](../behavioral/strategy.md) for that specific piece rather than leaving it as a branch inside the god object.
- Cost: a big-bang rewrite that tries to split the class in one PR is high-risk — it's easy to silently change behavior while moving code, and the review is too large to actually review. Prefer incremental extraction (extract one method group, ship, verify, repeat) over a single sweeping refactor, even though it takes longer and leaves the class "still kind of a god object" for several iterations.

## When the "fix" is worse than the disease

- A script or CLI tool with a single 400-line `main`-adjacent class that one person owns, runs occasionally, and that has no other consumers — splitting it into five collaborator classes adds navigation overhead with no payoff if nobody else will ever read or extend it.
- A class that looks like a god object by line count but is actually a single cohesive algorithm (e.g., a tax engine with many rules that all genuinely belong together and change together for the same reason — "tax law changed"). If every method changes for the *same* reason, it's not a SRP violation even if it's long; splitting it into `TaxRuleA`, `TaxRuleB`, ... classes with no independent variability just adds indirection.
- Early-stage / prototype code where the domain boundaries aren't known yet. Splitting responsibilities prematurely, before you know where the real seams are, produces the wrong abstractions — you'll have to undo the split later anyway. It's often better to let a god object exist for a while during exploration and extract once the actual fault lines are visible from real change patterns (see [Premature Abstraction](premature-abstraction.md)).
- A class that's large because it's a thin orchestration layer with many one-line delegations (e.g., a REST controller with 30 endpoints, each just calling one service method). Line/method count is high but coupling and cognitive load are low — this is bulk, not complexity. Splitting it by resource/route grouping might still help navigation, but it's a different problem than a true god object with tangled internal logic.

## Related patterns

- [Spaghetti Code](spaghetti-code.md) — often co-occurs; a god object's internals are frequently spaghetti because there's no structure forcing separation even within the class.
- [Shotgun Surgery](shotgun-surgery.md) — the opposite failure mode (responsibility split too thin across files instead of concentrated in one); useful to check you're not overcorrecting a god object into shotgun surgery.
- [Single Responsibility](../principles/solid.md) — the principle a god object violates.
- [Facade](../structural/facade.md) — useful during incremental extraction to preserve a stable external interface.
- [Strategy](../behavioral/strategy.md) — useful when one of the extracted responsibilities is itself a family of interchangeable algorithms.

## Real-world example shape

```java
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;
    private final EmailClient emailClient;
    private final PdfRenderer pdfRenderer;
    private final TaxCalculator taxCalculator;
    private final InventoryClient inventoryClient;
    private final DiscountRuleRepository discountRules;
    // ... 6 more collaborators

    public Order createOrder(OrderRequest request) { /* ... */ }
    public void cancelOrder(Long orderId) { /* ... */ }
    public BigDecimal applyDiscount(Order order, String code) { /* ... */ }
    public void sendConfirmationEmail(Order order) { /* ... */ }
    public byte[] renderInvoicePdf(Order order) { /* ... */ }
    public BigDecimal calculateTax(Order order, Address address) { /* ... */ }
    public void reserveInventory(Order order) { /* ... */ }
    public void chargePayment(Order order, PaymentMethod method) { /* ... */ }
    // ... 20 more methods spanning invoicing, tax, email, inventory
}
```

Each method is individually reasonable; the problem is that "order" became the excuse to attach tax, email, PDF, and payment logic to one class instead of giving each its own home.
