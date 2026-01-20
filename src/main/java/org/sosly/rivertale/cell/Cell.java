package org.sosly.rivertale.cell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.world.RiverShaping;

public record Cell(
    CellPos pos,
    Map<ChunkPos, Sample> samples,
    List<FlowDirection> flowDirections,
    Feature feature,
    BlockPos waypoint,
    Integer entryY,
    Integer exitY,
    Integer width,
    Integer depth,
    Integer upstreamCount,
    Integer downstreamCount,
    CellPos terminus,
    Double entryT,
    Double exitT
) {
    public Cell(CellPos pos, Map<ChunkPos, Sample> samples) {
        this(pos, samples, List.of(), Feature.NONE, null, null, null, null, null, null, null, null, null, null);
    }

    public double averageContinents() {
        return samples.values().stream()
            .mapToDouble(Sample::continents)
            .average()
            .orElse(0.0);
    }

    public double averageDepth() {
        return samples.values().stream()
            .mapToDouble(Sample::depth)
            .average()
            .orElse(0.0);
    }

    public int averageEstimatedTerrainHeight() {
        return (int) Math.round(70 + 144 * averageDepth());
    }

    public double averageErosion() {
        return samples.values().stream()
            .filter(Sample::hasFullDensities)
            .mapToDouble(Sample::erosion)
            .average()
            .orElseThrow(() -> new IllegalStateException("No enriched samples"));
    }

    public double averageRidges() {
        return samples.values().stream()
            .filter(Sample::hasFullDensities)
            .mapToDouble(Sample::ridges)
            .average()
            .orElseThrow(() -> new IllegalStateException("No enriched samples"));
    }

    public double averageTemperature() {
        return samples.values().stream()
            .filter(Sample::hasFullDensities)
            .mapToDouble(Sample::temperature)
            .average()
            .orElseThrow(() -> new IllegalStateException("No enriched samples"));
    }

    public double averageVegetation() {
        return samples.values().stream()
            .filter(Sample::hasFullDensities)
            .mapToDouble(Sample::vegetation)
            .average()
            .orElseThrow(() -> new IllegalStateException("No enriched samples"));
    }

    public boolean isOcean() {
        return averageContinents() < CommonConfig.get().oceanThreshold();
    }

    public boolean isBasin() {
        if (samples.values().stream().anyMatch(Sample::isOcean)) {
            return false;
        }
        return averageEstimatedTerrainHeight() < RiverShaping.getSeaLevel();
    }

    public Cell withFlowDirections(List<FlowDirection> flowDirections) {
        return new Cell(pos, samples, flowDirections, feature, waypoint, entryY, exitY, width, depth, upstreamCount, downstreamCount, terminus, entryT, exitT);
    }

    public Cell withFeature(Feature feature) {
        return new Cell(pos, samples, flowDirections, feature, waypoint, entryY, exitY, width, depth, upstreamCount, downstreamCount, terminus, entryT, exitT);
    }

    public Cell withWaypoint(BlockPos waypoint) {
        return new Cell(pos, samples, flowDirections, feature, waypoint, entryY, exitY, width, depth, upstreamCount, downstreamCount, terminus, entryT, exitT);
    }

    public Cell withElevations(int entryY, int exitY) {
        return new Cell(pos, samples, flowDirections, feature, waypoint, entryY, exitY, width, depth, upstreamCount, downstreamCount, terminus, entryT, exitT);
    }

    public Cell withAccumulation(int width, int depth, int upstreamCount, int downstreamCount) {
        return new Cell(pos, samples, flowDirections, feature, waypoint, entryY, exitY, width, depth, upstreamCount, downstreamCount, terminus, entryT, exitT);
    }

    public Cell withWatershed(CellPos terminus) {
        return new Cell(pos, samples, flowDirections, feature, waypoint, entryY, exitY, width, depth, upstreamCount, downstreamCount, terminus, entryT, exitT);
    }

    public Cell withSplineParams(double entryT, double exitT) {
        return new Cell(pos, samples, flowDirections, feature, waypoint, entryY, exitY, width, depth, upstreamCount, downstreamCount, terminus, entryT, exitT);
    }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", pos.toLong());
        tag.putString("feature", feature.name());

        ListTag sampleList = new ListTag();
        for (Sample sample : samples.values()) {
            sampleList.add(sample.encode());
        }
        tag.put("samples", sampleList);

        ListTag flowList = new ListTag();
        for (FlowDirection dir : flowDirections) {
            flowList.add(StringTag.valueOf(dir.name()));
        }
        tag.put("flowDirections", flowList);

        if (waypoint != null) {
            tag.putLong("waypoint", waypoint.asLong());
        }
        if (entryY != null) {
            tag.putInt("entryY", entryY);
        }
        if (exitY != null) {
            tag.putInt("exitY", exitY);
        }
        if (width != null) {
            tag.putInt("width", width);
        }
        if (depth != null) {
            tag.putInt("depth", depth);
        }
        if (upstreamCount != null) {
            tag.putInt("upstreamCount", upstreamCount);
        }
        if (downstreamCount != null) {
            tag.putInt("downstreamCount", downstreamCount);
        }
        if (terminus != null) {
            tag.putLong("terminus", terminus.toLong());
        }
        if (entryT != null) {
            tag.putDouble("entryT", entryT);
        }
        if (exitT != null) {
            tag.putDouble("exitT", exitT);
        }

        return tag;
    }

    public static Cell decode(CompoundTag tag) {
        List<FlowDirection> flows = new ArrayList<>();
        ListTag flowList = tag.getList("flowDirections", Tag.TAG_STRING);
        for (int i = 0; i < flowList.size(); i++) {
            flows.add(FlowDirection.valueOf(flowList.getString(i)));
        }

        Map<ChunkPos, Sample> samples = new HashMap<>();
        ListTag sampleList = tag.getList("samples", Tag.TAG_COMPOUND);
        for (int i = 0; i < sampleList.size(); i++) {
            Sample sample = Sample.decode(sampleList.getCompound(i));
            samples.put(sample.pos(), sample);
        }

        return new Cell(
            new CellPos(tag.getLong("pos")),
            samples,
            flows,
            Feature.valueOf(tag.getString("feature")),
            tag.contains("waypoint") ? BlockPos.of(tag.getLong("waypoint")) : null,
            tag.contains("entryY") ? tag.getInt("entryY") : null,
            tag.contains("exitY") ? tag.getInt("exitY") : null,
            tag.contains("width") ? tag.getInt("width") : null,
            tag.contains("depth") ? tag.getInt("depth") : null,
            tag.contains("upstreamCount") ? tag.getInt("upstreamCount") : null,
            tag.contains("downstreamCount") ? tag.getInt("downstreamCount") : null,
            tag.contains("terminus") ? new CellPos(tag.getLong("terminus")) : null,
            tag.contains("entryT") ? tag.getDouble("entryT") : null,
            tag.contains("exitT") ? tag.getDouble("exitT") : null
        );
    }
}
