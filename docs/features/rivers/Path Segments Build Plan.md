---
level: 5
parent: "[[Cell-based Networks Build Plan]]"
status: draft
---

# Path Segments Build Plan

This document outlines the refactoring of river path representation from `Map<Direction, List<CellPos>>` to a segment-based model. The goal is to fix validation bugs where invalidating one tributary incorrectly removes shared downstream cells.

## Implementation Philosophy

The phases below represent logical ordering of work, not compilation checkpoints. The code is not expected to compile until the entire refactor is complete. This allows phases to reference types and methods that don't exist yet, and avoids the complexity of maintaining intermediate compatibility layers.

Work on a feature branch. Complete all phases. Then compile, fix errors, and test.

## Problem Statement

Currently, paths are stored as independent lists keyed by input direction. When paths merge at a confluence, the downstream segment is "owned" by whichever path was drawn first. If that path fails validation, the entire path is removed, including the shared downstream cells. This orphans other tributaries that merged into it.

**Example:**

```
Path from NORTH: (0,6) -> (1,7)
Path from SOUTH: (7,3) -> ... -> (1,3) -> (1,4) -> (1,5) -> (0,6)
Path from WEST:  (1,0) -> (1,1) -> (1,2) -> (1,3)
```

NORTH enters at (0,6) and immediately reaches the output. If NORTH fails tributary validation (too short), the current code removes `(0,6) -> (1,7)`. But SOUTH also needs that segment to reach the output.

## Key Decisions

| Decision | Value | Rationale |
|----------|-------|-----------|
| Graph structure | Node + Segment | Nodes are decision points (edges, confluences); Segments connect nodes |
| Mutability | Immutable | All types are records; validation rebuilds rather than mutates |
| Node ownership | Nodes own their cell | Each cell is either a Node's cell or in a Segment's cells list; no overlap |
| Segment cells | Exclusive of endpoints | Segment.cells contains only cells BETWEEN start and end nodes |
| Invalid path handling | Rebuild | Validation identifies invalid directions, then rebuilds graph excluding them |
| Cross-region validation | Compute lazily | Trace through crossings during validation, same as current |

## Architecture Overview

```
Before (current):
  Flow.riverPaths: Map<Direction, List<CellPos>>
  - NORTH -> [(0,6), (1,7)]
  - SOUTH -> [(7,3), (6,3), ..., (0,6)]  <- stops at confluence
  - WEST  -> [(1,0), (1,1), (1,2), (1,3)]  <- stops at confluence

After (Node + Segment graph):
  Flow.graph: RiverGraph

  RiverGraph.nodes: List<Node>
  RiverGraph.segments: List<Segment>

  Nodes (decision points):
    N1: cell=(0,6), inDir=NORTH, outDir=null   ← NORTH enters here, confluence with SOUTH
    N2: cell=(1,7), inDir=null, outDir=EAST    ← output
    N3: cell=(1,3), inDir=null, outDir=null    ← interior confluence (SOUTH meets WEST)
    N4: cell=(7,3), inDir=SOUTH, outDir=null   ← SOUTH enters here
    N5: cell=(1,0), inDir=WEST, outDir=null    ← WEST enters here

  Segments (cells between nodes):
    S1: cells=[], start=N1, end=N2             ← empty (N1 and N2 are adjacent)
    S2: cells=[(1,4), (1,5)], start=N3, end=N1 ← interior cells between confluences
    S3: cells=[(6,3), (5,3), (4,3), (3,3), (2,3)], start=N4, end=N3
    S4: cells=[(1,1), (1,2)], start=N5, end=N3

  Full path for NORTH: N1.cell + S1.cells + N2.cell = [(0,6)] + [] + [(1,7)]
  Full path for WEST:  N5.cell + S4.cells + N3.cell + S2.cells + N1.cell + S1.cells + N2.cell
```

When NORTH is invalidated:
1. Identify N1 as NORTH's entry node
2. Rebuild graph excluding NORTH: N1 loses inDir, but still has upstream (S2 from SOUTH/WEST)
3. If N1 has no inDir AND no upstreams → remove N1 and cascade
4. Otherwise N1 stays; SOUTH and WEST paths remain intact

