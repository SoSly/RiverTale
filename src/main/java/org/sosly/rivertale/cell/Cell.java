package org.sosly.rivertale.cell;

import net.minecraft.nbt.CompoundTag;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;

public record Cell(CellPos pos, Sample sample, Feature feature, Direction flowDirection) {
    private static final double SQRT2 = Math.sqrt(2.0);

    public static Direction computeFlowDirection(CellPos pos, Sample sample, SampleCache cache) {
        if (cache == null) {
            return Direction.NONE;
        }

        double currentDensity = sample.continents() + sample.depth();
        double steepestSlope = 0;
        Direction steepest = Direction.NONE;

        for (Direction dir : Direction.D8) {
            CellPos neighbor = pos.relative(dir);
            Sample neighborSample = cache.getOrCompute(neighbor.getMiddleBlockX(), neighbor.getMiddleBlockZ());
            double neighborDensity = neighborSample.continents() + neighborSample.depth();
            double slope = currentDensity - neighborDensity;

            if (dir.dx != 0 && dir.dz != 0) {
                slope /= SQRT2;
            }

            if (slope > steepestSlope) {
                steepestSlope = slope;
                steepest = dir;
            }
        }

        return steepest;
    }

    public static boolean hasUpstreamNeighbor(CellPos pos, SampleCache cache) {
        if (cache == null) {
            return false;
        }

        for (Direction dir : Direction.D8) {
            CellPos neighbor = pos.relative(dir);
            Sample neighborSample = cache.getOrCompute(neighbor.getMiddleBlockX(), neighbor.getMiddleBlockZ());
            Direction neighborFlow = computeFlowDirection(neighbor, neighborSample, cache);
            if (neighborFlow == dir.opposite()) {
                return true;
            }
        }

        return false;
    }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", pos.toLong());
        tag.put("sample", sample.encode());
        tag.putString("feature", feature.name());
        tag.putString("flowDirection", flowDirection.name());
        return tag;
    }

    public static Cell decode(CompoundTag tag) {
        return new Cell(
            new CellPos(tag.getLong("pos")),
            Sample.decode(tag.getCompound("sample")),
            Feature.valueOf(tag.getString("feature")),
            Direction.valueOf(tag.getString("flowDirection"))
        );
    }
}
