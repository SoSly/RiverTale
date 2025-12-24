package org.sosly.rivertale.path.validators;

import java.util.List;
import java.util.Optional;
import org.sosly.rivertale.cell.Grid;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.path.Crossing;
import org.sosly.rivertale.path.Flow;
import org.sosly.rivertale.path.FlowCalculator;
import org.sosly.rivertale.path.Manager;
import org.sosly.rivertale.path.MutableFlowState;
import org.sosly.rivertale.path.Refiner;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.terrain.DensityProvider;

public class LengthValidator {

    private LengthValidator() {
    }

    public static void validate(MutableFlowState state, ValidationContext ctx) {
        for (Direction dir : state.getInputDirections()) {
            if (!isValidLength(dir, state, ctx)) {
                state.removePath(dir);
            }
        }
    }

    private static boolean isValidLength(Direction dir, MutableFlowState state, ValidationContext ctx) {
        List<CellPos> path = state.getPath(dir);
        if (path.isEmpty()) {
            return false;
        }

        Crossing crossing = state.getCrossing(dir);
        boolean isOutput = crossing != null && crossing.isSource(ctx.regionPos());

        int totalLength;
        if (isOutput) {
            totalLength = computeOutputLength(dir, path.size(), ctx);
        } else {
            totalLength = computeInputLength(dir, path.size(), state.getPrimaryOutputDirection(), ctx);
        }
        return totalLength >= ctx.minimumRiverLength();
    }

    private static int computeInputLength(
            Direction inputDir,
            int localPathLength,
            Direction outputDir,
            ValidationContext ctx) {

        int remaining = ctx.minimumRiverLength() - localPathLength;
        if (remaining <= 0) {
            return localPathLength;
        }

        int upstream = traceUpstream(ctx.regionPos(), inputDir, remaining, ctx);
        remaining -= upstream;
        if (remaining <= 0) {
            return localPathLength + upstream;
        }

        int downstream = traceDownstream(outputDir, ctx, remaining);
        return localPathLength + upstream + downstream;
    }

    private static int computeOutputLength(Direction outputDir, int localPathLength, ValidationContext ctx) {
        int remaining = ctx.minimumRiverLength() - localPathLength;
        if (remaining <= 0) {
            return localPathLength;
        }

        int downstream = traceDownstream(outputDir, ctx, remaining);
        return localPathLength + downstream;
    }

    private static int traceUpstream(RegionPos pos, Direction dir, int remaining, ValidationContext ctx) {
        if (remaining <= 0 || ctx.provider() == null) {
            return 0;
        }

        RegionPos neighbor = pos.relative(dir);
        Optional<Flow> flow = computeNeighborFlow(neighbor, ctx.provider());
        if (flow.isEmpty()) {
            return 0;
        }

        Direction expectedOutput = dir.opposite();
        if (flow.get().primaryOutputDirection() != expectedOutput) {
            return 0;
        }

        int neighborLength = findMaxPathLength(flow.get());
        if (neighborLength == 0) {
            return 0;
        }

        int counted = Math.min(neighborLength, remaining);
        remaining -= counted;

        if (remaining <= 0) {
            return counted;
        }

        Direction neighborInput = findLongestInputDirection(flow.get());
        if (neighborInput == null || neighborInput == Direction.NONE) {
            return counted;
        }

        return counted + traceUpstream(neighbor, neighborInput, remaining, ctx);
    }

    private static int traceDownstream(Direction outputDir, ValidationContext ctx, int remaining) {
        RegionType regionType = ctx.featureRegionType();
        if (regionType == RegionType.SHORE || regionType == RegionType.BASIN) {
            return 0;
        }

        return traceDownstreamFrom(ctx.regionPos(), outputDir, regionType, remaining, ctx);
    }

    private static int traceDownstreamFrom(
            RegionPos pos,
            Direction dir,
            RegionType regionType,
            int remaining,
            ValidationContext ctx) {

        if (remaining <= 0 || ctx.provider() == null) {
            return 0;
        }

        if (regionType == RegionType.SHORE || regionType == RegionType.BASIN) {
            return 0;
        }

        if (dir == null || dir == Direction.NONE) {
            return 0;
        }

        RegionPos neighbor = pos.relative(dir);
        RegionType neighborTerrain = Manager.classifyTerrain(neighbor, ctx.provider());
        Optional<Flow> flow = computeNeighborFlow(neighbor, ctx.provider());
        if (flow.isEmpty()) {
            return 0;
        }

        List<CellPos> neighborPath = flow.get().riverPaths().get(dir.opposite());
        if (neighborPath == null || neighborPath.isEmpty()) {
            return 0;
        }

        int counted = Math.min(neighborPath.size(), remaining);
        remaining -= counted;

        if (remaining <= 0) {
            return counted;
        }

        RegionType nextRegionType = neighborTerrain != null ? neighborTerrain : RegionType.DIVIDE;
        return counted + traceDownstreamFrom(
            neighbor, flow.get().primaryOutputDirection(), nextRegionType, remaining, ctx);
    }

    private static Optional<Flow> computeNeighborFlow(RegionPos pos, DensityProvider provider) {

        RegionType terrain = Manager.classifyTerrain(pos, provider);
        if (terrain == RegionType.BODY) {
            return Optional.empty();
        }

        Grid flowGrid = FlowCalculator.compute(pos, provider::getDensity);
        RegionType[] neighborTypes = Manager.loadNeighborFeatures(pos, provider);
        Grid[] neighborGrids = computeNeighborGrids(pos, provider);

        RegionType featureType = terrain != null ? terrain : RegionType.DIVIDE;
        return Optional.of(Refiner.refine(pos, flowGrid, featureType, neighborGrids, neighborTypes, provider));
    }

    private static Grid[] computeNeighborGrids(RegionPos pos, DensityProvider provider) {
        Direction[] cardinals = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        Grid[] grids = new Grid[4];

        for (int i = 0; i < 4; i++) {
            RegionPos neighborPos = cardinals[i].neighbor(pos);
            grids[i] = FlowCalculator.compute(neighborPos, provider::getDensity);
        }

        return grids;
    }

    private static int findMaxPathLength(Flow flow) {
        int maxLength = 0;
        for (List<CellPos> path : flow.riverPaths().values()) {
            if (path.size() > maxLength) {
                maxLength = path.size();
            }
        }
        return maxLength;
    }

    private static Direction findLongestInputDirection(Flow flow) {
        Direction longestDir = null;
        int maxLength = 0;

        for (var entry : flow.riverPaths().entrySet()) {
            if (entry.getValue().size() > maxLength) {
                maxLength = entry.getValue().size();
                longestDir = entry.getKey();
            }
        }

        return longestDir;
    }
}
