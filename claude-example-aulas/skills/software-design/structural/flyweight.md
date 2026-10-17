# Flyweight

## Intent

Use sharing to support large numbers of fine-grained objects efficiently, by factoring out shared (intrinsic) state from per-instance (extrinsic) state.

## Problem

An application needs to create a very large number of similar objects, and the memory cost of duplicating their shared data per instance is prohibitive — glyphs in a text editor, tiles in a game map, particle systems, tokens in a large parsed document.

## When to use

- Object counts are large (thousands to millions), not merely "a lot" in casual terms.
- Objects share a large chunk of identical, effectively immutable state, and the part that varies per instance is small enough to pass in at operation time instead of storing.
- Measured or clearly projected memory pressure comes from object *count*, not from any single object's complexity — profiling data, not a hunch.
- The shared data is naturally read-only after construction (fonts, textures, style definitions, terrain tile metadata), making the immutability requirement easy to satisfy rather than fought against.
- A factory/cache layer for the shared instances is acceptable at every construction site — callers going through `new` directly would defeat the sharing.

## When NOT to use

- Object counts are small or moderate (hundreds, low thousands). Modern garbage collectors handle that without help; sharing infrastructure adds complexity for no measurable win.
- Intrinsic and extrinsic state can't be cleanly separated. If the "shared" state actually varies per instance in practice, the pattern collapses into near-1:1 flyweight instances — all the complexity paid, none of the sharing gained.
- **Failure scenario 1:** applying Flyweight to a `Customer` domain object because "there are thousands of them." Customer records are almost entirely unique per instance (name, address, id) — there's nothing meaningfully shared to factor out. Flyweight doesn't apply to entities with identity and mostly-unique state.
- **Failure scenario 2:** introducing a Flyweight factory and cache for a UI with a few hundred icons "for performance," before any profiling shows a memory problem. The result is added indirection (factory, cache, extrinsic state threading through every call site) with no measured benefit, and icon lookup now requires going through a factory instead of a plain constructor.
- Mutable state creeps into what's supposed to be the shared "intrinsic" object. Flyweights must be effectively immutable; if two clients mutate the shared instance's fields, you get cross-contamination bugs that are hard to trace back to the sharing.

## Structure

`Flyweight` exposes an operation that takes extrinsic state as a parameter. `ConcreteFlyweight` stores only intrinsic (shared, immutable) state. `FlyweightFactory` caches and returns shared instances keyed by intrinsic state. The client holds and passes extrinsic state at each call site.

```java
final class CharacterGlyph {                 // ConcreteFlyweight — intrinsic state only
    private final char symbol;
    private final String fontFamily;

    CharacterGlyph(char symbol, String fontFamily) {
        this.symbol = symbol;
        this.fontFamily = fontFamily;
    }

    void render(int x, int y, int sizePt) {  // x, y, size = extrinsic state, passed in
        // draw `symbol` in `fontFamily` at (x, y) with sizePt
    }
}

class GlyphFactory {
    private final Map<String, CharacterGlyph> cache = new HashMap<>();

    CharacterGlyph glyphFor(char symbol, String fontFamily) {
        String key = symbol + "|" + fontFamily;
        return cache.computeIfAbsent(key, k -> new CharacterGlyph(symbol, fontFamily));
    }
}
```

## Consequences

**Benefits:** drastic memory reduction when object count is high and the sharing ratio is high; centralizing creation in a factory makes it easier to enforce immutability.

**Costs:** extrinsic state must be threaded through method calls, adding parameters and complexity at every call site; the factory adds a lookup/caching layer; flyweights must be disciplined about immutability, which is a burden to maintain as the class evolves; trades memory for either CPU (recomputing from extrinsic state) or lookup latency (cache access); the factory's cache itself needs a lifecycle policy (unbounded growth risks trading one memory problem for another) if the key space is large or unbounded.

## Related patterns

- [Singleton](../creational/singleton.md) — a Flyweight factory manages a *pool* of shared instances keyed by intrinsic state (many keys, one instance each), using caching mechanics similar to but broader than a single global instance.
- [Composite](composite.md) — Flyweight leaf nodes are sometimes used inside a Composite tree to keep per-leaf memory cost low when the tree is very large.

## Smells that suggest this pattern

- A profiler shows memory dominated by a very large count of small, structurally identical objects.
- Out-of-memory or excessive GC pressure traced to object *count*, not object complexity.
- Duplicated immutable fields (font, color, texture reference) repeated identically across thousands of instances.
- GC pause times correlate with allocation rate of many short-lived, near-identical objects on a hot path.
- A memory profile shows thousands of objects with identical hash/equals content that could collapse to a handful of shared instances.

## Common misuse

- Reaching for Flyweight preemptively, without profiling data showing an actual memory problem — this is premature optimization with structural cost.
- Applying it to entities with real identity and mostly-unique state (customers, orders), where there's no genuine intrinsic/extrinsic split to exploit.
- Letting "intrinsic" state become mutable as requirements evolve, silently breaking the sharing-safety assumption the whole pattern depends on.
