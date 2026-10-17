# Memento

## Intent

Capture and externalize an object's internal state so it can be restored later, without violating the encapsulation of the object whose state is being captured.

## Problem

You need to support undo, rollback, or checkpointing of an object's state, but the object's internals shouldn't be exposed to the code that manages the history (an undo stack, a save-point manager). Directly exposing all internal fields via public getters just to let external code snapshot and restore them breaks encapsulation and couples the history-management code tightly to the object's internal representation.

## When to use

- You need undo/redo, checkpoint/rollback, or "save game" style state restoration, and the state being saved is more than one or two trivial fields.
- The object's internal representation should stay private/encapsulated even from the code responsible for managing the history of snapshots.
- Snapshots need to be stored in a history (a stack, a list of checkpoints) managed by code that shouldn't need to understand what's inside each snapshot — only how to hand one back to be restored.
- State captured needs to be immutable once taken, so that later changes to the live object can't corrupt an already-saved snapshot.

## When NOT to use

- The object's state is small and simple enough (a couple of primitive fields) that a plain copy/clone, or just recomputing the previous value directly, is simpler than formalizing a Memento class and an originator/caretaker split.
- You need to snapshot large or deeply nested object graphs frequently, and the naive Memento approach (deep-copying the whole object each time) is a real memory/performance problem — this needs a smarter strategy (structural sharing, command-based undo storing only diffs/inverse operations, event sourcing) rather than the textbook Memento.
- Undo is better modeled as replaying inverse operations (Command's `undo()`) rather than restoring a full prior snapshot — appropriate when operations are cheap to invert and full-state snapshots would be wasteful.
- The "state" you'd be capturing crosses object/aggregate boundaries or has external side effects (a file write, a network call) — a Memento can only meaningfully capture in-memory state; restoring it doesn't undo the side effects that happened alongside the original state change.

**Failure scenarios:**
1. An in-memory text editor takes a full deep-copy Memento snapshot of the entire document on every keystroke to support undo — for a large document, this is a severe memory and CPU cost per keystroke; the fix is either snapshotting less frequently (batch by "edit operation" not by keystroke) or switching to command-based undo storing only the diff, not the full document each time.
2. A workflow engine implements "rollback" via Memento snapshots of an aggregate's fields, but the aggregate's state change also triggered an external email send and a payment charge — restoring the Memento resets the in-memory fields but does nothing about the email already sent or the payment already charged, giving a false impression that "rollback" undid the whole operation when it only undid the object's local state.

## Structure

- **Originator**: the object whose state is captured; creates a Memento containing a private snapshot of its own state, and can restore itself from one.
- **Memento**: an opaque, typically immutable value object holding the captured state; exposes no (or a narrow, restricted) interface to outside code.
- **Caretaker**: stores/manages Mementos (e.g., an undo stack) without inspecting or modifying their contents.

```java
class TextEditor {
    private String content = "";

    EditorMemento save() { return new EditorMemento(content); }
    void restore(EditorMemento m) { this.content = m.content(); }
    void type(String text) { this.content += text; }
}

final class EditorMemento {
    private final String content;
    EditorMemento(String content) { this.content = content; }
    String content() { return content; }
}

class UndoStack {
    private final Deque<EditorMemento> history = new ArrayDeque<>();

    void save(TextEditor editor) { history.push(editor.save()); }
    void undo(TextEditor editor) { if (!history.isEmpty()) editor.restore(history.pop()); }
}
```

## Consequences

**Benefits:**
- Undo/checkpoint functionality is added without exposing the Originator's internal fields via public setters/getters.
- The Caretaker (undo stack, history manager) can be generic and reusable across different Originator types, since it never inspects Memento contents.
- Snapshots are immutable, so once taken they can't be corrupted by later state changes to the live object.

**Costs:**
- Naive full-state snapshots are memory-expensive for large objects or high-frequency snapshotting — needs an explicit strategy (batching, diffs, structural sharing) once this becomes a real cost, not just a textbook `deepCopy()`.
- Only captures in-memory state; doesn't undo external side effects performed alongside the state change (see failure scenario 2) — a Memento-based "undo" can be misleadingly incomplete if callers assume it reverses everything that happened.
- In languages without easy access-restricted nested classes, keeping the Memento's contents genuinely opaque to the Caretaker requires discipline (or language features like Java's private inner classes) — otherwise it degrades into just a DTO with public fields, losing the encapsulation benefit.

## Related patterns

- [Command](command.md) — frequently paired: a Command's `undo()` can be implemented by capturing a Memento before `execute()` and restoring it on `undo()`, instead of (or alongside) writing an explicit inverse operation.
- [State](state.md) — different concern: State governs *which behavior* applies based on current lifecycle stage; Memento governs *saving and restoring* a snapshot of state data. They compose well when you need to undo a state transition, not just the data.
- [Prototype](../creational/prototype.md) — both involve copying an object's state, but Prototype's clone is a fully live, independent object meant to be used going forward; a Memento's snapshot is inert data meant only to be handed back to the Originator for restoration, not used directly.

## Smells that suggest this pattern

- Undo/rollback logic implemented by manually copying an object's public fields into a parallel "backup" struct scattered through calling code, rather than the object owning its own snapshot/restore logic.
- Public setters added to a class solely so external history-management code can restore prior values — a sign the snapshot/restore responsibility should move into the object itself via Memento.
- Bug reports where "undo" only partially restores state because some field was added to the object but the ad hoc backup-copying code wasn't updated to include it.

## Common misuse

- Deep-copying large object graphs on every minor change without considering the memory/performance cost, when a coarser-grained snapshot cadence or diff-based approach would suffice (see failure scenario 1).
- Treating Memento-based rollback as a transactional undo of *everything* that happened during an operation, including external side effects it can't actually reverse (see failure scenario 2) — leads to incorrect assumptions about what "undo" guarantees.
- Exposing a Memento's internal state through public getters "for convenience," which lets Caretaker code start depending on its contents — reintroducing the coupling Memento exists to avoid.
