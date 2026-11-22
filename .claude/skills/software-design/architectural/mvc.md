# MVC (Model-View-Controller)

## Intent

Separate presentation into three roles — Model (data and business state), View (rendering), and Controller (translates input into model changes and view selection) — so UI rendering changes don't require touching business logic, and vice versa.

## Problem

UI rendering code tangled with business logic and state management makes both hard to change independently, and makes it hard to present the same underlying data through more than one view without duplicating logic. MVC gives each of those three concerns a distinct home and a defined direction of communication between them.

Concretely: a page that computes a shipping discount inline inside its template, and a second page (or a JSON API) that needs the same discount, either duplicates the calculation or resorts to rendering one page's output through the other — neither is a real fix. MVC's answer is that the discount calculation belongs to the Model, and both the page and the API call the same Model code.

## When to use

- Server-rendered web applications where the framework already scaffolds this split (Spring MVC, Rails, Django, ASP.NET MVC).
- Desktop GUI applications with multiple views over shared, mutable application state.
- Any application that needs more than one view of the same underlying data (e.g. an HTML page and a JSON API) and wants to add the second view without duplicating the first view's logic.
- Systems where user input handling (validation, routing to the right operation) is nontrivial enough to warrant a dedicated Controller layer rather than being embedded in the view template.
- Desktop or embedded GUI toolkits (Swing, WPF, Qt) that are already structured around this triad, where fighting the toolkit's own conventions costs more than adopting them.
- Content-heavy sites where the same underlying data legitimately needs several renderings (web page, RSS feed, print view) driven from one model.

## When NOT to use

- An API-only backend with no UI to render — there is no "view" to separate from anything, so applying MVC vocabulary here is a category error; reach for [Layered](layered.md) or [Hexagonal](hexagonal.md) instead, which describe the actual concerns (request handling, business logic, persistence).
- Component-based SPA frontends (React, Vue) where the component model already unifies view and local state per component — forcing classic MVC's separate Controller classes and passive Views fights the framework's own idioms and produces code nobody familiar with the framework recognizes. Concrete failure: a React codebase with hand-rolled `Controller` classes dispatching to `View` components that hold no state of their own, reimplementing what component-local state and hooks already do, with far more indirection.
- A simple static content site — there is no meaningful "model" that changes at runtime, so the split is unnecessary overhead over serving templates directly.
- When "MVC" is applied as three folders with no real behavioral separation — see Common misuse below.

## Structure

```
Model:      data and business rules, no knowledge of any specific UI
Controller: receives user input, updates the Model, selects/prepares a View
View:       renders Model state, contains no business logic of its own
```

Classic MVC: the View observes the Model directly (via [Observer](../behavioral/observer.md)) and re-renders when it changes; the Controller's job is limited to translating input into Model updates, not pushing rendering decisions.

In practice, most modern web frameworks implement a variant sometimes called MVC but structurally closer to **MVP** (Model-View-Presenter: the Presenter explicitly pushes updates to a fully passive View, no Observer wiring) or **MVVM** (Model-View-ViewModel: the ViewModel exposes bindable state, the View binds to it declaratively — common in SwiftUI, WPF, and JS frameworks with reactive bindings). Know which variant a given framework actually implements before assuming classic MVC's Observer-based coupling applies.

## Consequences

**Benefits:**
- A new view (a JSON API alongside an existing HTML page) can be added without touching the Model, as long as the Model doesn't already have view-specific logic baked in.
- UI-focused engineers can iterate on Views without deep knowledge of business rules, and vice versa.
- Input handling has one obvious home (the Controller), rather than being scattered across templates and route handlers.
- The pattern is near-universally recognized, so a new team member already knows where to look for each concern.

**Costs:**
- Controllers become a dumping ground for logic that doesn't obviously belong in the Model or the View — the "fat controller, skinny model" anti-pattern, where validation, calculation, and orchestration all accumulate in the Controller because it's the path of least resistance.
- Many real-world implementations (Rails, Spring MVC) couple View and Controller more tightly than the theoretical diagram suggests, undermining the separation MVC is supposed to buy.
- MVC says nothing about how deep or well-modeled the domain logic inside the Model should be — it's silent on the exact problem Hexagonal/Clean/Onion address, so pairing MVC's presentation split with a deliberately structured Model is common practice, not redundant.
- Multiple MVC variants (classic Observer-based, MVP, MVVM) get referred to interchangeably as "MVC" in casual conversation, which causes real confusion about who pushes updates to whom in a given codebase.

## Related patterns

- [Layered](layered.md) — MVC concerns the presentation layer specifically and typically sits on top of a layered (or hexagonal) backend; it is not a substitute for backend layering.
- [Observer](../behavioral/observer.md) — classic MVC's View-updates-from-Model mechanism.
- [Command](../behavioral/command.md) — Controller actions are sometimes implemented as Command objects, particularly in desktop GUI frameworks.
- MVP and MVVM (siblings, not separately filed here) — differ from classic MVC in who pushes updates to whom; know which one a framework actually implements.

## Smells that suggest this pattern

- Business logic embedded directly in templates (SQL queries or calculations in a Thymeleaf/JSP/ERB/Blade template) — reason to at minimum enforce View = rendering only.
- The same business rule duplicated across an HTML-rendering code path and a JSON API code path because there is no shared Model layer they both go through.
- Input validation and parsing logic scattered across templates and route handlers instead of centralized in a Controller.
- A second view of the same data (a CSV export, a JSON endpoint) requiring a copy-paste of an existing controller's business logic rather than a call into a shared Model.
- View templates directly querying the database or an ORM session to "just grab one more field," bypassing the Controller/Model entirely.

## Common misuse

- Calling any three-folder split "MVC" when there is no real separation — a "Model" that is just JPA entities with zero behavior, a "Controller" that contains all the validation, calculation, and persistence orchestration, and a "View" that's a plain template. The folder names exist; the separation of responsibility that MVC is supposed to buy does not.
- Forcing classic Observer-based MVC into a React or Vue SPA — hand-written Controller classes and passive View components reimplementing what component-local state and reactive bindings already provide, at a large indirection cost with no corresponding benefit.
- Treating the Controller as the place business logic belongs by default, letting it grow into a God Object that happens to also handle HTTP concerns — MVC describes where input handling and view selection go, not where business rules should live; that decision still needs deliberate domain modeling.
- Introducing a ViewModel/Presenter layer on top of an already-thin CRUD screen "for consistency with the rest of the codebase," when the screen has no meaningful presentation logic to separate from its data.
