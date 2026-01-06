package org.sosly.rivertale.physics;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.sosly.rivertale.capability.FlowDirectionCapability;
import org.sosly.rivertale.capability.FlowDirectionData;
import org.sosly.rivertale.client.FlowDirectionClientCache;
import org.sosly.rivertale.core.FlowDirection;

public class FluidFlow {
    private static final Set<BlockPos> PLAYER_UNLOCKED = ConcurrentHashMap.newKeySet();

    public static boolean allowSpread(LevelAccessor level,
                                      BlockPos targetPos,
                                      BlockState block,
                                      Direction dir,
                                      FluidState fluid) {

        BlockPos sourcePos = targetPos.relative(dir.getOpposite());
        BlockState sourceState = level.getBlockState(sourcePos);
        if (!(level.getChunk(sourcePos) instanceof LevelChunk sourceChunk)) {
            return true;
        }
        FlowDirection sourceFlow = getFlowDirection(sourceChunk, sourcePos);

        if (PLAYER_UNLOCKED.remove(targetPos)) {
            return true;
        }

        return !sourceState.is(Blocks.WATER) || sourceFlow == null;
    }

    public static Vec3 getOverrideFlow(BlockGetter level, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;

        FlowDirection direction = getFromPending(chunkPos, localX, localZ);

        if (direction == null && level instanceof Level world) {
            direction = world.isClientSide
                ? FlowDirectionClientCache.get(chunkPos, localX, localZ)
                : getFromCapability(world, chunkPos, localX, localZ);
        }

        if (direction == null || direction == FlowDirection.NONE) {
            return null;
        }

        return new Vec3(direction.dx, 0.0, direction.dz).normalize();
    }

    private static FlowDirection getFromCapability(Level world, ChunkPos chunkPos, int localX, int localZ) {
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

    private static FlowDirection getFromPending(ChunkPos chunkPos, int localX, int localZ) {
        FlowDirection[][] pending = FlowDirectionCapability.getPending(chunkPos);
        if (pending == null) {
            return null;
        }
        return pending[localX][localZ];
    }

    private @Nullable static FlowDirection getFlowDirection(LevelChunk chunk, BlockPos pos) {
        FlowDirectionData cap = FlowDirectionCapability.get(chunk);
        if (cap == null) {
            return null;
        }

        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;
        return cap.get(localX, localZ);
    }

    public static void unlock(BlockPos pos) {
        PLAYER_UNLOCKED.add(pos.immutable());
    }
}
