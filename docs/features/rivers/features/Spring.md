---
level: 5
parent: "[[Rough Diagrams]]"
status: draft
---

# Spring

A `SPRING` is a river source where groundwater emerges at a distinct point.

## Classification criteria

A land cell qualifies if no earlier source type matches, none of its eight neighbors has a terrain flow direction pointing into it, and its average ridge value is below `sources.springMaxRidges` (default `0.5`). At least one outgoing terrain flow direction must lead to a land neighbor at or above `sources.springMinGrade` (default `0.125`, or 1:8). Ocean and basin cells do not count as land.

Grade is the drop between the cells’ average terrain heights divided by the horizontal distance between their centers. A diagonal step is √2 times longer than a cardinal step. There is no minimum absolute elevation; the downhill step must have a positive drop even if the configured minimum grade is zero.

## Examples

With 64-block cells and the default grade, a cardinal outlet needs a drop of at least 8 blocks; a diagonal outlet needs at least 12. A hillside at Y=105 draining toward a land cell at Y=95 can qualify on a cardinal step, while a plateau at Y=130 draining toward Y=128 cannot.
