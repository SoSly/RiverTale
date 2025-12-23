---
cellType: poc
validates: "[[Cell-based Networks Architecture]]"
assumption: "D8 flow accumulation within cells produces more accurate drainage than density averaging, without significant performance cost"
status: pending
---

## Goal

The current system averages 64 density samples (8×8 grid) to get a single region density value. This loses spatial information—a region with cliffs on one side and plains on the other gets an "average" density that doesn't represent either area accurately.

This PoC tests whether computing actual D8 flow accumulation within each region:
1. Produces more realistic flow decisions at region boundaries
2. Identifies natural drainage outlets rather than using edge centers
3. Remains fast enough for real-time use (target: <2× current computation time)

**Design decisions that depend on this:**
- Whether to replace averaged density with edge-based flow comparison
- Whether to compute intra-region drainage paths vs. use A* pathfinding
- Whether region flow direction should consider boundary gradient rather than region-to-region average

## Approach

Add a `/rivertale visualize d8` command that computes and renders D8 flow accumulation alongside the existing `/rivertale visualize` command. This allows direct visual and performance comparison.

### Implementation

**New computation in `RiverCellManager` or a new `D8FlowCalculator` class:**

```java
public class D8FlowResult {
    int[][] flowDirection;     // 8x8 grid, each cell points to steepest neighbor (0-7 or -1 for sink)
    int[][] accumulation;      // 8x8 grid, count of upstream cells draining through each point
    List<int[]> exitPoints;    // Cells on edges with highest accumulation (natural outlets)
    List<int[]> sinks;         // Internal minima (potential ponds)
}
```

**D8 Algorithm (per region):**
1. For each of 64 cells, compare density to 8 neighbors
2. Record direction toward steepest descent (or -1 if local minimum)
3. Trace flow from each cell until it exits or sinks
4. Count how many paths cross each cell (accumulation)
5. Identify edge cells with high accumulation (natural drainage points)

**Visualization additions:**
- Render flow direction arrows at each cell
- Color cells by accumulation (blue gradient: low→high)
- Highlight natural exit points (high-accumulation edge cells)
- Highlight internal sinks (red)

**Performance measurement:**
- Time the D8 computation separately from existing region computation
- Log timing for both `visualize` and `visualize d8`
- Compare ms per region for each approach

### Test Scenarios

**The screenshot scenario:**
Two adjacent cells where one has cliffs on the far side. With averaging, the cliff region appears "higher" even though the shared boundary is flat.

| Test | Setup | Current Behavior | Expected D8 Behavior |
|------|-------|------------------|----------------------|
| Cliff region | Cliffs on south edge, plains on north | High average density blocks flow from north | North edge shows low density, allows flow |
| Valley region | Depression in center | Average may hide the depression | Accumulation shows water pooling in center |
| Ridge region | Ridge across middle | Average smooths out the ridge | Flow diverges around ridge, multiple exits |

**Edge comparison test:**
For adjacent cells A and B, compare:
- Current: `A.avgDensity < B.avgDensity` → flow B→A
- D8: `A.edgeDensities[shared_edge]` vs `B.edgeDensities[shared_edge]` → compare actual boundary values

## Success Criteria

**Performance:**
| Rating | D8 Time vs Current | Verdict |
|--------|-------------------|---------|
| Failure | >5× slower | Unacceptable for production |
| Acceptable | 2-5× slower | Proceed with optimization work |
| Good | 1-2× slower | Proceed confidently |
| Excellent | ≤1× (same or faster) | Immediate adoption |

Current region computation is ~40ms for a 8×8 region (64 cells). D8 adds 64 comparisons + 64 path traces per region.

**Visual accuracy:**
| Rating | Observation |
|--------|-------------|
| Failure | D8 flow directions contradict visible terrain |
| Acceptable | D8 matches terrain better than averaging in obvious cases |
| Good | D8 identifies drainage points that match terrain contours |
| Excellent | D8 flow accumulation clearly shows where rivers "should" form |

**The cliff test:**
| Rating | Result |
|--------|--------|
| Failure | D8 still blocks flow due to distant cliffs |
| Success | D8 allows flow across flat boundary despite cliff on far side |

## Results

*Not yet run.*

## Conclusions

*Pending results.*
