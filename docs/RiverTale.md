---
level: 1
status: stable
---

Vanilla Minecraft's world generation is a technical marvel—infinite terrain from a single seed. But rivers have never quite fit. They're sea-level canals carved through terrain, not water finding its way downhill. They don't originate in highlands or lead to oceans; they just... exist. Mojang has revisited world generation multiple times, but rivers remain an afterthought.

RiverTale makes rivers that follow rules. A river exists because water would collect here. It flows toward the sea because that's downhill. It widens as tributaries join because that's how accumulation works. When a player looks at a river, they should see evidence of a world with internal logic.

## The Pillars

### 1. Rivers Are Consequences

Rivers exist *because* of terrain, not despite it. A river should feel like it had to be here—like the landscape demanded it.

**Key constraint:** Rivers are the natural flow of elevation, drainage, and accumulation.

**What this means in practice:**

- River placement derives from continental structure
- Rivers originate where elevation is high and terminate where elevation is low
- They follow valleys, not arbitrary noise patterns

### 2. Rivers Are Legible

A river tells you where you are. Narrow means you're near the headwaters; wide means tributaries have joined upstream. Downstream always points toward the ocean. If you're lost, the river knows the way.

**Key constraint:** A river widens, deepens, and descends along its length.

**What this means in practice:**

- Width reflects accumulation—how much water has joined upstream
- Direction reflects slope—downstream is always toward lower elevation
- A player can follow a river to find the ocean, or trace it back to find highlands

### 3. Rivers Are Coherent

Rivers follow consistent rules everywhere. The same algorithm that placed this river also placed that river. Chunk boundaries are invisible. Generation order is irrelevant. If two players generate the same seed, they get identical rivers—not "similar" rivers, *identical* rivers.

**Key constraint:** River generation must be purely deterministic.

**What this means in practice:**

- The same seed produces the same rivers every time
- All randomness is derived from the world seed
- Rivers don't care which chunks loaded first
- Chunk boundaries are invisible in the output

## Design Questions

When evaluating any river feature, ask these questions in order:

### Understanding Player Expectations

1. When a player encounters this feature, what do they instinctively expect to happen?
2. What would surprise or confuse a player about this feature?
3. Can a player use this feature to answer a question about the world?

### Understanding Vanilla's Gap

1. How does vanilla Minecraft handle this (if at all)?
2. What player expectations does vanilla violate?
3. What would a player *wish* vanilla did here?

### Designing the RiverTale Version

1. If a player asked "why is this here?", could you point at the terrain and explain?
2. Does this feature help players navigate or understand the world?
3. Will this feature look identical regardless of exploration order?

## Anti-Patterns

These are design tendencies to watch for. If a proposed feature exhibits any of these, stop and reconsider.

### Generation Order Artifacts

If rivers look different depending on which chunks were generated first, coherence has failed.

**Why it's tempting:** Chunk-based worldgen naturally processes regions independently. It's easier to generate "a river in this chunk" than to coordinate a river system across chunks that may not exist yet.

**Why it's wrong:** Rivers are features of the world, not artifacts of how the world was explored. If exploration order affects river placement, players in multiplayer will see different rivers than singleplayer explorers of the same seed.

### Complexity Theater

Adding systems that feel deep but don't produce better rivers. If a feature can be removed without visible impact on the generated rivers, it shouldn't exist.

**Why it's tempting:** River hydrology is a rich domain. Simulating real phenomena feels intellectually satisfying, and "that's how real rivers work" feels like justification.

**Why it's wrong:** Players can't tell the difference between a physically accurate simulation and a good approximation. If a simpler model produces visually identical rivers, the complexity adds nothing.

### Perfection Over Performance

Chasing hydrological accuracy at the expense of world generation speed. If the algorithm tanks worldgen performance to achieve perfect rivers, we've just traded one frustration for another.

**Why it's tempting:** Rivers are visible, large-scale features. If they're wrong, players notice. The pressure to get them "right" pushes toward ever-more-accurate (and expensive) algorithms.

**Why it's wrong:** A player waiting on slow chunk loading isn't admiring the river system—they're wondering why the game is lagging. Fast and good enough beats slow and perfect.

## Success Criteria

A RiverTale world passes if:

1. No river connects two separate oceans
2. A player can stand at any point in a river and correctly guess which direction leads to the ocean
3. Following a river downstream leads to ocean, a larger river, or a lake—without the river reversing direction
