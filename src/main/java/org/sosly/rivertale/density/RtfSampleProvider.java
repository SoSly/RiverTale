package org.sosly.rivertale.density;

import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.heightmap.Heightmap;
import raccoonman.reterraforged.world.worldgen.cell.heightmap.Levels;
import raccoonman.reterraforged.world.worldgen.densityfunction.CellSampler;

public class RtfSampleProvider implements SampleProvider {
    private final Heightmap heightmap;
    private final Levels levels;

    public RtfSampleProvider(GeneratorContext context) {
        this.heightmap = context.generator.getHeightmap();
        this.levels = context.levels;
    }

    @Override
    public Sample sampleCore(ChunkPos pos) {
        Timer.Record timer = Store.getTimer(RtfSampleProvider.class, "sampleCore").start();
        try {
            Cell cell = new Cell();
            int x = pos.getMiddleBlockX();
            int z = pos.getMiddleBlockZ();
            heightmap.applyTerrain(cell, x, z);
            heightmap.applyClimate(cell, x, z, true);

            return new Sample(
                pos,
                CellSampler.Field.CONTINENT.read(cell, heightmap),
                levels.scale(cell.height),
                cell.terrain.isDeepOcean() || cell.terrain.isShallowOcean(),
                (double) cell.erosion,
                (double) cell.weirdness,
                (double) cell.temperature,
                (double) cell.moisture
            );
        } finally {
            timer.stop();
        }
    }

    @Override
    public double sample(DensityField field, ChunkPos pos) {
        Sample sample = sampleCore(pos);
        return switch (field) {
            case CONTINENTS -> sample.continents();
            case EROSION -> sample.erosion();
            case RIDGES -> sample.ridges();
            case TEMPERATURE -> sample.temperature();
            case VEGETATION -> sample.vegetation();
        };
    }
}
