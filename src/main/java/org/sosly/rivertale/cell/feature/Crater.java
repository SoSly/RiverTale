package org.sosly.rivertale.cell.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.river.RiverSpline;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.terrain.CarveProposal;
import org.sosly.rivertale.world.RiverShaping;

public class Crater implements SourceFeature {
    private static final ResourceLocation CRATER_RANDOM = new ResourceLocation("rivertale", "crater");

    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        SourceConfig config = CommonConfig.get().sources();
        if (cell.averageEstimatedTerrainHeight() < config.craterMinHeight()) {
            return false;
        }
        if (cell.hasInflowingNeighbor()) {
            return false;
        }
        if (cell.averageTemperature() < config.craterMinTemperature()) {
            return false;
        }
        RandomSource random = RiverShaping.getRandomState()
            .getOrCreateRandomFactory(CRATER_RANDOM)
            .at(cell.pos().getMiddleBlockX(), 0, cell.pos().getMiddleBlockZ());
        return random.nextFloat() < config.craterChance();
    }

    @Override
    public List<CarveProposal> carveHeadwater(List<RiverSpline> paths, double flow, ChunkPos chunk, int size) {
        List<CarveProposal> proposals = new ArrayList<>(SourceFeature.super.carveHeadwater(paths, flow, chunk, size));
        RiverSpline overflow = paths.get(0);
        Vec3 lip = overflow.pointAt(1);
        Vec3 downstream = lip.subtract(overflow.pointAt(0)).normalize();
        double lengthRadius = 7.0 * size / 24;
        double widthRadius = 5.0 * size / 12;
        Vec3 center = lip.subtract(downstream.scale(lengthRadius));
        int depth = Math.max(1, size / 6);
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                double dx = x + 0.5 - center.x;
                double dz = z + 0.5 - center.z;
                double along = (dx * downstream.x + dz * downstream.z) / lengthRadius;
                double across = (dz * downstream.x - dx * downstream.z) / widthRadius;
                double profile = 1 - along * along - across * across;
                if (profile > 0) {
                    proposals.add(new CarveProposal(x, z, lip.y - Math.max(1, depth * profile), profile));
                }
            }
        }
        return proposals;
    }

    @Override
    public List<RiverSpline> buildSplines(Cell cell, List<Cell> entries, Optional<Cell> exit, long meanderSeed) {
        if (!entries.isEmpty() || exit.isEmpty()) {
            return SourceFeature.super.buildSplines(cell, entries, exit, meanderSeed);
        }
        SourceChannel channel = new SourceChannel(cell, exit.get(), meanderSeed);
        Vec3 rim = channel.origin(0.25, 0);
        Vec3 lip = channel.origin(0.375, 0);
        return List.of(t -> rim.lerp(lip, t), channel.toExit(lip));
    }
}
