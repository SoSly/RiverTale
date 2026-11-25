# RiverTale

![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)
![Forge](https://img.shields.io/badge/Forge-47.1.100-blue.svg)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)
![Version](https://img.shields.io/badge/Version-0.1.0--alpha-red.svg)

**RiverTale** is working toward rivers that flow from high ground to the ocean. This branch is an early prototype on [ReTerraForged](libs/README.md) terrain. RiverTale and ReTerraForged stay separate mods.

## Status

**This branch is an early alpha prototype.** It does not generate rivers during world creation. Expect bugs, incomplete features, and breaking changes.

`/rivertale vis` draws route previews from classified sources toward coasts and basins. Those lines can cross rising ground. They are not finished downhill rivers, and they are not written into the terrain. Channel carving and water placement are still manual experiments.

Planning heights come from ReTerraForged's base terrain, before erosion and smoothing, so they can disagree with the surface you see.

## What works

- Cell classification and downhill flow over ReTerraForged terrain
- Route previews and watershed overlays (`/rivertale vis`)
- Timing metrics (`/rivertale metrics`)
- Manual carving and filling experiments

Tuning lives in `config/rivertale-common.toml`. With `disableChunkSaving` enabled at startup, operators can edit the file and run `/rivertale rebuild` to apply river tuning, including `samplesPerCell`. Changes to `cellSize`, `regionSize`, cache capacities, and `disableChunkSaving` still require a restart.

## Requirements

- Minecraft 1.20.1
- Forge 47.1 or newer, below 48. Development uses Forge 47.1.100.
- Java 17
- ReTerraForged `0.0.6-sosly-1.0.0` for Forge 1.20.1, on both client and server

## Setup

1. Build with `./gradlew build` (`.\gradlew.bat build` on Windows).
2. Put the RiverTale JAR and `libs/reterraforged-0.0.6-sosly-1.0.0-forge-1.20.1.jar` in the instance's `mods` folder. ReTerraForged is not bundled inside RiverTale.
3. Remove Lithosphere from that instance.
4. Create a fresh ReTerraForged world. Keep the default MULTI_IMPROVED continent generator, and set River Count to 0 before generating. The Fancy generator in this ReTerraForged build ignores that setting.
5. Use the diagnostic commands and manual prototypes to inspect the result.

`./gradlew runClient` loads the pinned ReTerraForged JAR itself. You do not need a second copy in `run/client/mods`.

This branch does not suppress ReTerraForged rivers in code. It relies on the preset. An existing world keeps its original settings.

## Development

The pinned ReTerraForged JAR, its checksum, and its license are recorded in [libs/README.md](libs/README.md). See [CHANGELOG.md](CHANGELOG.md) for version history.

## License

RiverTale is licensed under the MIT License. See [LICENSE](LICENSE) for details. The ReTerraForged JAR is covered by its own [MIT license](libs/reterraforged-LICENSE.txt).
