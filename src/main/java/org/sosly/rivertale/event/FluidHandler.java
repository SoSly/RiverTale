package org.sosly.rivertale.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.sosly.rivertale.capability.FlowDirectionCapability;
import org.sosly.rivertale.capability.FlowDirectionData;
import org.sosly.rivertale.physics.FluidFlow;


public class FluidHandler {
    @SubscribeEvent
    public void onAttachCapabilities(AttachCapabilitiesEvent<LevelChunk> event) {
        LevelChunk chunk = event.getObject();
        event.addCapability(FlowDirectionCapability.ID, new FlowDirectionCapability.Provider(chunk.getPos()));
    }

    @SubscribeEvent
    public void onBreakEvent(BlockEvent.BreakEvent event) {
        BlockPos pos = event.getPos();
        if (hasAdjacentRiverWater(event.getLevel(), pos)) {
            FluidFlow.unlock(pos);
        }
    }

    @SubscribeEvent
    public void onEntityPlaceEvent(BlockEvent.EntityPlaceEvent event) {
        BlockPos pos = event.getPos();
        LevelAccessor level = event.getLevel();
        if (hasAdjacentRiverWater(level, pos)) {
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = pos.relative(dir);
                BlockState state = level.getBlockState(neighbor);

                boolean isAir = state.isAir();
                boolean isFlowingWater = state.is(Blocks.WATER) && !level.getFluidState(neighbor).isSource();
                if (!isAir && !isFlowingWater) {
                    continue;
                }

                if (!(level.getChunk(neighbor) instanceof LevelChunk chunk)) {
                    continue;
                }

                FlowDirectionData cap = FlowDirectionCapability.get(chunk);
                if (cap == null) {
                    continue;
                }

                int localX = neighbor.getX() & 15;
                int localZ = neighbor.getZ() & 15;
                if (cap.get(localX, localZ) == null) {
                    continue;
                }

                FluidFlow.unlock(neighbor);
            }
        }
    }

    private boolean hasAdjacentRiverWater(LevelAccessor level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            if (!level.getBlockState(neighbor).is(Blocks.WATER)) {
                continue;
            }
            if (!(level.getChunk(neighbor) instanceof LevelChunk chunk)) {
                continue;
            }
            FlowDirectionData cap = FlowDirectionCapability.get(chunk);
            if (cap == null) {
                continue;
            }
            int localX = neighbor.getX() & 15;
            int localZ = neighbor.getZ() & 15;
            if (cap.get(localX, localZ) != null) {
                return true;
            }
        }
        return false;
    }
}
