# Visitor

## Intent

Add new operations over a stable object structure without modifying the classes of the elements it's made of, by moving the operation into a separate visitor object that each element accepts and dispatches to.

## Problem

You have a fixed hierarchy of element types (an AST's node types, a document's element types, a file system's node types) and you keep needing to add new *operations* over that hierarchy (pretty-print, type-check, serialize, compute size) without wanting to add a new method to every element class each time — especially when the element classes belong to a stable, closed set and adding a method to each of N classes for every new operation doesn't scale, or when the element classes are owned by different maintainers/modules than the operations.

## When to use

- The object structure (the set of element types) is stable/closed and changes rarely, but the set of operations performed over it grows regularly.
- You want operation-specific logic for each element type to be all grouped together in one visitor class (e.g., all the "pretty-print" logic for every node type in one `PrettyPrintVisitor`), rather than smeared as a method on each element class.
- The operation needs type-specific handling — behaving differently depending on the concrete element type — but you can't (or don't want to) put that logic inside the element classes themselves (they may be generated code, third-party types, or intentionally kept free of unrelated concerns).
- Double dispatch is needed: the operation to perform depends on both the visitor's type and the element's concrete type, and a single `instanceof`/type-switch chain would otherwise be needed at every call site.

## When NOT to use

- The object hierarchy changes shape frequently (new element types added regularly). Every new element type requires adding a `visit(NewType)` method to *every existing visitor* — this inverts Visitor's whole value proposition when the churn is on the type side rather than the operation side. If types change often and operations are stable, put the logic back on the elements as regular methods instead.
- There are only one or two operations, and they're simple enough that adding a method directly to each element class is not a maintenance burden. Visitor's machinery (accept/visit double dispatch, one visitor interface method per element type) is pure overhead for a stable two-operation case.
- The language has good pattern matching / sealed types with exhaustiveness checking (modern Java `switch` on sealed interfaces, Kotlin `when` on sealed classes, Rust `match`). A `switch` expression over a sealed hierarchy gives you the same double-dispatch safety (compiler flags missing cases) with far less ceremony than classic GoF Visitor — reach for that first in languages that support it well.
- The visitor needs to accumulate cross-element state that doesn't map cleanly onto a single visit-per-node model (e.g., needs to see two siblings together) — this often signals the traversal itself, not just the per-node operation, needs to be custom, which plain Visitor doesn't handle well.

**Failure scenarios:**
1. An AST library's node hierarchy is still actively being redesigned (new node types added most sprints as language features are prototyped), but the team already committed to a Visitor pattern with five existing visitors (`TypeChecker`, `Printer`, `Optimizer`, `Interpreter`, `Serializer`) — every new node type requires editing all five visitor classes plus the base `Visitor` interface, turning what should be a localized change (one new node type, its own logic) into a five-file cross-cutting change, and it's easy to forget one visitor and ship a silent gap (e.g., the optimizer doesn't know how to handle the new node and just treats it as a no-op).
2. A team building a simple two-operation reporting system (`toCsv()` and `toJson()`) over a small, stable set of 4 domain types adopts full GoF Visitor with `accept()`/`visit()` double dispatch, when a `switch` over a sealed interface (with compiler-enforced exhaustiveness) in a modern language would have given the same "compiler catches missing cases" safety in a fraction of the code and without the indirection of double dispatch.

## Structure

- **Element** (interface): declares `accept(Visitor)`, which calls back `visitor.visit(this)` — this is the double-dispatch hook.
- **ConcreteElement** (one per type in the hierarchy): implements `accept()`, always the same one-line delegation.
- **Visitor** (interface): declares one `visit(ConcreteElementX)` overload per element type.
- **ConcreteVisitor**: implements one operation's logic for every element type, grouped together.

```java
interface Node { void accept(Visitor v); }

class NumberNode implements Node {
    final double value;
    NumberNode(double value) { this.value = value; }
    public void accept(Visitor v) { v.visit(this); }
}

class AddNode implements Node {
    final Node left, right;
    AddNode(Node left, Node right) { this.left = left; this.right = right; }
    public void accept(Visitor v) { v.visit(this); }
}

interface Visitor {
    void visit(NumberNode n);
    void visit(AddNode n);
}

class EvalVisitor implements Visitor {
    double result;
    public void visit(NumberNode n) { result = n.value; }
    public void visit(AddNode n) {
        n.left.accept(this);
        double l = result;
        n.right.accept(this);
        result = l + result;
    }
}
```

## Consequences

**Benefits:**
- New operations added by writing one new Visitor class, with no changes to the element hierarchy at all (Open/Closed with respect to operations).
- Logic for a given operation across all element types is grouped in one place instead of smeared across every element class as separate methods.
- Achieves proper double dispatch (behavior depends on both visitor and element concrete type) in languages without native multiple dispatch.

**Costs:**
- Adding a new element type means editing every existing Visitor implementation — the pattern's cost is fully inverted onto type changes (see failure scenario 1). Only worth it when types are stable and operations aren't.
- Breaks encapsulation to some degree: visitors often need access to element internals that would otherwise stay private, pushing element classes toward exposing more than they would for their own sake.
- More ceremony (accept/visit double dispatch across two interfaces) than a modern sealed-type `switch`, which achieves the same safety more directly in languages that support exhaustiveness checking.
- Traversal logic (how to walk composite structures) has to be written into the visit methods themselves (e.g., `AddNode`'s visit recursing into left/right) unless combined with [Iterator](iterator.md) for the walk.

## Related patterns

- [Iterator](iterator.md) — see the distinction in iterator.md: Iterator lets the *caller* pull elements one at a time and control the loop; Visitor lets the *structure* push each element to a visitor's operation, with the structure (or the visit methods) controlling traversal order.
- [Composite](../structural/composite.md) — Visitor is commonly applied over a Composite tree, visiting both leaf and composite nodes; the accept/visit double dispatch handles the type-specific behavior that would otherwise require `instanceof` checks when walking a Composite.
- [Strategy](strategy.md) — a Visitor is, in a sense, a Strategy per-element-type bundled into one object; but Strategy varies one algorithm as a whole per call, while Visitor varies behavior per concrete element type within a single traversal.

## Smells that suggest this pattern

- A recurring `instanceof`/type-switch chain (`if (node instanceof NumberNode) ... else if (node instanceof AddNode) ...`) duplicated across multiple operations that all need to branch on the same hierarchy's concrete types.
- Every time a new cross-cutting operation is needed (a new export format, a new analysis pass), it requires adding a same-shaped method to every class in an entire hierarchy.
- Operation-specific logic (e.g., serialization logic) awkwardly mixed into element classes that otherwise have nothing to do with that concern, because there was no other place to put type-specific behavior.

## Common misuse

- Adopting Visitor for a hierarchy that's still actively growing/changing shape, incurring a five-files-touched cost for every new type when the hierarchy was the wrong axis to optimize for (see failure scenario 1).
- Using classic GoF double-dispatch Visitor in a language with sealed types and exhaustive `switch`/`match`, when the latter gives the same compile-time safety with far less code (see failure scenario 2).
- Letting a "visitor" accumulate direct mutation of element internals as a side channel, turning it into an untestable God object that both reads and mutates the whole structure in ways the accept/visit contract doesn't make obvious from the call site.