When an output node is invalidated:
1. Rebuild graph excluding that output
2. All segments leading to it become invalid (no destination)
3. Their start nodes lose a downstream; if they have no other purpose, cascade removal

## File Structure

| File | Change | Responsibility |
|------|--------|----------------|
| `path/Node.java` | New | Record: cell, inDir, outDir |
| `path/Segment.java` | New | Record: cells, start (Node), end (Node) |
| `path/RiverGraph.java` | New | Record: nodes, segments; methods for traversal and rebuilding |
| `path/GraphBuilder.java` | New | Mutable builder for constructing RiverGraph during path-finding |
| `path/Flow.java` | Modify | Replace `riverPaths` with `RiverGraph` |
| `path/Refiner.java` | Modify | Build graph directly using GraphBuilder (no intermediate map) |
| `path/validators/LengthValidator.java` | Modify | Validate using graph |
| `path/validators/TributaryValidator.java` | Modify | Validate using graph |
| `path/MutableFlowState.java` | Delete | Replaced by single-pass validation pattern |
| `path/Validator.java` | Modify | Orchestrate single-pass validation |

## Validation Strategy

Validation uses a **single-pass** approach: all inputs are evaluated against the current graph state, invalid directions are collected, then one `excluding()` call removes them all.

Validators are evaluated in cardinal direction order (NORTH, EAST, SOUTH, WEST) for determinism. This ensures identical results regardless of which direction the player approaches from or how much of the river network has been computed.

```
Set<Direction> invalid = new HashSet<>();
for (Direction dir : List.of(NORTH, EAST, SOUTH, WEST)) {
    if (!LengthValidator.isValid(dir, graph, ctx)) {
        invalid.add(dir);
    }
}
for (Direction dir : List.of(NORTH, EAST, SOUTH, WEST)) {
    if (!TributaryValidator.isValid(dir, graph, ctx)) {
        invalid.add(dir);
    }
}
RiverGraph validated = graph.excluding(invalid);
return flow.withGraph(validated);
```

This means two short tributaries that both fail validation will both be removed, even if removing one would have "saved" the other by eliminating the confluence. This is intentional: determinism over leniency.

## Phased Build Order

### Phase 1: Core Types

**Goal:** Define Node, Segment, RiverGraph as immutable records, and GraphBuilder for construction.

**Files:** `path/Node.java`, `path/Segment.java`, `path/RiverGraph.java`, `path/GraphBuilder.java` (all new)

**Details:**

```
Node:
  cell: CellPos               // The cell at this decision point
  inDir: Direction            // Non-null if a river enters the region here
  outDir: Direction           // Non-null if a river exits the region here

  // Derived properties
  isInputEdge(): inDir != null
  isOutputEdge(): outDir != null
  isInterior(): inDir == null && outDir == null

Segment:
  cells: List<CellPos>        // Cells BETWEEN start and end nodes (exclusive of both)
  start: Node                 // Node where this segment begins
  end: Node                   // Node where this segment ends

  // Derived properties
  length(): cells.size()      // Interior cells only; nodes counted separately

  // IMPORTANT: Segment endpoint comparisons use cell equality, not reference equality.
  // GraphBuilder may merge nodes (updating inDir/outDir), creating new Node objects
  // while segments still reference the old objects. Always compare via:
  //   segment.start.cell().equals(node.cell())
  // Never use reference equality (==) for segment endpoint matching.

RiverGraph:
  nodes: List<Node>
  segments: List<Segment>

  // Lookup
  getNodeAt(CellPos): Node at that cell, or null if none

  // Traversal
  getInputNode(Direction): Node with that inDir
  getOutputNode(Direction): Node with that outDir
  getInputDirections(): Set of directions with input nodes (nodes where inDir != null)
  getSegmentsFrom(Node): segments where start.cell == node.cell
  getSegmentsTo(Node): segments where end.cell == node.cell
  getPathFor(Direction): ordered list of cells from input to output

  // Rebuilding
  excluding(Set<Direction> invalidInputs): new RiverGraph without those inputs

getPathFor(direction) algorithm:
  inputNode = getInputNode(direction)
  if inputNode == null:
    return []

  path = [inputNode.cell]
  current = inputNode

  while true:
    outSegments = getSegmentsFrom(current)
    if outSegments.isEmpty():
      break  // Reached terminus or output

    if outSegments.size() > 1:
      throw IllegalStateException("Node has multiple downstream segments - graph is malformed")

    segment = outSegments.get(0)
    path.addAll(segment.cells)
    path.add(segment.end.cell)
    current = segment.end

  return path

Path length for validation: path.size() (which equals sum of segment.length() + node count)
```

