# ReTerraForged dependency

RiverTale's `poc/rtf` branch uses this existing production Forge mod as a separate required dependency:

| Property | Value |
| --- | --- |
| Artifact | `reterraforged-0.0.6-sosly-1.0.0-forge-1.20.1.jar` |
| Mod ID | `reterraforged` |
| Mod version | `0.0.6-sosly-1.0.0` |
| Minecraft | `1.20.1` |
| Size | `1,211,409` bytes |
| SHA-256 | `7f80a8a5ac741c29a402609226aa3646da37028851593f9eba7f1448ffa97191` |
| License | [MIT](reterraforged-LICENSE.txt) |

The artifact was copied unchanged from the existing local build at `C:\Users\Kevin\IdeaProjects\reterraforged\forge\build\libs\reterraforged-0.0.6-sosly-1.0.0-forge-1.20.1.jar`. The source checkout is [nivthefox/reterraforged](https://github.com/nivthefox/reterraforged), a fork of [racoonman2/ReTerraForged](https://github.com/racoonman2/ReTerraForged), and was clean at commit [`d7038bd11c6ae2067f5ea6a304d66526f5804b8a`](https://github.com/nivthefox/reterraforged/commit/d7038bd11c6ae2067f5ea6a304d66526f5804b8a) when this dependency was copied. The existing build was not rebuilt as part of this integration; the checksum above identifies the exact supplied binary.

Gradle resolves the JAR through the local `libs` repository and applies `fg.deobf` so the production artifact can be used in the mapped development environment. RTF remains its own mod at runtime and must also be installed separately alongside release builds of RiverTale.

Create a fresh RTF world using the default **MULTI_IMPROVED** continent generator with **Rivers / River Count = 0**. The experimental Fancy generator in this RTF version ignores the river count setting. RiverTale does not modify this JAR or suppress RTF's rivers programmatically.
