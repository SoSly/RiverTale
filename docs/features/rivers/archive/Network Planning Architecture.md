---
level: 3
parent: "[[Rivers]]"
status: stable
---

Network Planning determines flow direction, connectivity, width, and water elevation across the world, producing a complete watershed for River Shaping to carve into terrain.

## System Diagram

```mermaid
flowchart TD
    subgraph Input
        WG[("World Generation")]
    end

    subgraph Network Planning
        TS["Terrain Sampling"]
        FC_early["Feature Classification<br/>(early phase)"]
        RPG["River Path Generation"]
        WA["Watershed Aggregation<br/>(includes late-phase classification)"]
    end

    subgraph Output
        RS[("Terrain Shaping")]
    end

    WG -->|"Minecraft noise"| TS
    TS -->|"flow direction,<br/>density values"| FC_early
    FC_early -->|"identified sources"| RPG
    TS -.->|"density values"| WA
    RPG -->|"traced paths"| WA
    WA -->|"classified watershed<br/>(structure, width, elevation, labels)"| RS
```

Terrain Sampling outputs (flow direction, density values) are available to downstream subsystems (shown as dashed lines).

## Subsystems

### Terrain Sampling

Reads Minecraft's terrain data to determine where rivers should be planned and how water flows across the landscape.

**Owns:**

- Ocean location detection
- Area classification (which areas need river planning)
- Terrain density values
- Flow direction computation

**Does not:**

- Trace paths
- Build network structure
- Compute width or water elevation

**Outputs:**

- Flow direction
- Density values

### River Path Generation

Traces complete paths from identified sources toward the ocean. Each path is traced in isolation, without knowledge of other paths.

**Owns:**

- Path tracing
- Path validation
- Terminus selection

**Does not:**

- Know about other paths
- Handle path merging
- Compute width or elevation

**Outputs:**

- Complete traced paths (source to terminus)

### Watershed Aggregation

Transforms individual river paths into a unified drainage network. Where paths meet, they merge into larger rivers. Computes segment width from upstream tributary count and assigns water surface elevations that flow monotonically downhill. Performs late-phase feature classification once network structure is known.

**Owns:**

- Path merging
- Conflict resolution (larger drainage wins)
- Tributary relationships
- Network structure
- Segment width (derived from upstream tributary count)
- Water surface elevation (derived from terrain via cached samples, corrected for monotonic downhill flow)
- Late-phase feature classification (courses, junctions, termini, lakes)

**Does not:**

- Trace original paths
- Identify sources (handled by early-phase classification)

**Outputs:**

- Classified drainage network with computed values (structure, width, elevation, flow relationships, feature labels)

### Feature Classification

Assigns classification labels based on each cell's role in the network. Runs in two phases:

- **Early phase:** Identifies sources before path generation (cells with outflow but no upstream neighbors)
- **Late phase:** Classifies remaining features during Watershed Aggregation once network structure is known

Classification labels fall into categories based on network role:

- **Sources** — where rivers originate; network entry points
- **Courses** — river segments between junctions
- **Junctions** — where channels merge or split
- **Termini** — where rivers end; network exit points (ocean, larger river)
- **Lakes** — enclosed water bodies in endorheic basins

Cells outside river networks receive no special classification (DEFAULT).

Specific features within each category are defined at the specification level.

**Owns:**

- Source identification (early phase)
- Segment classification labels (late phase, executed within Watershed Aggregation)

**Does not:**

- Modify network structure
- Modify computed values (width, elevation)
- Trace paths or build network

## Key Decisions

| Decision                 | Choice                                        | Rationale                                                            |
| ------------------------ | --------------------------------------------- | -------------------------------------------------------------------- |
| Worldgen phase           | Noise generation (not features/decoration)    | Terrain density available; surface rules decorate our modifications  |
| Terrain discretization   | Local units with flow computed from neighbors | Enables detailed paths without global optimization                   |
| Ocean-directed paths     | Force progress toward ocean                   | Guarantees rivers reach coast even on flat terrain                   |
| Two-phase classification | Early pass without network, late pass with    | Source detection doesn't need context; course features do            |
| Conflict resolution      | Larger drainage wins                          | Prevents crossings; matches natural river behavior                   |
| Width computation        | Derived from upstream tributary count         | Reflects natural river behavior; network structure already available |
| Elevation correction     | Post-process to ensure monotonic downhill     | Terrain may have local rises; water must always flow down            |

## Integration Points

**Depends on:**

- Minecraft noise functions for terrain density
- Configuration for thresholds and sizes

**Provides to:**

- Terrain Shaping system: Complete annotated watershed (network structure, width, elevation, classifications)
- Debug rendering: River locations, segment classifications, flow relationships

**Potential conflicts:**

- Ocean boundary detection must align with Minecraft's ocean biome placement

## Emergent Behaviors

**Tributaries:** Rivers naturally form tributary systems where paths converge into larger drainage networks.

**Determinism:** All computation derives from world seed. Same seed produces identical river networks regardless of exploration order.

**Graceful degradation:** Continental interiors far from oceans simply have no rivers planned. The system doesn't fail; it just produces fewer rivers where drainage to the ocean isn't feasible.
