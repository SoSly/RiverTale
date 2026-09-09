package org.sosly.rivertale.river;

import com.mojang.brigadier.context.CommandContext;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.NetworkRegistry;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.client.ClientRegionCache;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.command.VisCommand;
import org.sosly.rivertale.networking.Message;
import org.sosly.rivertale.networking.Network;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.DensityField;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.density.SampleProvider;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.region.RegionTypeCache;
import org.sosly.rivertale.world.RiverShaping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class FlowlinePreviewTest {
    private CommonConfig originalConfig;
    private final CellPos source = new CellPos(1, 2);

    @BeforeEach
    void setUp() {
        originalConfig = CommonConfig.get();
        CommonConfig.set(new CommonConfig(8, 1, 1, 0, 1, 3, 288, false, false, 16));
        RiverShaping.shutdown();
        WatershedCache.shutdown();
        RegionCache.shutdown();
        RegionTypeCache.shutdown();
        CellCache.shutdown();
        SampleCache.shutdown();
        SampleCache.init(new SlopingTerrain());
        CellCache.init();
        RegionTypeCache.init();
        RegionCache.init();
    }

    @AfterEach
    void tearDown() {
        RegionCache.shutdown();
        RegionTypeCache.shutdown();
        CellCache.shutdown();
        SampleCache.shutdown();
        CommonConfig.set(originalConfig);
    }

    @Test
    void tracesClassifiedSourcesWithoutBuildingWatershedsOrAssigningElevations() {
        Region region = RegionCache.get().getOrCompute(source.getRegion());
        List<Flowline> paths = FlowlinePreview.trace(region);

        assertFalse(paths.isEmpty());
        Flowline path = paths.stream().filter(candidate -> candidate.cells().get(0).equals(source)).findFirst().orElseThrow();
        assertTrue(path.isValid());
        assertTrue(region.boundaries().stream().anyMatch(boundary -> boundary.land().equals(path.terminus())));
        assertEquals(Feature.SNOWMELT, CellCache.get().getOrCompute(source).feature());
        assertNull(WatershedCache.get());
        for (CellPos pos : path.cells()) {
            Cell cell = CellCache.get().getOrCompute(pos);
            assertNull(cell.terminus());
            assertNull(cell.entryY());
            assertNull(cell.exitY());
        }
    }

    @Test
    void tracingProducesTheSamePathsWithColdAndReversePrewarmedCaches() {
        Region region = RegionCache.get().getOrCompute(source.getRegion());
        CellCache.get().clear();
        SampleCache.get().clear();
        List<Flowline> cold = FlowlinePreview.trace(region);
        assertFalse(cold.isEmpty());

        CellCache.get().clear();
        SampleCache.get().clear();
        List<CellPos> positions = new ArrayList<>(region.cells());
        Collections.reverse(positions);
        for (CellPos pos : positions) {
            CellCache.get().getOrComputeWithFlow(pos);
        }
        List<Flowline> warm = FlowlinePreview.trace(region);

        assertEquals(cold, warm);
    }

    @Test
    void previewPacketPreservesSeparatePathsAndTheirSampledHeights() {
        Region region = RegionCache.get().getOrCompute(source.getRegion());
        CellPos terminus = new CellPos(6, 2);
        Flowline first = new Flowline(List.of(source, new CellPos(2, 2), terminus), terminus, true);
        Flowline singleCell = new Flowline(List.of(terminus), terminus, true);
        Flowline invalid = new Flowline(List.of(new CellPos(0, 0)), null, false);
        Region.Packet packet = new Region.Packet(region, List.of(first, singleCell, invalid));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            Region.Packet.encode(packet, buffer);
            Region.Packet decoded = Region.Packet.decode(buffer);
            buffer.clear();
            Region.Packet.encode(decoded, buffer);
            CompoundTag data = buffer.readNbt();
            ClientRegionCache.Region client = ClientRegionCache.Region.decode(data);

            assertEquals(2, client.paths().size());
            assertEquals(first.cells(), client.paths().get(0).points().stream().map(ClientRegionCache.Point::pos).toList());
            assertEquals(250, client.paths().get(0).points().get(0).y());
            assertEquals(1, client.paths().get(1).points().size());
            assertEquals(terminus, client.paths().get(1).points().get(0).pos());
            assertTrue(client.edges().isEmpty());
        } finally {
            buffer.release();
        }
    }

    @Test
    void regionWithoutBoundariesHasNoPreviewPaths() {
        Region region = RegionCache.get().getOrCompute(new RegionPos(-8, 0));

        assertTrue(FlowlinePreview.trace(region).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void existingVisCommandSendsPathsAndTogglesTheWholeOverlayOff() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ServerPlayer player = mock(ServerPlayer.class, RETURNS_DEEP_STUBS);
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        when(player.blockPosition()).thenReturn(source.getRegion().getWorldPosition(100));
        when(player.serverLevel().getChunkSource().getGenerator().getSeaLevel()).thenReturn(63);
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
            boolean foundPath = false;
            for (Message message : messages) {
                if (!(message instanceof Region.Packet packet)) {
                    continue;
                }
                FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    Region.Packet.encode(packet, buffer);
                    ClientRegionCache.Region region = ClientRegionCache.Region.decode(buffer.readNbt());
                    foundPath |= region.paths().stream().anyMatch(path -> path.points().get(0).pos().equals(source));
                } finally {
                    buffer.release();
                }
            }
            assertTrue(foundPath, "The existing vis command must send the source path to the client");
            assertNull(WatershedCache.get());

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

    private static class SlopingTerrain implements SampleProvider {
        @Override
        public Sample sampleCore(ChunkPos pos) {
            boolean ocean = pos.x >= 7;
            int height = ocean ? 40 : 200 - 15 * pos.x - 3 * Math.abs(pos.z - 2);
            if (pos.x == 1 && pos.z == 2) {
                height = 250;
            }
            return new Sample(pos, ocean ? -0.5 : 0.5, height, ocean, 0.5, 0.1, 0.1, 0.1);
        }

        @Override
        public double sample(DensityField field, ChunkPos pos) {
            throw new AssertionError("All fields are supplied by the terrain sample");
        }
    }
}
