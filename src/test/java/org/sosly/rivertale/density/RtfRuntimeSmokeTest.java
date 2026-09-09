package org.sosly.rivertale.density;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sosly.rivertale.RiverTale;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.world.RiverShaping;
import raccoonman.reterraforged.data.worldgen.preset.settings.ContinentType;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import raccoonman.reterraforged.world.worldgen.RTFRandomState;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.heightmap.Heightmap;

@Mod.EventBusSubscriber(modid = RiverTale.MOD_ID)
public class RtfRuntimeSmokeTest {
    private static final Path RESULT = Path.of("rtf-smoke-result.txt");

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean("rivertale.rtfSmoke")) {
            return;
        }

        MinecraftServer server = event.getServer();
        List<String> checks = new ArrayList<>();
        long start = System.nanoTime();
        try {
            verifyInactiveDimension(server.getLevel(Level.NETHER), "Nether", checks);
            verifyInactiveDimension(server.getLevel(Level.END), "End", checks);
            runChecks(server.overworld(), checks);
            checks.add("Elapsed milliseconds: " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            Files.writeString(RESULT, "PASS: RTF runtime integration smoke\n" + String.join("\n", checks) + "\n");
            RiverTale.LOGGER.info("[RTF_SMOKE] PASS: {}", String.join("; ", checks));
        } catch (Throwable failure) {
            RiverTale.LOGGER.error("[RTF_SMOKE] FAIL", failure);
            try {
                Files.writeString(RESULT, "FAIL: " + failure + "\n" + String.join("\n", checks) + "\n");
            } catch (Exception reportFailure) {
                RiverTale.LOGGER.error("Could not write RTF smoke result", reportFailure);
            }
        } finally {
            server.halt(false);
        }
    }

    private static void runChecks(ServerLevel level, List<String> checks) throws Exception {
        RandomState randomState = level.getChunkSource().randomState();
        require((Object) randomState instanceof RTFRandomState, "The world RandomState does not expose RTF");
        GeneratorContext context = ((RTFRandomState) (Object) randomState).generatorContext();
        require(context != null, "The world does not have an initialized RTF context");
        require(context.preset.rivers().riverCount == 0, "The RTF preset has native rivers enabled");
        require(context.preset.world().continent.continentType == ContinentType.MULTI_IMPROVED, "The smoke preset must use MULTI_IMPROVED");
        require(SampleCache.isInitialized(), "Rivertale did not initialize its sample cache");
        require(RiverShaping.isActive(randomState), "Rivertale is not attached to the actual world RandomState");
        checks.add("The actual world RTF context is active with native rivers set to zero");

        RtfSampleProvider provider = new RtfSampleProvider(context);
        List<ChunkPos> positions = samplePositions();
        int loadedBefore = level.getChunkSource().getLoadedChunksCount();
        List<Sample> serial = new ArrayList<>();
        for (ChunkPos pos : positions) {
            require(level.getChunkSource().getChunkNow(pos.x, pos.z) == null, "A distant probe chunk was already loaded: " + pos);
            Sample sample = provider.sampleCore(pos);
            verifyNativeHeight(context, sample);
            serial.add(sample);
        }
        verifyConcurrentSamples(provider, positions, serial);
        int loadedAfter = level.getChunkSource().getLoadedChunksCount();
        require(loadedAfter == loadedBefore, "Point sampling loaded Minecraft chunks: " + loadedBefore + " -> " + loadedAfter);
        checks.add("Serial and concurrent samples match at " + positions.size() + " distant positive and negative coordinates");
        checks.add("Point queries kept the loaded chunk count at " + loadedBefore);

        Sample beforeGeneration = serial.stream().filter(sample -> !sample.isOcean()).findFirst().orElseThrow(() -> new AssertionError("The fixed probe positions did not include land"));
        verifyChunkGeneration(level, provider, beforeGeneration, checks);
        verifyRegionDiagnostics(level, checks);
    }

    private static void verifyNativeHeight(GeneratorContext context, Sample sample) {
        require(sample.hasFullDensities(), "A sample is missing terrain or climate inputs");
        Cell nativeCell = new Cell();
        Heightmap heightmap = context.generator.getHeightmap();
        heightmap.applyTerrain(nativeCell, sample.pos().getMiddleBlockX(), sample.pos().getMiddleBlockZ());
        heightmap.applyClimate(nativeCell, sample.pos().getMiddleBlockX(), sample.pos().getMiddleBlockZ(), true);
        require(Float.isFinite(nativeCell.height), "RTF returned a non-finite height");
        require(sample.terrainHeight() == context.levels.scale(nativeCell.height), "The adapter height differs from RTF's native height");
        require(Double.isFinite(sample.temperature()) && Double.isFinite(sample.vegetation()), "RTF returned non-finite climate inputs");
    }

    private static void verifyConcurrentSamples(RtfSampleProvider provider, List<ChunkPos> positions, List<Sample> expected) throws Exception {
        List<CompletableFuture<Sample>> futures = new ArrayList<>();
        for (ChunkPos pos : positions) {
            futures.add(CompletableFuture.supplyAsync(() -> provider.sampleCore(pos)));
        }
        for (int i = 0; i < futures.size(); i++) {
            Sample concurrent = futures.get(i).get(30, TimeUnit.SECONDS);
            require(expected.get(i).equals(concurrent), "Concurrent sampling changed the result at " + positions.get(i));
        }
    }

    private static void verifyChunkGeneration(ServerLevel level, RtfSampleProvider provider, Sample before, List<String> checks) {
        ChunkPos pos = before.pos();
        CellPos cellPos = new CellPos(pos.getMiddleBlockPosition(0));
        require(CellCache.get().getIfPresent(cellPos) == null, "The target planning cell existed before explicit chunk generation");
        LevelChunk chunk = level.getChunk(pos.x, pos.z);
        require(chunk.getPos().equals(pos), "The generated chunk has the wrong coordinates");
        require(CellCache.get().getIfPresent(cellPos) != null, "Chunk generation did not populate Rivertale's classifier cache");
        require(before.equals(provider.sampleCore(pos)), "The point sample changed after RTF tile generation");
        checks.add("The engine generated distant chunk " + pos + " and populated Rivertale classification");
        checks.add("The point sample remained identical after full chunk generation");
    }

    private static void verifyInactiveDimension(ServerLevel level, String name, List<String> checks) {
        require(level != null, name + " did not initialize");
        require(!RiverShaping.isActive(level.getChunkSource().randomState()), "Rivertale incorrectly activated in the " + name);
        checks.add("Rivertale remains inactive in the " + name);
    }

    private static void verifyRegionDiagnostics(ServerLevel level, List<String> checks) {
        int loadedBefore = level.getChunkSource().getLoadedChunksCount();
        CompoundTag encoded = RegionCache.get().getOrCompute(new RegionPos(-4096, 4096)).encode();
        int expectedCells = CommonConfig.get().cellsPerRegion() * CommonConfig.get().cellsPerRegion();
        require(encoded.getList("cells", Tag.TAG_COMPOUND).size() == expectedCells, "Region diagnostics did not encode every classified cell");
        require(encoded.getList("cells", Tag.TAG_COMPOUND).getCompound(0).contains("y", Tag.TAG_INT), "Region diagnostics omitted terrain heights");
        require(!encoded.getList("cells", Tag.TAG_COMPOUND).getCompound(0).getString("feature").isEmpty(), "Region diagnostics omitted feature classification");
        require(level.getChunkSource().getLoadedChunksCount() == loadedBefore, "Region diagnostics loaded Minecraft chunks");
        checks.add("On-demand region diagnostics encoded " + expectedCells + " cells without loading Minecraft chunks");
    }
    private static List<ChunkPos> samplePositions() {
        List<ChunkPos> positions = new ArrayList<>();
        for (int distance : new int[]{384, 768, 1536, 3072}) {
            positions.add(new ChunkPos(distance, distance));
            positions.add(new ChunkPos(-distance, distance));
            positions.add(new ChunkPos(distance, -distance));
            positions.add(new ChunkPos(-distance, -distance));
        }
        return positions;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