All types are records (immutable). Validation produces a new RiverGraph rather than mutating.

```
GraphBuilder:
  nodes: Map<CellPos, Node>       // Nodes by their cell position
  segments: List<Segment>
  visitedCells: Set<CellPos>      // All cells covered by any path so far

  getOrCreateNode(cell, inDir, outDir): Node
    existing = nodes.get(cell)
    if existing != null:
      // Merge: keep existing inDir/outDir if set, use new values if not
      merged = Node(cell, existing.inDir ?? inDir, existing.outDir ?? outDir)
      nodes.put(cell, merged)
      return merged
    else:
      node = Node(cell, inDir, outDir)
      nodes.put(cell, node)
      return node

  addSegment(cells, startNode, endNode):
    segments.add(Segment(cells, startNode, endNode))

  markVisited(cell):
    visitedCells.add(cell)

  isVisited(cell): boolean
    return visitedCells.contains(cell)

  build(): RiverGraph
    return RiverGraph(List.copyOf(nodes.values()), List.copyOf(segments))
```

GraphBuilder is mutable during construction, but produces an immutable RiverGraph at the end.

**Validation:** Code compiles. Write unit tests for Node, Segment, RiverGraph traversal, and GraphBuilder construction.

---

### Phase 2: Graph Building in Refiner

**Goal:** Build RiverGraph directly in Refiner using GraphBuilder, eliminating the intermediate `Map<Direction, List<CellPos>>` representation.

**Files:** `path/Refiner.java`

**Details:**

The existing `findPath()` method returns a flat `List<CellPos>`. Instead of collecting these into a map and converting later, we pass a GraphBuilder and add paths directly.

**Path addition by region type:**

```
addFlowPath(builder, cells, inputDir, outputDir):
  // Normal regions: input -> output (or confluence)
  if cells.isEmpty(): return

  startNode = builder.getOrCreateNode(cells.first(), inputDir, null)
  builder.markVisited(cells.first())

  // Single-cell path: just a node, no segment
  if cells.size() == 1:
    return

  currentNode = startNode
  cellsBetween = []

  for cell in cells (skipping first and last):
    if builder.isVisited(cell) || builder.isForbidden(cell):
      // Confluence or forbidden: this cell is already claimed
      confluenceNode = builder.getOrCreateNode(cell, null, null)
      builder.addSegment(cellsBetween, currentNode, confluenceNode)
      return  // Stop here - downstream already exists or is blocked

    builder.markVisited(cell)
    cellsBetween.add(cell)

  // Check if last cell is a confluence or forbidden
  lastCell = cells.last()
  if builder.isVisited(lastCell) || builder.isForbidden(lastCell):
    confluenceNode = builder.getOrCreateNode(lastCell, null, null)
    builder.addSegment(cellsBetween, currentNode, confluenceNode)
    return

  // Reached output without hitting a confluence
  outputNode = builder.getOrCreateNode(lastCell, null, outputDir)
  builder.markVisited(lastCell)
  builder.addSegment(cellsBetween, currentNode, outputNode)


addDividePath(builder, cell, outputDir):
  // Divide regions: single-cell output node, no segments
  builder.getOrCreateNode(cell, null, outputDir)
  builder.markVisited(cell)


addTerminusPath(builder, cells, inputDir):
  // Basin/Shore: input -> terminus (no output direction)
  if cells.isEmpty(): return

  startNode = builder.getOrCreateNode(cells.first(), inputDir, null)
  builder.markVisited(cells.first())

  // Single-cell path: input cell IS the terminus, no segment needed
  if cells.size() == 1:
    return

  currentNode = startNode
  cellsBetween = []

  for cell in cells (skipping first and last):
    if builder.isVisited(cell) || builder.isForbidden(cell):
      confluenceNode = builder.getOrCreateNode(cell, null, null)
      builder.addSegment(cellsBetween, currentNode, confluenceNode)
      return

    builder.markVisited(cell)
    cellsBetween.add(cell)

  // Check if last cell is a confluence or forbidden
  lastCell = cells.last()
  if builder.isVisited(lastCell) || builder.isForbidden(lastCell):
    confluenceNode = builder.getOrCreateNode(lastCell, null, null)
    builder.addSegment(cellsBetween, currentNode, confluenceNode)
    return

  // Terminus node: no outDir
  terminusNode = builder.getOrCreateNode(lastCell, null, null)
  builder.markVisited(lastCell)
  builder.addSegment(cellsBetween, currentNode, terminusNode)
```

