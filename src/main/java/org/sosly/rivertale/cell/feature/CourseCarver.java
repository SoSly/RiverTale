package org.sosly.rivertale.cell.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.river.RiverSpline;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.terrain.CarveProposal;

final class CourseCarver {
    private static final int MAX_HALF_WIDTH = 16;
    private static final int SAMPLES_PER_CELL_BLOCK = 3;
    // At depth one, a parabola only cuts columns inside halfWidth / sqrt(2) after rounding.
    private static final double MIN_HALF_WIDTH = 1.5;

    private CourseCarver() {}

    static List<CarveProposal> carve(FeatureHandler handler, Cell cell, Watershed watershed, ChunkPos chunk, long seed) {
        OptionalDouble flow = watershed.flow(cell.pos());
        if (cell.cellY().isEmpty() || flow.isEmpty()) {
            return List.of();
        }
        List<Cell> entries = watershed.upstream(cell.pos()).stream().map(watershed::elevatedCell).toList();
        if (entries.size() > 1) {
            throw new IllegalArgumentException("A course cannot have multiple upstream cells");
        }
        Optional<Cell> exit = Optional.ofNullable(watershed.downstream(cell.pos())).map(watershed::elevatedCell);
        List<RiverSpline> reaches = handler.buildSplines(cell, entries, exit, seed);
        double outflow = flow.getAsDouble();
        double inflow = entries.isEmpty() ? outflow : watershed.flow(entries.get(0).pos()).orElseThrow();
        return carve(reaches, inflow, outflow, chunk, CommonConfig.get().cellBlocks());
    }

    static List<CarveProposal> carve(List<RiverSpline> reaches, double inflow, double outflow, ChunkPos chunk, int cellBlocks) {
        List<Segment> nearby = nearbySegments(reaches, chunk, SAMPLES_PER_CELL_BLOCK * cellBlocks);
        if (nearby.isEmpty()) {
            return List.of();
        }

        List<CarveProposal> proposals = new ArrayList<>();
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                CarveProposal proposal = proposalAt(x, z, nearby, inflow, outflow);
                if (proposal != null) {
                    proposals.add(proposal);
                }
            }
        }
        return proposals;
    }

    private static List<Segment> nearbySegments(List<RiverSpline> reaches, ChunkPos chunk, int budget) {
        double[] lengths = new double[reaches.size()];
        double total = 0;
        for (int i = 0; i < reaches.size(); i++) {
            RiverSpline reach = reaches.get(i);
            Vec3 start = reach.pointAt(0);
            Vec3 end = reach.pointAt(1);
            lengths[i] = Math.hypot(end.x - start.x, end.z - start.z);
            total += lengths[i];
        }
        List<Segment> nearby = new ArrayList<>();
        double distance = 0;
        for (int i = 0; i < reaches.size(); i++) {
            if (lengths[i] == 0) {
                continue;
            }
            double from = distance / total;
            distance += lengths[i];
            double to = distance / total;
            addSegments(nearby, reaches.get(i), chunk, Math.max(1, Mth.ceil(budget * (to - from))), from, to);
        }
        return nearby;
    }

    private static void addSegments(List<Segment> nearby, RiverSpline spline, ChunkPos chunk, int steps, double from, double to) {
        Vec3 start = spline.pointAt(0);
        for (int i = 1; i <= steps; i++) {
            Vec3 end = spline.pointAt((double) i / steps);
            if (Math.min(start.x, end.x) <= chunk.getMaxBlockX() + 0.5 + MAX_HALF_WIDTH
                && Math.max(start.x, end.x) >= chunk.getMinBlockX() + 0.5 - MAX_HALF_WIDTH
                && Math.min(start.z, end.z) <= chunk.getMaxBlockZ() + 0.5 + MAX_HALF_WIDTH
                && Math.max(start.z, end.z) >= chunk.getMinBlockZ() + 0.5 - MAX_HALF_WIDTH) {
                nearby.add(new Segment(start, end, Mth.lerp((double) (i - 1) / steps, from, to),
                    Mth.lerp((double) i / steps, from, to)));
            }
            start = end;
        }
    }

    private static CarveProposal proposalAt(int x, int z, List<Segment> segments, double inflow, double outflow) {
        double bestDistanceSquared = MAX_HALF_WIDTH * MAX_HALF_WIDTH;
        double surfaceY = 0;
        double t = 0;
        for (Segment segment : segments) {
            double u = segment.project(x + 0.5, z + 0.5);
            double dx = Mth.lerp(u, segment.start().x, segment.end().x) - (x + 0.5);
            double dz = Mth.lerp(u, segment.start().z, segment.end().z) - (z + 0.5);
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                surfaceY = Mth.lerp(u, segment.start().y, segment.end().y);
                t = Mth.lerp(u, segment.startT(), segment.endT());
            }
        }

        double flow = Mth.lerp(t, inflow, outflow);
        double halfWidth = Math.max(MIN_HALF_WIDTH, 1 + 15 * (flow / (flow + 24)));
        int depth = Mth.floor(1 + 5 * (flow / (flow + 40)));
        double influence = 1 - bestDistanceSquared / (halfWidth * halfWidth);
        if (influence <= 0 || influence * depth <= 0.5) {
            return null;
        }
        return new CarveProposal(x, z, Math.floor(surfaceY) - depth * influence, influence);
    }

    private record Segment(Vec3 start, Vec3 end, double startT, double endT) {
        double project(double x, double z) {
            double dx = end.x - start.x;
            double dz = end.z - start.z;
            double lengthSquared = dx * dx + dz * dz;
            if (lengthSquared == 0) {
                return 0;
            }
            return Mth.clamp(((x - start.x) * dx + (z - start.z) * dz) / lengthSquared, 0, 1);
        }
    }
}
