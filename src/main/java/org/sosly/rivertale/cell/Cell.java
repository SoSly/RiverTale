package org.sosly.rivertale.cell;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;

public record Cell(CellPos pos, Sample sample, Feature feature, List<FlowDirection> flowDirections, int y) {
    private static final double SQRT2 = Math.sqrt(2.0);

    private record SlopeEntry(FlowDirection dir, double slope) {}

    public static List<FlowDirection> computeFlowDirections(CellPos pos, Sample sample, SampleCache cache) {
        if (cache == null) {
            return List.of();
        }

        double currentDensity = sample.continents() + sample.depth();
        List<SlopeEntry> downhill = new ArrayList<>();

        for (FlowDirection dir : FlowDirection.D8) {
            CellPos neighbor = pos.relative(dir);
            Sample neighborSample = cache.getOrCompute(neighbor.getMiddleBlockX(), neighbor.getMiddleBlockZ());
            double neighborDensity = neighborSample.continents() + neighborSample.depth();
            double slope = currentDensity - neighborDensity;

            if (dir.dx != 0 && dir.dz != 0) {
                slope /= SQRT2;
            }

            if (slope > 0) {
                downhill.add(new SlopeEntry(dir, slope));
            }
        }

        downhill.sort(Comparator.comparingDouble(SlopeEntry::slope).reversed());
        return downhill.stream().map(SlopeEntry::dir).toList();
    }

    public static boolean hasUpstreamNeighbor(CellPos pos, SampleCache cache) {
        if (cache == null) {
            return false;
        }

        for (FlowDirection dir : FlowDirection.D8) {
            CellPos neighbor = pos.relative(dir);
            Sample neighborSample = cache.getOrCompute(neighbor.getMiddleBlockX(), neighbor.getMiddleBlockZ());
            List<FlowDirection> neighborFlows = computeFlowDirections(neighbor, neighborSample, cache);
            if (neighborFlows.contains(dir.opposite())) {
                return true;
            }
        }

        return false;
    }

    public Cell withY(int newY) {
        return new Cell(pos, sample, feature, flowDirections, newY);
    }

    public Cell withFeature(Feature newFeature) {
        return new Cell(pos, sample, newFeature, flowDirections, y);
    }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", pos.toLong());
        tag.put("sample", sample.encode());
        tag.putString("feature", feature.name());
        tag.putInt("y", y);

        ListTag flowList = new ListTag();
        for (FlowDirection dir : flowDirections) {
            flowList.add(StringTag.valueOf(dir.name()));
        }
        tag.put("flowDirections", flowList);

        return tag;
    }

    public static Cell decode(CompoundTag tag) {
        List<FlowDirection> flows = new ArrayList<>();
        ListTag flowList = tag.getList("flowDirections", Tag.TAG_STRING);
        for (int i = 0; i < flowList.size(); i++) {
            flows.add(FlowDirection.valueOf(flowList.getString(i)));
        }

        return new Cell(
            new CellPos(tag.getLong("pos")),
            Sample.decode(tag.getCompound("sample")),
            Feature.valueOf(tag.getString("feature")),
            flows,
            tag.getInt("y")
        );
    }
}