**Key insight:** Each cell appears exactly once - either as a Node's cell, or in a Segment's cells list. Confluences are detected when a path reaches an already-visited cell.

**Region-type handling (maps to existing Refiner code paths):**

| Current Method | Region Type | Graph Builder Method | Notes |
|----------------|-------------|---------------------|-------|
| `buildPathsToOutput()` | Normal with inputs | `addFlowPath` | Input crossings → primary output |
| `buildDividePaths()` | Normal without inputs | `addDividePath` | Output crossings only, no segments |
| `buildBasinPaths()` | Basin | `addTerminusPath` | Input crossings → single-cell terminus |
| `buildShorePaths()` | Shore | `addTerminusPath` | Input crossings → nearest water cell |

**Secondary output handling:**

Secondary outputs must remain forbidden during pathfinding. Currently `collectInputsAndForbidden()` adds secondary output cells to the `forbidden` set. With the graph model:

```
GraphBuilder:
  forbiddenCells: Set<CellPos>    // Secondary output cells - paths cannot cross these

  addForbidden(cell):
    forbiddenCells.add(cell)

  isForbidden(cell): boolean
    return forbiddenCells.contains(cell)
```

Update `addFlowPath` and `addTerminusPath` to check `isForbidden(cell)` alongside `isVisited(cell)`. If a path would enter a forbidden cell, terminate the path at the previous cell (creating a terminus node there).

Before building paths, populate `forbiddenCells` with all secondary output crossing cells:
```
for each crossing in crossings:
  if crossing.isSource(regionPos) && crossing.direction != primaryOutputDirection:
    builder.addForbidden(crossing.cellFor(regionPos))
```

**Validation:**
- Log graph structure in debug mode
- Verify nodes and segments match expected structure for the example region
- Test each region type produces correct graph structure

---

### Phase 3: Flow Updates

**Goal:** Integrate RiverGraph into Flow.

**Files:** `path/Flow.java`

**Flow changes:**
- Replace `Map<Direction, List<CellPos>> riverPaths` with `RiverGraph graph`
- Replace `List<CellPos> confluenceCells` with nothing (confluences are nodes with multiple upstream segments)
- Add convenience methods that delegate to graph:
  - `getPathFor(Direction)`: delegates to `graph.getPathFor()`
  - `getInputDirections()`: delegates to `graph.getInputDirections()`
- Add `withGraph(RiverGraph)`: returns new Flow with updated graph (for validation result)

**Terminus cell derivation:**

Currently `terminusCells` is populated by `collectPathEndpoints()` which takes the last cell from each path. With the graph model, terminus cells are derived from graph structure:

```
getTerminusCells():
  termini = []
  for each node in graph.nodes:
    // A terminus is a node that:
    // - Has no output direction (not an output crossing)
    // - Has no downstream segments (flow ends here)
    if node.outDir == null && graph.getSegmentsFrom(node).isEmpty():
      // Also must have upstream flow (not an isolated divide output)
      if node.inDir != null || !graph.getSegmentsTo(node).isEmpty():
        termini.add(node.cell)
  return termini
```

This preserves existing behavior: only shore/basin regions produce terminus cells (where rivers end at water or basins rather than crossing to another region).

**Delete MutableFlowState.java** - no longer needed. Validation is now single-pass with immutable data.

**Validation:** Existing tests pass with updated accessors.

---

### Phase 4: Refactor LengthValidator

**Goal:** Convert to pure function that validates using graph.

**Files:** `path/validators/LengthValidator.java`

**Details:**

