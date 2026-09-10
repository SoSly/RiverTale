# RiverTale

![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)
![Forge](https://img.shields.io/badge/Forge-47.1.100-blue.svg)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)
![Version](https://img.shields.io/badge/Version-0.1.0--alpha-red.svg)

**RiverTale** is developing rivers that flow from high elevations toward the ocean. The `poc/rtf` branch integrates RiverTale with ReTerraForged (RTF) terrain generation. RiverTale and RTF remain separate mods.

## Status

This branch is an early integration prototype. It supports RiverTale's existing cell feature classification, neighboring flow calculations, diagnostic commands and overlays, timing metrics, and manual carving and filling experiments over RTF terrain.

The existing `/rivertale vis` also traces and displays independent river paths from classified sources to known coastal or basin boundaries. Blue lines and arrows follow sampled base terrain heights, and a cross marks each endpoint. Tracing uses the current region discovery working set; sources without a reachable destination in that set have no displayed path. The near-destination approach and optional nudging can cross rising terrain, so these lines are route previews, not final downhill water profiles.

Paths go directly toward the nearest destination within 5 cells. From more than 5 through 10 cells, they try downhill flow and, when enabled, nudging first, then use a direct fallback if those branches cannot reach a destination. Beyond 10 cells, there is no forced direct fallback. These preview thresholds are fixed; the legacy `mergeThreshold` setting still belongs to the older path implementation. Inland regions can trace through edge-adjacent fluvial regions into their edge-adjacent coastal regions, with nearer basin shores remaining valid destinations. Without a fluvial neighbor, inland paths stay within their own region and its four edge-adjacent neighbors.

Nudging is enabled by default, with `nudgingLimit = 16` allowing at most 16 blocks of uphill rise per nudged step between sampled average cell heights. Set `enableNudging = false` to disable it, or adjust `nudgingLimit` in `config/rivertale-common.toml` and restart. A zero limit allows flat or downhill nudges. The limit is per step, not a cumulative climb allowance. With nudging disabled, steps beyond the 10-cell fallback radius must descend in sampled cell height. The immediate direct approach within 5 cells and fallback within 10 cells still permit uphill steps.

Each source uses an iterative depth-first search with no search budget. Failed branches are retried in preference order, including backtracking to the source when necessary. Duplicate next steps are removed, and exhausted cells are remembered within that source's search. Every step must still move closer to the nearest destination and stay inside the permitted regions. Within 5 cells, only the direct step is considered. Failed previews retain the first failed branch for diagnostics and are not displayed.

Before sending a preview, `/rivertale vis` prepares upstream contributors rooted in the player's current region. It checks every source region whose existing routing territory can include that region, traces its sources, and adds the regions traversed before those paths enter the target. It repeats this for newly included upstream regions until no contributors remain. Incoming paths may use the existing nudging and backtracking rules. A candidate is included when its selected successful path enters the set; permission to enter alone does not make it a contributor. Downstream-only routing regions and their unrelated tributaries do not become expansion roots.

The candidate search covers up to two cardinal region steps from each target, matching the longest existing inland-to-fluvial-to-coastal routing corridor. This is a bound on where an individual source's permitted territory can reach, not a limit on the recursive upstream set. Preparation has no region-count budget, traces each candidate source region at most once per request, and preserves each source region's original routing rules and destinations. Region borders and cell overlays still cover the existing nine-region view; paths from more distant contributors are included in the current region's packet. The renderer displays each source's path once even if it is present in multiple cached packets.

With metrics enabled, `/rivertale metrics` reports:

- `FlowlinePreparation_prepare` measures the complete upstream preparation, including region discovery and source tracing.
- `FlowlinePreparation_upstreamRegions`, `FlowlinePreparation_examinedRegions`, and `FlowlinePreparation_tracedRegions` report region counts per preparation. The first includes the current region; the latter counts include candidates that were checked but did not contribute. Existing tracer metrics include those candidate traces as well.

- `FlowlineTracer_trace` measures the complete search for each source.
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
- `FlowlineTracer_deadEndHits` reports known failed-cell hits over candidate lookups. A hit skips a previously exhausted or divide-blocked cell; a miss proceeds to evaluate it. This cache is local to each source search.
Path previews run on demand when visualization requests a region and are measured by `FlowlinePreview.trace` and `FlowlineTracer.trace`. No Minecraft chunks are requested by tracing. Automatic watershed construction, channel shaping, and water placement are not connected to world generation on this branch. Those unfinished stages and placeholder feature classifiers remain unfinished; the manual prototypes do not establish automatic river generation or custom currents.

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
