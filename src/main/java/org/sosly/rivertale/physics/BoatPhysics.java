package org.sosly.rivertale.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

public class BoatPhysics {

    public record WaterCheckResult(double waterLevel, boolean inWater) {}

    public static Boat.Status checkUnderwater(Boat boat) {
        AABB aabb = boat.getBoundingBox();
        double boatTop = aabb.maxY + 0.001D;

        int minX = Mth.floor(aabb.minX);
        int maxX = Mth.ceil(aabb.maxX);
        int minZ = Mth.floor(aabb.minZ);
        int maxZ = Mth.ceil(aabb.maxZ);

        int boatTopBlock = Mth.floor(boatTop);
        int searchCeiling = boatTopBlock + 3;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = boatTopBlock; y <= searchCeiling; y++) {
            for (int x = minX; x < maxX; x++) {
                for (int z = minZ; z < maxZ; z++) {
                    pos.set(x, y, z);
                    FluidState fluid = boat.level().getFluidState(pos);

                    if (!fluid.is(FluidTags.WATER)) {
                        continue;
                    }
                    if (!fluid.isSource()) {
                        continue;
                    }
                    if (aabb.minY >= pos.getY()) {
                        continue;
                    }

                    return Boat.Status.UNDER_WATER;
                }
            }
        }

        return null;
    }

    public static WaterCheckResult checkInWater(Boat boat) {
        AABB aabb = boat.getBoundingBox();

        int minX = Mth.floor(aabb.minX);
        int maxX = Mth.ceil(aabb.maxX);
        int minZ = Mth.floor(aabb.minZ);
        int maxZ = Mth.ceil(aabb.maxZ);

        int boatBottomBlock = Mth.floor(aabb.minY);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double highestWater = -Double.MAX_VALUE;
        boolean inWater = false;

        for (int y = boatBottomBlock; y <= boatBottomBlock + 1; y++) {
            for (int x = minX; x < maxX; x++) {
                for (int z = minZ; z < maxZ; z++) {
                    pos.set(x, y, z);
                    FluidState fluid = boat.level().getFluidState(pos);

                    if (!fluid.is(FluidTags.WATER)) {
                        continue;
                    }

                    float waterSurface = y + fluid.getHeight(boat.level(), pos);
                    if (waterSurface > highestWater) {
                        highestWater = waterSurface;
                    }
                    if (aabb.minY < waterSurface) {
                        inWater = true;
                    }
                }
            }
        }

        if (highestWater > -Double.MAX_VALUE) {
            return new WaterCheckResult(highestWater, true);
        }

        if (inWater) {
            return new WaterCheckResult(-Double.MAX_VALUE, true);
        }

        return null;
    }
}
