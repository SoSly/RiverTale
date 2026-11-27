package org.sosly.rivertale.cell;

import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
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
    OptionalInt cellY,
    int overFill
) {
    public Cell(CellPos pos, Set<ChunkPos> samplePositions, List<FlowDirection> flowDirections, Feature feature, OptionalInt cellY) {
        this(pos, samplePositions, flowDirections, feature, cellY, 0);
    }

    public Cell withOverFill(int depth) {
        return new Cell(pos, samplePositions, flowDirections, feature, cellY, depth);
    }

    public Cell(CellPos pos, Set<ChunkPos> samplePositions, List<FlowDirection> flowDirections, Feature feature) {
        this(pos, samplePositions, flowDirections, feature, OptionalInt.empty());
    }

    public Cell(CellPos pos, Set<ChunkPos> samplePositions) {
        this(pos, samplePositions, List.of(), Feature.NONE);
    }

    public double averageContinents() {
        double sum = 0;
        for (ChunkPos pos : samplePositions) {
            Sample sample = SampleCache.get().getOrCompute(pos);
            sum += sample.continents();
        }
        return sum / samplePositions.size();
    }

    public int averageEstimatedTerrainHeight() {
        double sum = 0;
        for (ChunkPos pos : samplePositions) {
            Sample sample = SampleCache.get().getOrCompute(pos);
            sum += sample.terrainHeight();
        }
        return (int) Math.round(sum / samplePositions.size());
    }

    public double averageErosion() {
        double sum = 0;
        SampleCache cache = SampleCache.get();
        for (ChunkPos pos : samplePositions) {
            Sample sample = cache.getOrCompute(pos);
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
            Sample sample = cache.getOrCompute(pos);
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
            Sample sample = cache.getOrCompute(pos);
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
            Sample sample = cache.getOrCompute(pos);
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
            if (sample.terrainHeight() >= seaLevel) {
                return false;
            }
        }
        return true;
    }

    public boolean hasInflowingNeighbor() {
        CellCache cache = CellCache.get();
        for (FlowDirection dir : FlowDirection.D8) {
            CellPos neighborPos = pos.relative(dir);
            Cell neighbor = cache.getOrComputeWithFlow(neighborPos);
            if (neighbor.flowDirections().contains(dir.opposite())) {
                return true;
            }
        }
        return false;
    }

    public Cell withFlowDirections(List<FlowDirection> flowDirections) {
        return new Cell(pos, samplePositions, flowDirections, feature, cellY, overFill);
    }

    public Cell withFeature(Feature feature) {
        return new Cell(pos, samplePositions, flowDirections, feature, cellY, overFill);
    }

    public Cell withCellY(int height) {
        return new Cell(pos, samplePositions, flowDirections, feature, OptionalInt.of(height), overFill);
    }
}
