Vanilla Minecraft rivers are canals—arbitrary water channels imposed on terrain. RiverTale makes rivers emerge *from* terrain, so they mean something. A river exists because the landscape demands it.

## The Pillars

### 1. Rivers Are Consequences

Rivers exist *because* of terrain, not despite it. They're the natural result of elevation, drainage, and accumulation. A river should feel like it had to be here—like the landscape demanded it.

**What this means in practice:** River placement derives from continental structure. Rivers originate where elevation is high and terminate where elevation is low. They follow valleys, not arbitrary noise patterns.

### 2. Rivers Are Legible

Looking at a river tells you something true about the world. Width indicates accumulation—narrow means near the source, wide means many tributaries have joined. Direction indicates slope—downstream points toward the sea. The river is information, not decoration.

**What this means in practice:** River properties encode geographic facts. A player can use rivers to navigate, to find highlands, to locate the ocean. The river's appearance answers questions about the world.

### 3. Rivers Are Coherent

Rivers follow consistent rules everywhere. The same algorithm that placed this river also placed that river. Chunk boundaries are invisible. Generation order is irrelevant. If two players generate the same seed, they get identical rivers—not "similar" rivers, *identical* rivers.

**What this means in practice:** River generation must be purely deterministic based on world seed and coordinates. No randomness that isn't seeded. No state that depends on which chunks loaded first.

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

If rivers look different depending on which chunks were generated first, coherence has failed. Rivers are features of the world, not artifacts of how the world was explored.

### Complexity Theater

Adding systems that feel deep but don't produce better rivers. If a feature can be removed without visible impact on the generated rivers, it shouldn't exist.

### Perfection Over Performance

Chasing hydrological accuracy at the expense of world generation speed. If the algorithm tanks worldgen performance to achieve perfect rivers, we've just traded one frustration for another. A player waiting on slow chunk loading isn't admiring the river system—they're wondering why the game is lagging.

## Success Criteria

A RiverTale world passes if:

1. No river connects two separate oceans
2. A player can stand at any point in a river and correctly guess which direction leads to the ocean
3. Following a river downstream leads to ocean, a larger river, or a lake—without the river reversing direction