Validator becomes a pure function: `(Direction, RiverGraph, ValidationContext) → boolean`. No mutation, no MutableFlowState dependency.

Current logic traces path length by following cells. New logic must handle both input paths (normal regions) and output-only paths (divide regions):

```
isValidLength(direction, graph, ctx):
  inputNode = graph.getInputNode(direction)
  outputNode = graph.getOutputNode(direction)

  if inputNode != null:
    // Input path: trace both upstream and downstream
    localPath = graph.getPathFor(direction)
    localLength = localPath.size()
    totalLength = localLength + traceUpstream(direction, ...) + traceDownstream(...)
    return totalLength >= ctx.minimumRiverLength()

  if outputNode != null:
    // Output-only path (divide region): trace downstream only
    localLength = 1  // just the output node cell
    totalLength = localLength + traceDownstream(direction, ...)
    return totalLength >= ctx.minimumRiverLength()

  return false  // No node for this direction
```

The cross-region tracing remains lazy and unchanged. Local length is now just the path size (no deduplication needed since cells don't overlap).

**Validation:**
- Run existing validation tests
- Verify length calculations produce same results as before

---

### Phase 5: Refactor TributaryValidator

**Goal:** Convert to pure function that validates tributaries using graph.

**Files:** `path/validators/TributaryValidator.java`

**Details:**

Validator becomes a pure function: `(Direction, RiverGraph, ValidationContext) → boolean`. No mutation, no MutableFlowState dependency.

Current logic finds the confluence index in a path and measures length to that point. New logic:

```
isValidTributary(direction, graph, ctx):
  inputNode = graph.getInputNode(direction)
  if inputNode == null:
    return false

  // Walk from input toward output, looking for confluence nodes
  path = graph.getPathFor(direction)

  cellCount = 0
  for cell in path:
    cellCount++
    node = graph.getNodeAt(cell)
    if node == null:
      continue  // Interior cell, not a decision point

    // A confluence is where multiple flows meet:
    // - Multiple upstream segments, OR
    // - An external input (inDir) plus at least one upstream segment
    upstreamSegments = graph.getSegmentsTo(node)
    upstreamCount = upstreamSegments.size() + (node.inDir != null ? 1 : 0)
    if upstreamCount > 1:
      // This node is a confluence - multiple paths meet here
      tributaryLength = cellCount + traceUpstream(...)  // Cross-region
      return tributaryLength >= ctx.minimumRiverLength()

  // No confluence - this direction doesn't merge with anything
  return true
```

A node is a confluence if multiple flows arrive at it. This includes both upstream segments AND external inputs (nodes with `inDir` set). We use `getNodeAt(cell)` to check each cell along the path - most cells return null (they're segment interiors), but nodes at confluences will be found.

**Validation:**
- Write test case for the example region
- Verify NORTH is invalidated but SOUTH and WEST retain the shared downstream

---

### Phase 6: Graph Rebuilding

**Goal:** Implement immutable graph rebuilding when inputs are invalidated.

**Files:** `path/RiverGraph.java`

**Details:**

Instead of rebuilding from flat paths (which stop at confluences and don't contain shared downstream segments), we prune the existing graph structure:

```
RiverGraph.excluding(invalidInputs: Set<Direction>):
  // Start with copies of current nodes and segments
  remainingNodes = new Set(this.nodes)
  remainingSegments = new Set(this.segments)

  // Clear inDir from invalidated input nodes
  for each dir in invalidInputs:
    inputNode = getInputNode(dir)
    if inputNode != null:
      // Replace with node that has no inDir (immutable update)
      remainingNodes.remove(inputNode)
      updatedNode = Node(inputNode.cell, null, inputNode.outDir)
      remainingNodes.add(updatedNode)

      // Update segment references to point to updated node
      updateSegmentEndpoints(remainingSegments, inputNode.cell(), updatedNode)

  // Prune orphaned nodes iteratively
  changed = true
  while changed:
    changed = false
    for each node in remainingNodes:
      hasInDir = node.inDir != null
      hasOutDir = node.outDir != null
      hasUpstream = any segment in remainingSegments where segment.end.cell().equals(node.cell())

      // A node survives if it has:
      // - An external input (inDir), OR
      // - An upstream segment, OR
      // - An output direction (outDir) - it's a source/divide point
      if !hasInDir && !hasUpstream && !hasOutDir:
        // Node is orphaned - remove it and its segments
        remainingNodes.remove(node)
        for each segment where segment.start.cell().equals(node.cell())
                           || segment.end.cell().equals(node.cell()):
          remainingSegments.remove(segment)
        changed = true

  return new RiverGraph(remainingNodes, remainingSegments)

updateSegmentEndpoints(segments, oldCell, newNode):
  for each segment in segments (copy to avoid concurrent mod):
    if segment.start.cell().equals(oldCell) || segment.end.cell().equals(oldCell):
      segments.remove(segment)
      newStart = segment.start.cell().equals(oldCell) ? newNode : segment.start
      newEnd = segment.end.cell().equals(oldCell) ? newNode : segment.end
      segments.add(Segment(segment.cells, newStart, newEnd))
```

A node survives if it has `inDir != null` OR `outDir != null` OR at least one segment ends at it (upstream connection). Nodes with `outDir` are sources (divide points) and should never be pruned. Segments survive if both their start and end nodes survive.

When NORTH is invalidated:
1. Find N1 (NORTH's entry node at 0,6)
2. Replace N1 with a copy that has `inDir=null`
3. Check if N1 is orphaned: it has `inDir=null` but S2 ends at it (from SOUTH/WEST)
4. N1 survives; S1 (N1→N2) survives; SOUTH and WEST paths remain intact

When an output is invalidated:
1. Find the output node and clear its `outDir`
2. Prune nodes that now have no downstream path to any output
3. Segments connecting to pruned nodes are removed
4. Cascade continues until no orphaned nodes remain

**Validation:**
- Test the example region scenario end-to-end
- Verify SOUTH's path is intact after NORTH removal
- Verify node/segment count is correct after rebuild

---

### Phase 7: Consumer Updates

**Goal:** Update any code that consumes river paths to use RiverGraph.

**Files:** `network/VisualizePacket.java`, `client/FlowRenderer.java`, `command/VisualizeCommand.java`, any other consumers of `Flow.riverPaths()`

**Details:**

Network packet changes:
- `D8RegionData` replaces `Map<Direction, List<CellPos>> riverPaths` with `List<Node> nodes` and `List<Segment> segments`
- Serialize nodes as: cell row/col, inDir (nullable), outDir (nullable)
- Serialize segments as: start node index, end node index, cell list
- Remove `confluenceCells` from packet (confluences are now nodes with multiple upstream segments)

FlowRenderer changes:
- Draw nodes as boxes with color coding:
  - Input nodes (has inDir): orange
  - Output nodes (has outDir): purple/blue
  - Interior nodes (confluence only): green
- Draw segments as lines connecting node centers, passing through segment cells
- Remove separate confluence rendering (now part of node rendering)

Debug command changes:
- `/rivertale region` should display node and segment counts
- Show node details: cell position, inDir, outDir
- Show segment details: start→end, cell count

**Validation:**
- Full test suite passes
- Debug visualization accurately reflects graph structure
- Carving/rendering code works with graph-based paths

---

## Testing Milestones

| After Phase | Testable Behavior |
|-------------|-------------------|
| 1 | Node, Segment, RiverGraph, GraphBuilder compile, unit tests pass |
| 2 | Graph built correctly for each region type (normal, divide, basin, shore) |
| 3 | Flow uses RiverGraph, path accessors work |
| 4 | LengthValidator uses graph, same results as before |
| 5 | TributaryValidator correctly identifies confluences via getNodeAt |
| 6 | Graph rebuild excludes invalid inputs, preserves valid paths |
| 7 | All consumers updated, full test suite passes |

### Phase 6 Bug Scenario Unit Test

This is the core test case that validates the fix works. Add to `RiverGraphTest.java`:

```java
@Test
@DisplayName("Excluding short tributary preserves shared downstream for valid tributaries")
void testExcludingPreservesSharedDownstream() {
    // Build the problem scenario from the Problem Statement:
    // NORTH: (0,6) -> (1,7) [output]       -- too short, will be invalidated
    // SOUTH: (7,3) -> ... -> (0,6) -> (1,7) -- shares downstream with NORTH
    // WEST:  (1,0) -> (1,1) -> (1,2) -> (1,3) -- merges with SOUTH at (1,3)

    GraphBuilder builder = new GraphBuilder();

    // Build SOUTH path first (longer, will "own" downstream in old model)
    Node southEntry = builder.getOrCreateNode(cell(7, 3), Direction.SOUTH, null);
    builder.markVisited(cell(7, 3));
    // ... intermediate cells ...
    Node confluence1 = builder.getOrCreateNode(cell(1, 3), null, null);
    builder.addSegment(List.of(cell(6, 3), cell(5, 3), cell(4, 3), cell(3, 3), cell(2, 3)),
                       southEntry, confluence1);
    // ... to confluence with NORTH ...
    Node confluence2 = builder.getOrCreateNode(cell(0, 6), null, null);
    builder.addSegment(List.of(cell(1, 4), cell(1, 5)), confluence1, confluence2);
    Node output = builder.getOrCreateNode(cell(1, 7), null, Direction.EAST);
    builder.addSegment(List.of(), confluence2, output);

    // Build NORTH path (short - just entry to output)
    builder.getOrCreateNode(cell(0, 6), Direction.NORTH, null); // Merges with confluence2

    // Build WEST path
    Node westEntry = builder.getOrCreateNode(cell(1, 0), Direction.WEST, null);
    builder.addSegment(List.of(cell(1, 1), cell(1, 2)), westEntry, confluence1);

    RiverGraph graph = builder.build();

    // Exclude NORTH (too short)
    RiverGraph result = graph.excluding(Set.of(Direction.NORTH));

    // SOUTH's path should still reach output
    List<CellPos> southPath = result.getPathFor(Direction.SOUTH);
    assertFalse(southPath.isEmpty(), "SOUTH path should not be empty");
    assertEquals(cell(1, 7), southPath.get(southPath.size() - 1),
                 "SOUTH should still reach output");

    // WEST's path should still reach output (via SOUTH's downstream)
    List<CellPos> westPath = result.getPathFor(Direction.WEST);
    assertFalse(westPath.isEmpty(), "WEST path should not be empty");
    assertEquals(cell(1, 7), westPath.get(westPath.size() - 1),
                 "WEST should still reach output");

    // NORTH should have no path
    List<CellPos> northPath = result.getPathFor(Direction.NORTH);
    assertTrue(northPath.isEmpty(), "NORTH path should be empty after exclusion");
}
```

This test directly validates the bug fix: when NORTH is excluded, SOUTH and WEST retain their paths through the shared downstream segment.

## Edge Cases

### Nested Confluences

A region might have multiple confluences in sequence:

```
NORTH -> confluence A -> confluence B -> output
SOUTH -> confluence A
WEST  -> confluence B
```

Each confluence is a Node. Segments connect adjacent nodes. The graph naturally represents this as a DAG.

### No Confluences

If all inputs go directly to output without merging, each input has its own path through separate nodes/segments. Interior nodes only exist at confluences, so without confluences there are just input nodes, output node, and segments between them.

### Divide Regions

Divide regions have outputs but no inputs - rivers originate here. Each output crossing creates a Node with:
- `cell`: the output crossing cell
- `inDir`: null (no input)
- `outDir`: the output direction

There are no segments within the region (the river starts at the output node). For validation, the path length is 1 (the node) + downstream length through the crossing.

### Basin Regions

Basins have inputs but no output. The terminal node has:
- `cell`: the terminus cell
- `inDir`: null (unless an input enters there)
- `outDir`: null (no output - this is a terminus)

### Shore Regions

Same as basins - the terminal node is at a water cell. Structure is the same.

### Secondary Outputs

Secondary outputs are Nodes with:
- `cell`: the secondary output crossing cell
- `inDir`: null
- `outDir`: the secondary output direction

They're not connected to the main input-to-primary-output graph. They exist as isolated nodes representing river sources.

## Future Work

**Explicitly deferred:**

- Caching cross-region flow computations (current lazy approach is fast enough)
- Storing graph in saved data (currently recomputed on demand)
- Graph-level optimizations (e.g., merging adjacent segments with single upstream/downstream)
