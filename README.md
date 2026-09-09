# RiverTale

![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)
![Forge](https://img.shields.io/badge/Forge-47.1.100-blue.svg)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)
![Version](https://img.shields.io/badge/Version-0.1.0--alpha-red.svg)

**RiverTale** is developing rivers that flow from high elevations toward the ocean. The `poc/rtf` branch integrates RiverTale with ReTerraForged (RTF) terrain generation. RiverTale and RTF remain separate mods.

## Status

This branch is an early integration prototype. It supports RiverTale's existing cell feature classification, neighboring flow calculations, diagnostic commands and overlays, timing metrics, and manual carving and filling experiments over RTF terrain.

The existing `/rivertale vis` also traces and displays independent river paths from classified sources to known coastal or basin boundaries. Blue lines and arrows follow sampled base terrain heights, and a cross marks each endpoint. Tracing uses the current region discovery working set; sources without a reachable destination in that set have no displayed path. The near-destination approach and nudging can cross rising terrain, so these lines are route previews, not final downhill water profiles.

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
