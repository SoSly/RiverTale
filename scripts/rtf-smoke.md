# RTF runtime smoke check

This check starts a fresh local Minecraft server with Rivertale and the separate RTF mod, verifies their integration inside the actual engine, and stops the server automatically. Each run receives its own directory under `run/rtfSmoke`. The script does not reuse the client world or change the original RTF preset.

The development build must have the RTF dependency described in the project README. You also need a complete RTF preset exported as a datapack ZIP. A preset settings JSON alone is not sufficient because the server also needs the generated noise and biome registries. You can obtain the ZIP using RTF's preset editor's **Export as Datapack** command.

To prepare and inspect the configuration without starting a server or accepting the Minecraft EULA, run:

```powershell
.\scripts\rtf-smoke.ps1 -PresetPack 'C:\path\to\reterraforged-preset.zip' -PrepareOnly
```

The script copies the supplied pack, selects `MULTI_IMPROVED` continents, and sets the copied preset's river count to zero. If `-PresetPack` is omitted, it looks for the existing export at `..\reterraforged\forge\run\saves\Test\datapacks\reterraforged-preset.zip` relative to the repository.

Running the server requires accepting the [Minecraft EULA](https://aka.ms/MinecraftEULA). Pass `-AcceptEula` only after you have accepted it. This explicit switch is the only condition under which the script writes `eula=true`, and it writes that file only inside the new isolated run directory.

```powershell
.\scripts\rtf-smoke.ps1 -PresetPack 'C:\path\to\reterraforged-preset.zip' -AcceptEula
```

Add `-Offline` when all Gradle dependencies are already cached. The server binds to the loopback address on an ephemeral port. It uses sixteen distant sample positions, compares serial and concurrent results, generates a distant chunk through the normal engine, and checks region diagnostics and the Nether/End guards.

The script prints the run directory, log path, and result path. Success requires both a zero Gradle exit code and a result file beginning with `PASS:`. Failure details appear in `rtf-smoke-result.txt` when the smoke hook runs; failures before that point appear in `logs/latest.log`. Startup alone does not count as a passing check. The test does not establish visual quality or validate manual carving/filling in the client.
