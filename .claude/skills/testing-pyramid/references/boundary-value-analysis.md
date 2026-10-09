# Boundary Value Analysis

Source: Hamburg & Roman, "Boundary Value Analysis According to the ISTQB
Foundation Level Syllabus" (ISTQB white paper, 2025), which formalizes the
technique described in the ISTQB Foundation Level Syllabus v4.0.

## Why this technique over picking values by hand

Developers are more likely to get boundaries wrong than interiors: an
off-by-one, the wrong comparison operator (`<` vs `<=`), a boundary that's
misplaced or missing entirely. BVA targets exactly those defects instead of
spreading test effort evenly (and mostly uselessly) across an entire input
range.

## Boundary values are not "the border"

A border sits *between* two partitions. A boundary value is an element
*inside* a specific partition, at its edge. For an ordered domain split into
partitions, only the minimum and maximum of each partition are boundary
values — BVA only applies to **ordered** partitions, where "everything
between two elements of the same partition belongs to that partition" holds.

## 2-value vs. 3-value BVA

- **2-value BVA**: for each boundary, test the boundary itself and its
  nearest neighbor in the adjacent partition. Two coverage items per
  boundary (fewer at an outer/unbounded edge).
- **3-value BVA**: for each boundary, test the boundary and *both*
  neighbors (one on each side). Catches defects 2-value BVA misses — e.g.
  `x <= 10` implemented as `x == 10` passes 2-value BVA's `x=10, x=11` check
  but fails on the interior point `x=9` that only 3-value BVA includes.

3-value BVA costs more test cases. Decide per rule, based on how much it
would cost in production if that specific comparison operator were wrong.

## Step by step

1. Identify the value domain (what type of value, what shape).
2. Determine the equivalence partitions.
3. Identify each partition's boundary values (min/max; an unbounded
   partition has only one boundary).
4. For each boundary, list its 2-value or 3-value coverage items.
5. Union all coverage items (they can overlap between adjacent partitions).
6. Write one test case per coverage item.

## Worked example against this repo: `EventScheduleValidator.validateBusinessHours`

`src/main/java/ar/edu/utn/frc/siga/events/validator/EventScheduleValidator.java`:

```java
public void validateBusinessHours(LocalTime start, LocalTime end) {
    if (!end.isAfter(start)) { throw ...; }
    if (start.isBefore(scheduleSettings.getStart()) || end.isAfter(scheduleSettings.getEnd())) {
        throw ...;
    }
}
```

With `scheduleSettings.getStart() = 08:00` and `getEnd() = 23:00` (the
values the existing test fixture uses):

- Domain: `start`, `end` — both `LocalTime`, ordered.
- Partitions on `start`: too-early (`< 08:00`), valid (`>= 08:00`).
  Boundary: `08:00`.
- Partitions on `end`: valid (`<= 23:00`), too-late (`> 23:00`). Boundary:
  `23:00`.
- Partitions on the pair `(start, end)`: `end <= start` (invalid, strict
  `isAfter` required) vs. `end > start` (valid). Boundary: `end == start`.

2-value coverage items: `start = 07:59` (invalid) / `08:00` (valid, boundary
itself); `end = 23:00` (valid, boundary itself) / `23:01` (invalid);
`end == start` (invalid) / `end == start + 1 minute` (valid).

`EventScheduleValidatorTest` (`src/test/java/.../EventScheduleValidatorTest.java`)
already covers the *partitions* well (10:00-11:00 valid; 6:00-9:00 and
22:30-23:30 invalid; end == start invalid) but doesn't hit the boundary
values themselves — `08:00` and `23:00` as valid inputs, and their tightest
invalid neighbors `07:59`/`23:01`. That gap is exactly what BVA is for: the
existing tests would still pass if `isBefore` were accidentally
`!isAfter` or the window bound were off by one hour, because none of them
sit close enough to the boundary to notice. This is a concrete illustration,
not a request to edit that test — whether to add the missing boundary cases
is a call for whoever's touching that file next.

## The trap this technique catches: seamless transitions

If two adjacent partitions produce the *same output* right at their shared
boundary, naive boundary testing won't distinguish "implemented correctly"
from "implemented as one giant off-by-one." ISTQB's example: a fee that's
1% of a trade, minimum 1€ — right around the 100€ mark, both the "flat 1€"
rule and the "1%" rule produce the same 1.00€ result, so testing at exactly
100€/100.01€ can't tell you which rule the code actually implements. Fix:
introduce a narrow third partition around the transition and pick boundary
values *inside* it (in that example, 99.49€ and 100.50€ — the points where
the two formulas would actually disagree) to make the two implementations
distinguishable.

## Related techniques mentioned by the syllabus, briefly

- **Equivalence partitioning**: BVA is what you do *after* partitioning —
  it doesn't replace picking one representative value per partition
  (interior points still matter for confirming the partition's general
  behavior, not just its edges).
- **ON/OFF/IN/OUT values**: an alternative vocabulary (ON = the boundary,
  OFF = its nearest invalid neighbor, IN/OUT = any other value inside/
  outside the partition). Equivalent to 2-value BVA for one-dimensional
  domains.
- **Domain analysis**: when the rule depends on a *combination* of several
  bounded inputs at once (e.g. a rule that checks a date range AND a room
  capacity together), single-dimension BVA doesn't directly apply — that's
  domain analysis, out of scope here. Flag it explicitly rather than
  quietly picking boundary values for each dimension independently and
  assuming that's equivalent.
