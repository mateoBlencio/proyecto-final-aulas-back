# Composite

## Intent

Compose objects into tree structures representing part-whole hierarchies, so clients can treat individual objects and compositions of objects through the same interface.

## Problem

Client code needs to perform the same operation on a single object and on an arbitrarily nested group of objects — filesystem files vs. directories, UI widgets vs. containers, org-chart individual contributors vs. teams, menu items vs. submenus. Without Composite, client code ends up branching on "is this a leaf or a container?" everywhere it touches the structure.

## When to use

- The domain is naturally a recursive tree (arbitrary depth, not just one or two fixed levels).
- The same operation (render, total cost, permission check, size) needs to apply uniformly whether you're looking at one item or a whole subtree.
- Client code shouldn't need to know or care whether it holds a leaf or a composite node.
- Depth is genuinely unbounded or config-driven (arbitrary folder nesting, arbitrary UI container nesting), so a fixed number of hand-written levels won't hold up.
- You want to add new leaf or composite types over time without changing any client code that walks the structure.

## When NOT to use

- The collection is flat and non-recursive. A `List<T>` and a loop is simpler and clearer than `Component`/`Leaf`/`Composite` classes.
- Leaf and composite nodes genuinely need very different operations, and forcing them under one interface means unsupported operations throw at runtime.
- **Failure scenario 1:** modeling a fixed two-level, never-deeper `Order` → `List<LineItem>` relationship as a full Composite tree. There's no recursion to support, so the `Component` interface, `Leaf`, and `Composite` classes are pure ceremony over what a plain aggregate already expresses.
- **Failure scenario 2:** forcing `Leaf.addChild()` / `Leaf.getChildren()` to exist because the `Component` interface demands them, implemented as `throw new UnsupportedOperationException()`. This violates Liskov substitution — callers still have to know which concrete type they're holding to avoid the exception, which defeats the entire point of a uniform interface.

## Structure

`Component` declares the shared operation (and optionally child-management methods). `Leaf` implements `Component` with no children. `Composite` implements `Component`, holds a collection of child `Component`s, and implements the operation by delegating to (and aggregating) each child.

```java
interface FileSystemNode {                 // Component
    long size();
}

class FileNode implements FileSystemNode { // Leaf
    private final long bytes;
    FileNode(long bytes) { this.bytes = bytes; }
    public long size() { return bytes; }
}

class DirectoryNode implements FileSystemNode { // Composite
    private final List<FileSystemNode> children = new ArrayList<>();
    void add(FileSystemNode child) { children.add(child); }

    public long size() {
        return children.stream().mapToLong(FileSystemNode::size).sum();
    }
}
```

## Consequences

**Benefits:** uniform treatment of leaves and composites eliminates type-checking branches in client code; new component types are easy to add without touching client code; recursion is expressed naturally instead of manually walked.

**Costs:** the shared `Component` interface can become overly general, making it hard to restrict what kinds of children a composite may legally hold; leaf-specific gaps in the interface lead to no-op or throwing implementations; deep trees can hide real performance costs (recursive traversal, repeated allocation) behind an innocuous-looking method call; enforcing structural constraints (e.g., "only directories can contain files, files can't contain anything") requires extra runtime checks since the type system alone won't express them once everything shares one interface.

## Related patterns

- [Decorator](decorator.md) — structurally similar (both wrap a `Component`), but Decorator wraps a *single* component to layer on behavior, while Composite aggregates *multiple* children to represent structure. Different intent despite similar shape.
- [Visitor](../behavioral/visitor.md) — commonly paired with Composite to define new operations over the tree without modifying the node classes themselves.
- [Iterator](../behavioral/iterator.md) — often used to traverse a Composite's structure without exposing its internal representation.

## Smells that suggest this pattern

- Recursive `instanceof`/type-switch code walking a tree-shaped structure.
- The same traversal logic duplicated in multiple places across the codebase.
- Scattered `isLeaf()` checks in client code before deciding how to proceed.
- Aggregate calculations (total size, total price, total duration) implemented separately for "single item" and "collection of items" code paths.
- Adding a new node type requires hunting down and updating every place that switches on node type.

## Common misuse

- Forcing non-hierarchical, flat data into a Composite tree just to use the pattern.
- Building a Composite where child types differ so wildly in capability that the shared interface ends up polluted with methods most implementations must reject or no-op — a sign the abstraction is wrong, not that Composite needs more methods.
