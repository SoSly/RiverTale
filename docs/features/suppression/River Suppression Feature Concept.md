---
level: 2
parent: "[[RiverTale]]"
status: review
---

RiverTale rivers can't simply be added to the world. Vanilla rivers would still be there—arbitrary water channels crossing and conflicting with rivers that follow rules. River Suppression clears the canvas. It removes vanilla rivers so that RiverTale rivers are the only rivers players encounter.

## Real-World Context

Before you can build something, you have to clear the site. If you're laying a new road, you don't pour asphalt over the old broken pavement and hope for the best. You tear out what's there first.

Suppression is demolition. Vanilla rivers are the broken pavement. RiverTale can't lay down rivers that follow terrain logic on top of rivers that don't—the result would be a mess of overlapping, contradictory water features. The old rivers have to go.

## Vanilla Minecraft

Vanilla river generation produces rivers based on noise patterns with no relationship to terrain. The Rivers feature concept documents the problems this creates for players trying to navigate or understand the world.

The relevant point for suppression is simpler: vanilla rivers *exist*. As long as they exist, players will encounter two incompatible river systems—one that follows terrain logic, one that doesn't. This contradicts the Coherence pillar directly.

## The RiverTale Version

### The Fantasy

There is no fantasy here. River Suppression is invisible when it works correctly. Players don't notice the absence of vanilla rivers; they simply experience a world where rivers make sense.

The fantasy belongs to the Rivers feature. Suppression enables it.

### Foundation Mapping

River Suppression is an enabling feature. It doesn't directly satisfy the design pillars—it removes obstacles that would prevent rivers from satisfying them.

#### Rivers as Consequences

Vanilla rivers aren't consequences of anything. They exist because noise patterns said so, not because terrain demanded them. Suppressing vanilla rivers removes the counterexamples that would undermine the "rivers are consequences" message.

If vanilla rivers remain, players see rivers that follow terrain logic next to rivers that ignore it. The lesson becomes "some rivers are consequences, some aren't," which defeats the purpose.

#### Rivers as Legible

Vanilla rivers teach players nothing. Following them doesn't lead anywhere meaningful. If vanilla rivers remain alongside RiverTale rivers, players can't trust that following a river downstream will lead to the ocean—it might be a vanilla river that connects two oceans or dead-ends in a mountain.

Suppression ensures that every river players encounter follows the rules that make rivers legible.

#### Rivers as Coherent

Two river systems would be incoherent by definition. Coherence means rivers follow consistent rules everywhere. Having vanilla rivers that don't follow the rules alongside RiverTale rivers that do would violate coherence directly.

### Observable States

Suppression has exactly two observable states:

| State | What You See | What It Means |
|-------|--------------|---------------|
| Success | No vanilla river biomes or water channels | Suppression is working |
| Failure | Vanilla rivers visible alongside RiverTale rivers | Suppression has failed |

Partial success is not a state. If any vanilla rivers remain, suppression has failed. The feature is binary.

## Scope Boundaries

### What Gets Suppressed

- Vanilla river biomes
- Vanilla river terrain carving (the water-filled channels)

### What Doesn't Get Suppressed

- Ocean biomes and water bodies
- Aquifers and underground water

### Open Question: Other Surface Water

Vanilla also generates ponds, lakes, and swamp water features based on noise patterns. These have the same "arbitrary placement" problem as rivers, but at smaller scale.

**Lakes** are particularly relevant because RiverTale's endorheic basins terminate in lakes. If vanilla lakes exist alongside intentionally-placed basin lakes, players might not be able to distinguish "this lake means something" from "this lake is noise."

**Ponds and swamp water** are smaller features. It's unclear whether their noise-based placement undermines the fantasy or whether they're ignorable background detail.

This needs more thought before the scope boundary is finalized. For now, the minimum viable suppression targets rivers only. Lake/pond suppression may be added if it proves necessary for terrain legibility.

### Datapack Compatibility

River suppression must work with other datapacks that modify terrain generation. A solution that only works with vanilla worldgen, or only with specific terrain datapacks, is incomplete. Suppression must work with whatever terrain generation system produced the rivers.

## Success Criteria

River Suppression succeeds if:

1. No vanilla river biomes appear in generated terrain
2. No vanilla river water channels appear in generated terrain
3. Terrain where vanilla rivers would have been looks natural (no visible scars or artifacts)
4. Suppression works regardless of which terrain generation system (vanilla, datapacks) produced the rivers
5. Suppression does not noticeably impact world generation performance

## Failure Modes

| Failure | Player Experience | Severity |
|---------|-------------------|----------|
| Vanilla rivers appear | Two incompatible river systems | Critical |
| Visible artifacts at former river locations | Unexplained terrain scars | High |
| Suppression only works with specific datapacks | Rivers in some worlds, not others | High |
| Performance degradation during worldgen | Noticeable lag exploring new terrain | Medium |

## Related Features

- **Rivers** — Suppression enables the Rivers feature by clearing vanilla rivers first. Without suppression, RiverTale rivers would coexist with vanilla rivers, undermining legibility and coherence.
