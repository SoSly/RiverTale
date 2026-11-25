package org.sosly.rivertale.river;

import com.mojang.brigadier.context.CommandContext;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.NetworkRegistry;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.cell.CellType;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.client.ClientRegionCache;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.CacheConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.command.VisCommand;
import org.sosly.rivertale.networking.Message;
import org.sosly.rivertale.networking.Network;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.DensityField;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.density.SampleProvider;
import org.sosly.rivertale.event.ServerLifecycleHandler;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.region.RegionTypeCache;
import org.sosly.rivertale.world.RiverShaping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class FlowlinePreviewTest {
    private CommonConfig originalConfig;
    private final CellPos source = new CellPos(1, 2);
    private RandomState randomState;

    @BeforeEach
    void setUp() {
        originalConfig = CommonConfig.get();
        CommonConfig.set(new CommonConfig(8, 1, 1, false, false, 16, 5, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT));
        RiverShaping.shutdown();
        RegionCache.shutdown();
        RegionTypeCache.shutdown();
        CellCache.shutdown();
        SampleCache.shutdown();
        SampleCache.init(new SlopingTerrain());
        CellCache.init();
        RegionTypeCache.init();
        RegionCache.init();
        // Crater classification draws from RiverShaping's random state; never let it pass the chance roll.
        randomState = mock(RandomState.class);
        PositionalRandomFactory factory = mock(PositionalRandomFactory.class);
        RandomSource neverCrater = mock(RandomSource.class);
        when(neverCrater.nextFloat()).thenReturn(1.0f);
        when(factory.at(anyInt(), anyInt(), anyInt())).thenReturn(neverCrater);
        when(randomState.getOrCreateRandomFactory(any())).thenReturn(factory);
        RiverShaping.init(63, randomState);
    }

    @AfterEach
    void tearDown() {
        RiverShaping.shutdown();
        RegionCache.shutdown();
        RegionTypeCache.shutdown();
        CellCache.shutdown();
        SampleCache.shutdown();
        CommonConfig.set(originalConfig);
    }

    @Test
    void networkBuildRecordsOnlyItsTotalWhenMetricsAreDisabled() {
        Store.clear();
        try {
            assertFalse(CommonConfig.get().enableMetrics());
            RegionPos pos = source.getRegion();
            assertTrue(RiverNetwork.build(Set.of(pos), () -> { }).containsKey(pos));

            String buildMetric = RiverNetwork.class.getName() + "_build";
            assertEquals(Set.of(buildMetric), Store.timers().keySet());
            assertEquals(1, Store.timers().get(buildMetric).count());
            assertTrue(Store.ratios().isEmpty());
            assertTrue(Store.distributions().isEmpty());
        } finally {
            Store.clear();
        }
    }

    @Test
    void tracesClassifiedSourcesWithoutBuildingWatershedsOrAssigningElevations() {
        Region region = RegionCache.get().getOrCompute(source.getRegion());
        List<Flowline> paths = preparedPreview(region);

        assertFalse(paths.isEmpty());
        Flowline path = paths.stream().filter(candidate -> candidate.cells().get(0).equals(source)).findFirst().orElseThrow();
        assertTrue(path.isValid());
        assertTrue(region.boundaries().stream().anyMatch(boundary -> boundary.land().equals(path.terminus())));
        assertEquals(Feature.SNOWMELT, CellCache.get().getOrCompute(source).feature());
        assertNull(RiverShaping.getReady(region.pos(), randomState));
    }

    @Test
    void tracingProducesTheSamePathsWithColdAndReversePrewarmedCaches() {
        Region region = RegionCache.get().getOrCompute(source.getRegion());
        CellCache.get().clear();
        SampleCache.get().clear();
        List<Flowline> cold = preparedPreview(region);
        assertFalse(cold.isEmpty());

        CellCache.get().clear();
        SampleCache.get().clear();
        List<CellPos> positions = new ArrayList<>(region.cells());
        Collections.reverse(positions);
        for (CellPos pos : positions) {
            CellCache.get().getOrComputeWithFlow(pos);
        }
        List<Flowline> warm = preparedPreview(region);

        assertEquals(cold, warm);
    }

    @Test
    void upstreamPreparationFindsTheSameDistantSourcesWithColdAndPrewarmedCaches() {
        Region region = RegionCache.get().getOrCompute(source.getRegion());
        FlowlinePreparation.Result cold = FlowlinePreparation.prepare(region);
        List<Watershed> coldWatersheds = WatershedBuilder.build(WatershedPreparation.gather(cold));
        CellPos distantSource = new CellPos(-9, 2);
        assertTrue(cold.upstream().containsKey(distantSource.getRegion()));
        assertTrue(cold.upstream().get(distantSource.getRegion()).stream()
            .anyMatch(path -> path.cells().get(0).equals(distantSource)
                && path.cells().stream().anyMatch(region.pos()::contains)));

        RegionCache.get().clear();
        RegionTypeCache.get().clear();
        CellCache.get().clear();
        SampleCache.get().clear();
        List<RegionPos> regions = new ArrayList<>(cold.upstream().keySet());
        Collections.reverse(regions);
        for (RegionPos pos : regions) {
            RegionCache.get().getOrCompute(pos);
        }
        FlowlinePreparation.Result warm = FlowlinePreparation.prepare(RegionCache.get().getOrCompute(source.getRegion()));

        assertEquals(cold.upstream(), warm.upstream());
        List<Watershed> warmWatersheds = WatershedBuilder.build(WatershedPreparation.gather(warm));
        assertEquals(topology(coldWatersheds), topology(warmWatersheds));
        assertNull(RiverShaping.getReady(region.pos(), randomState));
    }

    @Test
    void gatheringFromEitherEndProducesTheSameCompleteWatershed() {
        Region root = RegionCache.get().getOrCompute(source.getRegion());
        List<Watershed> coastal = WatershedBuilder.build(WatershedPreparation.gather(FlowlinePreparation.prepare(root)));
        CellPos distant = new CellPos(-9, 2);
        Watershed fromCoast = coastal.stream().filter(watershed -> watershed.contains(distant)).findFirst().orElseThrow();
        RegionCache.get().clear();
        RegionTypeCache.get().clear();
        CellCache.get().clear();
        SampleCache.get().clear();
        Region upstream = RegionCache.get().getOrCompute(distant.getRegion());
        List<Watershed> inland = WatershedBuilder.build(WatershedPreparation.gather(FlowlinePreparation.prepare(upstream)));
        Watershed fromInland = inland.stream().filter(watershed -> watershed.contains(distant)).findFirst().orElseThrow();

        assertEquals(topology(List.of(fromCoast)), topology(List.of(fromInland)));
    }

    @Test
    void regionWithoutBoundariesHasNoPreviewPaths() {
        Region region = RegionCache.get().getOrCompute(new RegionPos(-8, 0));

        assertTrue(preparedPreview(region).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void stationaryVisDoesNotBuildAndSendsNewlyReadyWatershedAfterTwentyTicks() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ServerPlayer player = mock(ServerPlayer.class, RETURNS_DEEP_STUBS);
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        when(player.blockPosition()).thenReturn(source.getRegion().getWorldPosition(100));
        when(player.serverLevel().getChunkSource().getGenerator().getSeaLevel()).thenReturn(63);
        when(player.serverLevel().getChunkSource().randomState()).thenReturn(randomState);
        MinecraftServer server = player.serverLevel().getServer();
        when(server.getPlayerList().getPlayer(player.getUUID())).thenReturn(player);
        when(server.getTickCount()).thenReturn(5);
        CommandSourceStack commandSource = mock(CommandSourceStack.class);
        when(commandSource.getPlayer()).thenReturn(player);
        CommandContext<CommandSourceStack> context = mock(CommandContext.class);
        when(context.getSource()).thenReturn(commandSource);
        List<Message> messages = new ArrayList<>();

        try (MockedStatic<NetworkRegistry> registry = mockStatic(NetworkRegistry.class);
             MockedStatic<Network> network = mockStatic(Network.class)) {
            network.when(() -> Network.sendToPlayer(any(Message.class), eq(player))).thenAnswer(invocation -> {
                messages.add(invocation.getArgument(0));
                return null;
            });
            assertEquals(1, VisCommand.register().build().getCommand().run(context));
            assertTrue(VisCommand.isEnabled(player));
            assertEquals(1, messages.size());
            assertNull(RiverShaping.getReady(source.getRegion(), randomState));

            shape(source);
            messages.clear();
            tick(server, 24);
            assertTrue(messages.isEmpty());
            tick(server, 25);
            assertEquals(1, messages.size());
            Map<CellPos, Set<CellPos>> sentLinks = new HashMap<>();
            Set<CellPos> sentOutlets = new HashSet<>();
            for (Message message : messages) {
                if (!(message instanceof Region.Packet packet)) {
                    continue;
                }
                FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    Region.Packet.encode(packet, buffer);
                    ClientRegionCache.Region region = ClientRegionCache.Region.decode(buffer.readNbt());
                    region.edges().forEach(edge -> sentLinks.computeIfAbsent(edge.from(), key -> new HashSet<>()).add(edge.to()));
                    region.outlets().forEach(outlet -> sentOutlets.add(outlet.pos()));
                    region.edges().stream().filter(edge -> edge.from().equals(source))
                        .forEach(edge -> assertEquals(250, edge.fromY()));
                    assertTrue(region.paths().stream().noneMatch(path -> path.points().get(0).pos().equals(source)));
                } finally {
                    buffer.release();
                }
            }
            assertTrue(sentLinks.containsKey(source), "The existing vis command must send the merged source branch");
            assertTrue(sentLinks.containsKey(new CellPos(-9, 2)), "The incoming source outside the visible ring must be sent");
            assertTrue(sentLinks.values().stream().allMatch(next -> next.size() == 1));
            Watershed watershed = RiverShaping.getReady(source.getRegion(), randomState).watershedAt(source);
            assertNotNull(watershed);
            assertTrue(sentOutlets.contains(watershed.terminus(source).orElseThrow()));

            RegionPos next = new RegionPos(1, 0);
            shape(next.getMinCell());
            when(player.blockPosition()).thenReturn(next.getWorldPosition(100));
            messages.clear();
            tick(server, 26);
            assertEquals(2, messages.size());
            assertTrue(messages.stream().allMatch(Region.Packet.class::isInstance));

            messages.clear();
            VisCommand.register().build().getCommand().run(context);
            assertFalse(VisCommand.isEnabled(player));
            assertEquals(1, messages.size());
            VisCommand.TogglePacket toggle = (VisCommand.TogglePacket) messages.get(0);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                VisCommand.TogglePacket.encode(toggle, buffer);
                assertEquals(0, buffer.readInt());
            } finally {
                buffer.release();
            }
        } finally {
            VisCommand.onPlayerLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        }
    }

    @Test
    void chunkGenerationBuildsCompleteWatershedsWithVisOff() {
        assertNull(RiverShaping.getReady(source.getRegion(), randomState));

        shape(source);

        RiverRegion ready = RiverShaping.getReady(source.getRegion(), randomState);
        assertNotNull(ready);
        CellPos remoteSource = new CellPos(-9, 2);
        assertTrue(ready.flowlines().stream().anyMatch(path -> path.cells().get(0).equals(remoteSource)));
        assertTrue(ready.watershedAt(source).contains(remoteSource));
        ClientRegionCache.Region preview = ClientRegionCache.Region.decode(ready.encode());
        assertTrue(preview.cells().stream().anyMatch(cell -> cell.feature().type == CellType.COURSE));
        for (ClientRegionCache.Cell cell : preview.cells()) {
            if (ready.watershedAt(cell.pos()) == null) {
                continue;
            }
            assertEquals(ready.cells().get(cell.pos()).feature(), cell.feature());
            assertEquals(CellCache.get().getIfPresent(cell.pos()).feature(), cell.feature());
        }
    }

    @Test
    void loginAndRegionEntryPrepareTheNeighborhoodWithVisOff() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ServerPlayer player = mock(ServerPlayer.class, RETURNS_DEEP_STUBS);
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        when(player.serverLevel().dimension()).thenReturn(Level.OVERWORLD);
        when(player.serverLevel().getChunkSource().randomState()).thenReturn(randomState);
        RegionPos first = source.getRegion();
        when(player.blockPosition()).thenReturn(first.getWorldPosition(100));
        ServerLifecycleHandler lifecycle = new ServerLifecycleHandler();
        assertFalse(VisCommand.isEnabled(player));

        lifecycle.onPlayerLogin(new PlayerEvent.PlayerLoggedInEvent(player));
        awaitNeighborhood(first);
        assertTrue(RiverShaping.getReady(first, randomState).watershedAt(source).contains(new CellPos(-9, 2)));

        RegionPos second = new RegionPos(1, 0);
        assertNull(RiverShaping.getReady(new RegionPos(2, 0), randomState));
        when(player.blockPosition()).thenReturn(second.getWorldPosition(100));
        lifecycle.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        awaitNeighborhood(second);
    }

    private void awaitNeighborhood(RegionPos center) {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            while (center.neighborhood().stream().anyMatch(pos -> RiverShaping.getReady(pos, randomState) == null)
                && !Thread.currentThread().isInterrupted()) {
                LockSupport.parkNanos(1_000_000);
            }
        });
    }

    private void shape(CellPos cell) {
        ChunkAccess chunk = mock(ChunkAccess.class);
        when(chunk.getPos()).thenReturn(new ChunkPos(cell.x(), cell.z()));
        RiverShaping.shape(chunk, randomState);
    }

    private void tick(MinecraftServer server, int tick) {
        when(server.getTickCount()).thenReturn(tick);
        VisCommand.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
    }

    private List<Flowline> preparedPreview(Region region) {
        PreparedRiverData data = RiverPreparation.prepare(Set.of(region.pos()));
        return SourceTrace.trace(region, new FlowlineTracer(data));
    }

    private Map<CellPos, CellPos> topology(List<Watershed> watersheds) {
        Map<CellPos, CellPos> links = new HashMap<>();
        for (Watershed watershed : watersheds) {
            for (CellPos cell : watershed.allCells()) {
                links.put(cell, watershed.downstream(cell));
            }
        }
        return links;
    }

    private static class SlopingTerrain implements SampleProvider {
        @Override
        public Sample sampleCore(ChunkPos pos) {
            boolean ocean = pos.x >= 7;
            int height = ocean ? 40 : 200 - 15 * pos.x - 3 * Math.abs(pos.z - 2);
            if (pos.x == 1 && pos.z == 2) {
                height = 250;
            }
            if (pos.x == -9 && pos.z == 2) {
                height = 600;
            }
            return new Sample(pos, ocean ? -0.5 : 0.5, height, ocean, 0.5, 0.1, 0.1, 0.1);
        }

        @Override
        public double sample(DensityField field, ChunkPos pos) {
            throw new AssertionError("All fields are supplied by the terrain sample");
        }
    }
}
