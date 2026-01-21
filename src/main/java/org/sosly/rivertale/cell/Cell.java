package org.sosly.rivertale.cell;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.density.DensityField;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.world.RiverShaping;

public record Cell(
    CellPos pos,
    Set<ChunkPos> samplePositions,
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
    public Cell(CellPos pos, Set<ChunkPos> samplePositions) {
        this(pos, samplePositions, List.of(), Feature.NONE, null, null, null, null, null, null, null, null, null, null);
    }

    public double averageContinents() {
        double sum = 0;
        for (ChunkPos pos : samplePositions) {
            Sample sample = SampleCache.get().getOrCompute(pos);
            sum += sample.continents();
        }
        return sum / samplePositions.size();
    }

    public double averageDepth() {
        double sum = 0;
        for (ChunkPos pos : samplePositions) {
            Sample sample = SampleCache.get().getOrCompute(pos);
            sum += sample.depth();
        }
        return sum / samplePositions.size();
    }

    public int averageEstimatedTerrainHeight() {
        return (int) Math.round(70 + 144 * averageDepth());
    }

    public double averageErosion() {
        double sum = 0;
        SampleCache cache = SampleCache.get();
        for (ChunkPos pos : samplePositions) {
            Sample sample = cache.getOrCompute(pos.getMiddleBlockX(), pos.getMiddleBlockZ());
            if (sample.erosion() == null) {
                double erosion = cache.provider().sample(DensityField.EROSION, pos);
                sample = sample.withErosion(erosion);
                cache.put(pos, sample);
            }
            sum += sample.erosion();
        }
        return sum / samplePositions.size();
    }

    public double averageRidges() {
        double sum = 0;
        SampleCache cache = SampleCache.get();
        for (ChunkPos pos : samplePositions) {
            Sample sample = cache.getOrCompute(pos.getMiddleBlockX(), pos.getMiddleBlockZ());
            if (sample.ridges() == null) {
                double ridges = cache.provider().sample(DensityField.RIDGES, pos);
                sample = sample.withRidges(ridges);
                cache.put(pos, sample);
            }
            sum += sample.ridges();
        }
        return sum / samplePositions.size();
    }

    public double averageTemperature() {
        double sum = 0;
        SampleCache cache = SampleCache.get();
        for (ChunkPos pos : samplePositions) {
            Sample sample = cache.getOrCompute(pos.getMiddleBlockX(), pos.getMiddleBlockZ());
            if (sample.temperature() == null) {
                double temp = cache.provider().sample(DensityField.TEMPERATURE, pos);
                sample = sample.withTemperature(temp);
                cache.put(pos, sample);
            }
            sum += sample.temperature();
        }
        return sum / samplePositions.size();
    }

    public double averageVegetation() {
        double sum = 0;
        SampleCache cache = SampleCache.get();
        for (ChunkPos pos : samplePositions) {
            Sample sample = cache.getOrCompute(pos.getMiddleBlockX(), pos.getMiddleBlockZ());
            if (sample.vegetation() == null) {
                double vegetation = cache.provider().sample(DensityField.VEGETATION, pos);
                sample = sample.withVegetation(vegetation);
                cache.put(pos, sample);
            }
            sum += sample.vegetation();
        }
        return sum / samplePositions.size();
    }

    public boolean isOcean() {
        for (ChunkPos pos : samplePositions) {
            Sample sample = SampleCache.get().getOrCompute(pos);
            if (!sample.isOcean()) {
                return false;
            }
        }
        return true;
    }

    public boolean isBasin() {
        int seaLevel = RiverShaping.getSeaLevel();
        for (ChunkPos pos : samplePositions) {
            Sample sample = SampleCache.get().getOrCompute(pos);
            if (sample.isOcean()) {
                return false;
            }
            if (sample.estimatedTerrainHeight() >= seaLevel) {
                return false;
            }
        }
        return true;
    }

    public Cell withFlowDirections(List<FlowDirection> flowDirections) {
        return new Cell(pos, samplePositions, flowDirections, feature, waypoint, entryY, exitY, width, depth, upstreamCount, downstreamCount, terminus, entryT, exitT);
    }

    public Cell withFeature(Feature feature) {
        return new Cell(pos, samplePositions, flowDirections, feature, waypoint, entryY, exitY, width, depth, upstreamCount, downstreamCount, terminus, entryT, exitT);
    }

    public Cell withWaypoint(BlockPos waypoint) {
        return new Cell(pos, samplePositions, flowDirections, feature, waypoint, entryY, exitY, width, depth, upstreamCount, downstreamCount, terminus, entryT, exitT);
    }

    public Cell withElevations(int entryY, int exitY) {
        return new Cell(pos, samplePositions, flowDirections, feature, waypoint, entryY, exitY, width, depth, upstreamCount, downstreamCount, terminus, entryT, exitT);
    }

    public Cell withAccumulation(int width, int depth, int upstreamCount, int downstreamCount) {
        return new Cell(pos, samplePositions, flowDirections, feature, waypoint, entryY, exitY, width, depth, upstreamCount, downstreamCount, terminus, entryT, exitT);
    }

    public Cell withWatershed(CellPos terminus) {
        return new Cell(pos, samplePositions, flowDirections, feature, waypoint, entryY, exitY, width, depth, upstreamCount, downstreamCount, terminus, entryT, exitT);
    }

    public Cell withSplineParams(double entryT, double exitT) {
        return new Cell(pos, samplePositions, flowDirections, feature, waypoint, entryY, exitY, width, depth, upstreamCount, downstreamCount, terminus, entryT, exitT);
    }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", pos.toLong());
        tag.putString("feature", feature.name());

        long[] posArray = new long[samplePositions.size()];
        int i = 0;
        for (ChunkPos chunkPos : samplePositions) {
            posArray[i++] = chunkPos.toLong();
        }
        tag.putLongArray("samplePositions", posArray);

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

        Set<ChunkPos> samplePositions = new HashSet<>();
        long[] posArray = tag.getLongArray("samplePositions");
        for (long posLong : posArray) {
            samplePositions.add(new ChunkPos(posLong));
        }

        return new Cell(
            new CellPos(tag.getLong("pos")),
            samplePositions,
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
