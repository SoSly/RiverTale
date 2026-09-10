# RiverTale

![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)
![Forge](https://img.shields.io/badge/Forge-47.1.100-blue.svg)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)
![Version](https://img.shields.io/badge/Version-0.1.0--alpha-red.svg)

**RiverTale** is developing rivers that flow from high elevations toward the ocean. The `poc/rtf` branch integrates RiverTale with ReTerraForged (RTF) terrain generation. RiverTale and RTF remain separate mods.

## Status

This branch is an early integration prototype. It supports RiverTale's existing cell feature classification, neighboring flow calculations, diagnostic commands and overlays, timing metrics, and manual carving and filling experiments over RTF terrain.

The existing `/rivertale vis` traces rivers from classified sources to known coastal or basin boundaries, then assembles their connected watersheds. Blue lines and arrows follow sampled base terrain heights, and a cross marks each endpoint. Each cell uses the destination list and permitted next-step regions belonging to its current region; sources without a successful route have no displayed path. The near-destination approach and optional nudging can cross rising terrain, so these lines are route previews, not final downhill water profiles.

Paths try downhill flow and, when enabled, nudging first at every distance. Within 5 cells of the nearest destination, they can use a direct fallback if those branches cannot reach a destination. Beyond 5 cells, there is no forced direct fallback. This preview threshold is fixed; the legacy `mergeThreshold` setting still belongs to the older path implementation. An inland region builds its local destination list from itself, its edge-adjacent fluvial regions, and their edge-adjacent coastal regions, with basin shores remaining valid destinations. Without a fluvial neighbor, it uses itself and its four edge-adjacent neighbors. Coastal and fluvial regions retain their existing border-flow rules for admitting neighbors. Every border crossing switches to the new region's destinations and movement permissions; the starting region no longer bounds the entire route.

Nudging is enabled by default, with `nudgingLimit = 16` allowing at most 16 blocks of uphill rise per nudged step between sampled average cell heights. Set `enableNudging = false` to disable it, or adjust `nudgingLimit` in `config/rivertale-common.toml` and restart. A zero limit allows flat or downhill nudges. The limit is per step, not a cumulative climb allowance. With nudging disabled, steps beyond the 5-cell fallback radius must descend in sampled cell height. The direct fallback within 5 cells still permits uphill steps without applying the nudging limit.

Each source uses an iterative depth-first search with no search budget. Failed branches are retried in preference order, including backtracking to the source when necessary. Duplicate next steps are removed. A step into a cell already on the active tentative path is a failed candidate and triggers backtracking through the usual alternatives. Removing a cell from the active path removes that loop restriction; revisiting a region at different cells is allowed. When a failed branch encountered an active-path loop, a reachability check searches its possible continuations without the active-path restriction, while retaining regional permissions and the away-step limit. If even that search cannot find an outlet, the visited states are proven dead ends and can be cached. Otherwise, the failure remains dependent on the active path and is not cached. This prevents repeated exploration of closed cyclic branches without imposing a search budget. Other exhausted cells remain cached within that source's search together with the least number of consecutive away steps for which failure was established. A later arrival with more allowance remaining is searched again. Downhill steps may move farther from the current region's nearest destination, or stay equally far away, for up to 10 consecutive steps. Any step that gets closer resets this counter; backtracking restores the earlier counter, and crossing a region border does not reset it. Distance comparisons use the destination selected before each step. Nudges and direct fallback must still get closer, and every step obeys the current region's movement permissions. This allowance is fixed and limits consecutive non-approaching steps, not total detour length or total search work. Failed previews retain the first failed branch for diagnostics and are not displayed.

Before sending a preview, `/rivertale vis` prepares upstream contributors rooted in the player's current region. It walks backward through the tracer's legal cell moves, including downhill, nudged, and direct candidates, to discover potential source regions. It traces those regions' sources and includes the regions traversed before successful paths enter the target. It repeats for newly included upstream regions until no contributors remain. A region is accepted when a selected successful path enters the set; a possible alternative alone does not make it a contributor. Downstream-only regions do not become expansion roots in this stage.

Reverse discovery can follow arbitrarily long chains, including regions with no sources of their own. It no longer relies on a two-region source-search radius. Reverse discovery enforces the same 10-step allowance by tracking the leading run of non-approaching steps on each possible route to a target. It re-examines a cell only when a route with a shorter leading run is found, so cycles do not loop discovery and improved routes can admit additional upstream cells. There is no river-length, search, or region-count budget; world bounds still apply. Regional destination lists, candidate cell moves, and cell feature classifications are cached in the request's shared tracer, while active paths and failed-branch state belong to each individual source search. Region borders and cell overlays still cover the existing nine-region view. Completed watershed graphs, including distant tributaries, are included in the current region's packet. Nearby paths outside those watersheds remain individual previews. The renderer draws each merged downstream edge and outlet marker once across cached packets.

Watershed preparation follows the prepared rivers along their full lengths and gathers every successful traced path that shares a cell or crosses a diagonal with them. It repeats for newly connected tributaries, including branches joining downstream of the player's region. It uses the same reverse cell search to find potential source regions without loading Minecraft chunks, and it does not expand through unrelated rivers simply because they occupy the same region.

Watershed building assembles upstream links by walking from each terminus toward the sources. Shared cells have one downstream link. At diagonal crossings or conflicting continuations, the route supported by more unique upstream cells in the original flowlines takes precedence; coordinate ordering breaks ties. Crossings become confluences, and abandoned downstream tails disappear unless another source still feeds them. If combining individually valid paths creates a cycle, the builder selects an existing candidate connection to an outlet-connected branch. Every successful original source remains connected to a terminus. The graph and its upstream river-cell counts are independent of input ordering.

This stage stores the completed graphs in memory for cell lookup and visualization. It does not prune short tributaries, assign elevations, reclassify river features, carve terrain, or place water. The older `Watershed(RegionPos, List<Path>)` constructor and pending-path finalization remain legacy code and are not used by this pipeline. Destination choices and potential next steps now depend on the current location, independently of the river's source region. Which candidate succeeds can still depend on the remaining away-step allowance and on avoiding cells already in that particular tentative path.

With metrics enabled, `/rivertale metrics` reports:

- `WatershedPreparation_gather` measures additional connected-tributary discovery. Its `examinedRegions`, `upstreamCells`, and `paths` distributions report regions and cells reached by the reverse search and collected successful paths per request.
- `WatershedBuilder_build` measures graph construction. Its `watersheds`, `crossings`, `conflictingCells`, and `cycleRepairs` distributions report counts per build; `riverCells`, `sources`, and `confluences` report counts per completed watershed. Sources here are graph headwaters, and accumulation counts upstream river cells, not all terrain in the drainage area.
- `FlowlinePreparation_prepare` measures the complete upstream preparation, including region discovery and source tracing.
- `FlowlinePreparation_upstreamRegions`, `FlowlinePreparation_examinedRegions`, and `FlowlinePreparation_tracedRegions` report region counts per preparation. The first includes the current region; `examinedRegions` counts regions reached by reverse cell discovery, and `tracedRegions` counts candidate origins whose sources were traced. `FlowlinePreparation_upstreamCells` counts reverse-discovered cells. Candidate regions can be examined or traced without ultimately contributing. Existing tracer metrics include those candidate traces as well.

- `FlowlineTracer_trace` measures the complete search for each source.
- `FlowlineTracer_regionalRouting` measures construction of a region's destination list and movement permissions, including any region discovery it requires. Calls count newly built regional lists; cached lists are reused across sources in the request.
- `FlowlineTracer_awaySteps` counts steps that moved farther from, or stayed equally far from, the selected destination on each successful path. `FlowlineTracer_maxConsecutiveAwaySteps` reports the longest such run on each successful path, up to 10. Failed branches are excluded, and paths without detours contribute zero.
- `FlowlineTracer_awayLimitRejections` counts candidate steps rejected for exceeding the consecutive-step allowance per searched source. Existing trace and backtracking timers include this work.
- `FlowlineTracer_loopRejections` reports rejected attempts to revisit an active cell per searched source, including zero for searches without loops.
- `FlowlineTracer_closedBranchSearch` measures reachability checks for failed cyclic branches. `FlowlineTracer_closedBranchChecks` reports proven closed branches over all such checks. A check that finds a possible outlet leaves normal path-dependent backtracking in control. This time is included in the trace and backtracking totals.
- `FlowlineTracer_contextualFailures` reports exhausted search frames whose loop-dependent failure could not be independently proven and was consequently left out of the dead-end cache, per searched source.
- `FlowlineTracer_lengthBlocks` reports count, average, minimum, maximum, and median horizontal length along successful paths, in blocks. Diagonal steps count their actual horizontal distance. Failed branches are excluded; repeated successful traces are separate samples.
- `FlowlineTracer_netFallBlocks` reports sampled source elevation minus the configured sea level for each successful path. This is the available fall to the intended outlet, independent of the sampled ground height at the land-side endpoint.
- `FlowlineTracer_cellFallBlocks` reports fall at each boundary on successful paths, with every uphill or flat boundary contributing zero. The final profile point is placed at the configured sea level; other points use sampled terrain heights.
- `FlowlineTracer_carveFlatFallBlocks` and `FlowlineTracer_carveDescendingFallBlocks` compare hypothetical carve-only profiles. Each downstream interior point becomes the lower of its sampled height and the preceding corrected height minus 0 or 1 block respectively. The source stays fixed and the final point stays at sea level. These metrics report individual boundary falls, including zero, rather than averages of river averages.
- `FlowlineTracer_carveFlatFeasible` and `FlowlineTracer_carveDescendingFeasible` report how many successful paths can meet those carve-only rules while retaining the fixed outlet. Infeasible profiles contribute no boundary-fall samples; they do not invalidate or alter the traced river. Paths with no boundaries contribute no profile samples. Compare feasibility alongside fall statistics because the populations can differ. The profiles are diagnostics only and do not carve terrain or change visualization.
- `FlowlineTracer_nudgingRiseBlocks` reports cumulative uphill rise from nudges per successful river, summarized as count, average, minimum, maximum, and median in blocks. Each positive rise between sampled average cell heights is added; later descents do not cancel it. Rivers without uphill nudges contribute zero.
- `FlowlineTracer_uphillNudgeBlocks` reports the same statistics for individual uphill nudged steps on successful paths. Flat and downhill nudges are excluded. Both rise metrics exclude direct approaches, failed branches, and failed rivers; repeated successful traces contribute new samples. If a step qualifies as both a nudge and a direct fallback, its earlier nudge classification is retained.
- `FlowlineTracer_success` reports completed paths over all source trace attempts.
- `FlowlineTracer_backtracking` measures time from the first rejected branch through completion, for searches that actually try an alternative. It is included in the overall trace time.
- `FlowlineTracer_retrySuccess` reports rescued paths over searches that tried at least one alternative. Sources that succeed immediately or have no alternatives are excluded.
- `FlowlineTracer_deadEndHits` reports known failed-cell hits over candidate lookups. A hit skips a divide-blocked cell or a cell already exhausted with at least as much allowance remaining; a miss proceeds to evaluate it. This cache is local to each source search.
Path previews run on demand when visualization requests a region and are measured by `FlowlinePreview.trace` and `FlowlineTracer.trace`. No Minecraft chunks are requested by tracing. Watershed construction runs on visualization requests; it is not yet connected to normal chunk generation. Channel shaping and water placement remain disconnected. Those unfinished stages and placeholder feature classifiers remain unfinished; the manual prototypes do not establish automatic river generation or custom currents.

## Requirements

- Minecraft 1.20.1 and Java 17 are required.
- Development uses Forge 47.1.100; RiverTale declares compatibility with Forge versions from 47.1 up to, but excluding, 48.
- ReTerraForged `0.0.6-sosly-1.0.0` for Forge 1.20.1 is required on both the client and server. This integration pins that version because it calls RTF's terrain APIs directly.

## Installation and world setup

1. Build RiverTale with `./gradlew build` (`.\gradlew.bat build` on Windows).
2. Place the resulting RiverTale JAR and `libs/reterraforged-0.0.6-sosly-1.0.0-forge-1.20.1.jar` in the instance's `mods` folder. RTF is a separate dependency and is not bundled inside RiverTale's JAR.
3. Remove Lithosphere from that instance's terrain-generation setup.
4. Create a **fresh ReTerraForged world**. Keep the default **MULTI_IMPROVED** continent generator. In the RTF preset's Rivers settings, set **River Count to 0** before generating the world. The experimental Fancy continent generator in this RTF version ignores the river count setting, so it is not suitable for this setup.
5. Use RiverTale's diagnostic commands and manual prototypes to inspect the integration. `/rivertale metrics` reports the measured generation costs.

RiverTale relies on the RTF preset setting to disable RTF's rivers. It does not patch or suppress RTF's river generation. Existing worlds retain their original world-generation settings and are not migrated by installing this branch.

## Terrain classification

RiverTale samples RTF's native base terrain heights before RTF applies tile erosion and smoothing. The planning heights can therefore differ from the final surface. Ocean membership also comes from RTF, and inland cells can be classified without searching neighboring regions for a coast.

Normal chunk generation classifies the containing planning cell and samples nearby cells as needed to determine flow. Full region classification and boundary detection are performed when diagnostic commands request them. Their costs therefore appear when inspecting a region rather than on every generated chunk.

Spring classification now requires a height above Y=113. This preserves the approximate elevation of the previous depth threshold: 70 + 144 × 0.3 = 113.2. The other feature thresholds still need visual calibration against RTF terrain and climate values.

## Development

The pinned RTF JAR is included under `libs/`. Gradle deobfuscates this production Forge artifact for both compilation and the development runtime, so `./gradlew runClient` also loads RTF without a separate copy in `run/client/mods`. Lithosphere is no longer a development dependency.

See [the RTF artifact record](libs/README.md) for its provenance, SHA-256 checksum, and license. The integration does not modify RTF's source or JAR.

See [ROADMAP.md](ROADMAP.md) for planned features and [CHANGELOG.md](CHANGELOG.md) for version history.

## License

RiverTale is licensed under the MIT License. See [LICENSE](LICENSE) for details. The separate RTF artifact is also distributed under its [MIT license](libs/reterraforged-LICENSE.txt).
