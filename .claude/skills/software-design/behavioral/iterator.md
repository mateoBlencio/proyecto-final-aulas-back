# Iterator

## Intent

Provide a uniform way to traverse the elements of a collection sequentially without exposing how that collection is structured internally.

## Problem

Code that needs to walk through a collection's elements shouldn't need to know whether it's backed by an array, a linked list, a tree, or a lazily-computed sequence. If traversal logic (index bookkeeping, tree walking order, pagination cursors) is duplicated at every call site that needs to loop over a collection, changing the collection's internal representation breaks every one of those call sites.

## When to use

- You need to traverse a custom data structure (a tree, a graph, a composite hierarchy) in one or more well-defined orders without exposing its internal nodes/pointers to callers.
- You want to support multiple simultaneous, independent traversals over the same collection (each iterator keeps its own position).
- The underlying collection may change its internal representation later, and traversal code shouldn't need to change with it.
- You want lazy/on-demand traversal (streaming, pagination over a remote API, infinite sequences) rather than materializing the whole collection up front.

## When NOT to use

- Your language's standard library already provides iteration over the built-in collection you're using (arrays, lists, maps in virtually every mainstream language). Writing a custom `Iterator` wrapper around a `java.util.List` that a `for` loop already handles is pure overhead — use the language's native iteration.
- The traversal is trivial and there's exactly one way anyone will ever want to walk the structure, and it's a flat structure to begin with. A plain `for` loop over an array doesn't need an abstraction layer.
- You're building custom iterator machinery to support a hypothetical future traversal order that isn't needed yet ("we might want reverse order someday") — YAGNI; add it when a second real traversal order shows up.

**Failure scenarios:**
1. A team builds a custom `Iterator<T>` interface, a `Iterable<T>` wrapper, and a `ConcreteIterator` class around what is, underneath, a plain `ArrayList` — duplicating exactly what `java.util.Iterator`/enhanced-for already provides, adding three files and an interface for zero additional capability.
2. A tree traversal Iterator is built supporting only pre-order, but six months later in-order and post-order are both needed and BFS separately — the original Iterator interface didn't anticipate multiple traversal strategies, so instead of adding new iterator implementations cleanly, the traversal order gets smuggled in as a constructor flag with a growing `switch` inside `next()`, defeating the point of having separate, composable iterators per order.

## Structure

- **Iterable** (interface): exposes a method to obtain an Iterator.
- **Iterator** (interface): declares `hasNext()`/`next()` (or equivalent cursor operations), tracks traversal position internally.
- **ConcreteIterator**: implements traversal logic specific to one collection/order.
- **Client**: consumes the collection only through the Iterator interface, agnostic to internal structure.

```java
interface TreeIterator {
    boolean hasNext();
    Node next();
}

class InOrderIterator implements TreeIterator {
    private final Deque<Node> stack = new ArrayDeque<>();

    InOrderIterator(Node root) { pushLeft(root); }

    public boolean hasNext() { return !stack.isEmpty(); }

    public Node next() {
        Node node = stack.pop();
        pushLeft(node.right());
        return node;
    }

    private void pushLeft(Node n) { while (n != null) { stack.push(n); n = n.left(); } }
}
```

## Consequences

**Benefits:**
- Traversal logic is encapsulated once per order, reusable across all callers instead of duplicated inline.
- Collection's internal representation can change without breaking client code that only depends on the Iterator interface.
- Multiple independent iterators over the same collection can coexist, each with its own position — useful for concurrent or nested traversals.
- Enables lazy evaluation: elements can be computed on demand rather than all materialized upfront.

**Costs:**
- An extra layer of indirection over what a native `for` loop already gives you, for free, in most modern languages — real cost only justified for non-trivial custom structures.
- Iterator invalidation on concurrent modification is a real correctness hazard (`ConcurrentModificationException`-style bugs) that needs explicit handling or documentation.
- Stateful iterators (holding a cursor/stack) add a class of bugs around reuse — calling `next()` after exhaustion, sharing one iterator instance across threads unintentionally.

## Related patterns

- [Composite](../structural/composite.md) — Iterator is frequently used to traverse a Composite's tree structure uniformly, without client code needing to distinguish leaf from composite nodes during the walk.
- [Visitor](visitor.md) — both operate over a structure's elements, but Iterator externalizes *traversal order* to the caller (caller pulls elements one at a time); Visitor externalizes *the operation performed on each element* while the structure itself controls traversal (structure pushes elements to the visitor). Use Iterator when the caller wants control over stepping through; use Visitor when you want to add new operations without the caller driving the loop.
- [Factory Method](../creational/factory-method.md) — `Iterable.iterator()` is itself a Factory Method: it returns an object of a type the caller doesn't need to know concretely.

## Smells that suggest this pattern

- The same tree/graph-walking logic (recursive descent, stack-based traversal) duplicated in multiple places in the codebase, each subtly different due to copy-paste drift.
- Client code reaching into a custom collection's internal fields/pointers directly to loop over it, coupling callers to representation details that should be encapsulated.
- A need to pause and resume traversal across multiple method calls (e.g., paginated API results) without holding the entire result set in memory.

## Common misuse

- Reinventing Iterator over a standard library collection that already implements the language's native iteration protocol — check the standard library first.
- Building an Iterator interface anticipating multiple traversal strategies before a second one is actually needed, then smuggling strategy selection in as a flag once it is needed instead of adding a second Iterator implementation (see failure scenario 2).
- Exposing a "reset" or "seek" capability on an Iterator that fundamentally isn't reversible/resettable in its underlying structure (e.g., a stream from a network socket), producing an interface contract the implementation can't honestly fulfill.
