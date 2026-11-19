package org.sosly.rivertale.region;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.client.ClientRegionCache;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.networking.Message;
import org.sosly.rivertale.terrain.Coastal;
import org.sosly.rivertale.terrain.Fluvial;
import org.sosly.rivertale.world.RiverShaping;

public record Region(RegionPos pos, RegionType type, List<Boundary> boundaries) {

    public Region(RegionPos pos, RegionType type) {
        this(pos, type, List.of());
    }

    static Region create(RegionPos pos, CellCache cellCache, SampleCache sampleCache) {
        RegionType type = RegionTypeCache.get().getOrCompute(pos, sampleCache);
        Region region = new Region(pos, type);
        for (CellPos cellPos : region.cells()) {
            RiverShaping.preclassify(cellPos);
        }
        List<Boundary> boundaries = switch (type) {
            case COASTAL -> Coastal.boundaries(pos, cellCache);
            case FLUVIAL, INLAND -> Fluvial.boundaries(pos, cellCache);
            default -> List.of();
        };
        return region.withBoundaries(boundaries);
    }

    public Region withBoundaries(List<Boundary> boundaries) {
        return new Region(pos, type, boundaries);
    }

    public List<CellPos> cells() {
        List<CellPos> result = new ArrayList<>();
        CellPos min = pos.getMinCell();
        int count = CommonConfig.get().cellsPerRegion();
        for (int x = 0; x < count; x++) {
            for (int z = 0; z < count; z++) {
                result.add(new CellPos(min.x() + x, min.z() + z));
            }
        }
        return result;
    }

    public static class Packet extends Message {
        private CompoundTag data;

        public Packet(CompoundTag data) {
            this.data = data;
        }

        public static Packet decode(FriendlyByteBuf buf) {
            CompoundTag tag = buf.readNbt();
            if (tag == null) {
                LogUtils.getLogger().warn("Received null NBT in Region packet");
                return new Packet(new CompoundTag());
            }
            return new Packet(tag);
        }

        public static void encode(Packet msg, FriendlyByteBuf buf) {
            buf.writeNbt(msg.data);
        }

        public static void handle(Packet msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                context.enqueueWork(() -> handleClient(msg.data));
            }
            context.setPacketHandled(true);
        }

        private static void handleClient(CompoundTag data) {
            if (data.isEmpty()) {
                return;
            }
            ClientRegionCache.Region region = ClientRegionCache.Region.decode(data);
            ClientRegionCache.get().put(region);
        }
    }
}
