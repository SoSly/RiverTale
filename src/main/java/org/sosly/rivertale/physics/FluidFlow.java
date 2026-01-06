package org.sosly.rivertale.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.sosly.rivertale.capability.FlowDirectionCapability;
import org.sosly.rivertale.capability.FlowDirectionData;
import org.sosly.rivertale.client.FlowDirectionClientCache;
import org.sosly.rivertale.core.Direction;

public class FluidFlow {

    public static Vec3 getOverrideFlow(BlockGetter level, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;

        Direction direction = getFromPending(chunkPos, localX, localZ);

        if (direction == null && level instanceof Level world) {
            direction = world.isClientSide
                ? FlowDirectionClientCache.get(chunkPos, localX, localZ)
                : getFromCapability(world, chunkPos, localX, localZ);
        }

        if (direction == null || direction == Direction.NONE) {
            return null;
        }

        return new Vec3(direction.dx, 0.0, direction.dz).normalize();
    }

    private static Direction getFromCapability(Level world, ChunkPos chunkPos, int localX, int localZ) {
        ChunkAccess chunk = world.getChunk(chunkPos.x, chunkPos.z);
        if (!(chunk instanceof LevelChunk levelChunk)) {
            return null;
        }

        FlowDirectionData flowData = FlowDirectionCapability.get(levelChunk);
        if (flowData == null) {
            return null;
        }

        return flowData.get(localX, localZ);
    }

    private static Direction getFromPending(ChunkPos chunkPos, int localX, int localZ) {
        Direction[][] pending = FlowDirectionCapability.getPending(chunkPos);
        if (pending == null) {
            return null;
        }
        return pending[localX][localZ];
    }
}
