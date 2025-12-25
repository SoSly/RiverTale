package org.sosly.rivertale.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sosly.rivertale.RiverTale;
import org.sosly.rivertale.cell.Grid;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.poc.vis.Region;
import org.sosly.rivertale.poc.vis.RegionType;
import org.sosly.rivertale.poc.vis.OceanBoundary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = RiverTale.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class RegionRenderer {
    private static final double Y = 63.0;
    private static final double ARROW_LENGTH = 3.5;
    private static final double ARROWHEAD_SIZE = 1.5;
    private static final double DIAG_COMPONENT = ARROW_LENGTH / Math.sqrt(2.0);

    private static final float[] COLOR_BOUNDARY = {0.0f, 1.0f, 1.0f, 1.0f};
    private static final float[] COLOR_OCEAN = {0.0f, 0.3f, 1.0f, 0.8f};
    private static final float[] COLOR_COASTAL = {0.0f, 1.0f, 1.0f, 0.8f};
    private static final float[] COLOR_FLUVIAL = {0.0f, 1.0f, 0.3f, 0.8f};
    private static final float[] COLOR_INLAND = {0.5f, 0.5f, 0.5f, 0.5f};
    private static final float[] COLOR_FLOW_ARROW = {0.6f, 0.6f, 0.6f, 0.6f};

    private static List<Region> regions = new ArrayList<>();
    private static Map<Long, Grid> flows = new HashMap<>();
    private static int regionSize;
    private static int cellsPerRegion;

    public static void setRegions(List<Region> r, Map<Long, Grid> f, int size, int cells) {
        regions = r;
        flows = f;
        regionSize = size;
        cellsPerRegion = cells;
    }

    public static void clear() {
        regions.clear();
        flows.clear();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (regions.isEmpty()) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();

        PoseStack poseStack = event.getPoseStack();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(3.0f);

        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        for (Region region : regions) {
            float[] borderColor = getBorderColor(region.type());

            int minX = region.regionX() * regionSize + 1;
            int minZ = region.regionZ() * regionSize + 1;
            int maxX = minX + regionSize - 1;
            int maxZ = minZ + regionSize - 1;

            Vec3 nw = new Vec3(minX, Y, minZ);
            Vec3 ne = new Vec3(maxX, Y, minZ);
            Vec3 se = new Vec3(maxX, Y, maxZ);
            Vec3 sw = new Vec3(minX, Y, maxZ);

            drawLine(poseStack, buffer, nw, ne, camPos, borderColor);
            drawLine(poseStack, buffer, ne, se, camPos, borderColor);
            drawLine(poseStack, buffer, se, sw, camPos, borderColor);
            drawLine(poseStack, buffer, sw, nw, camPos, borderColor);

            for (OceanBoundary boundary : region.boundaries()) {
                int landChunkX = boundary.landChunkX();
                int landChunkZ = boundary.landChunkZ();

                Direction bearing = boundary.bearing();
                Vec3 start;
                Vec3 end;

                int chunkMinX = landChunkX << 4;
                int chunkMaxX = (landChunkX + 1) << 4;
                int chunkMinZ = landChunkZ << 4;
                int chunkMaxZ = (landChunkZ + 1) << 4;

                switch (bearing) {
                    case NORTH -> {
                        start = new Vec3(chunkMinX, Y, chunkMinZ);
                        end = new Vec3(chunkMaxX, Y, chunkMinZ);
                    }
                    case SOUTH -> {
                        start = new Vec3(chunkMinX, Y, chunkMaxZ);
                        end = new Vec3(chunkMaxX, Y, chunkMaxZ);
                    }
                    case EAST -> {
                        start = new Vec3(chunkMaxX, Y, chunkMinZ);
                        end = new Vec3(chunkMaxX, Y, chunkMaxZ);
                    }
                    case WEST -> {
                        start = new Vec3(chunkMinX, Y, chunkMinZ);
                        end = new Vec3(chunkMinX, Y, chunkMaxZ);
                    }
                    default -> {
                        continue;
                    }
                }

                drawLine(poseStack, buffer, start, end, camPos, COLOR_BOUNDARY);
            }

            long key = regionKey(region.regionX(), region.regionZ());
            Grid flow = flows.get(key);
            if (flow != null) {
                renderCellBorders(region, flow, poseStack, buffer, camPos);
                renderFlowArrows(region, flow, poseStack, buffer, camPos);
            }
        }

        tesselator.end();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void renderCellBorders(Region region, Grid flow, PoseStack poseStack,
                                          BufferBuilder buffer, Vec3 camPos) {
        int baseX = region.regionX() * regionSize;
        int baseZ = region.regionZ() * regionSize;
        double cellSize = (double) regionSize / cellsPerRegion;

        for (int row = 0; row < cellsPerRegion; row++) {
            for (int col = 0; col < cellsPerRegion; col++) {
                float[] color = flow.get(row, col).feature().TYPE.COLOR;

                double minX = baseX + col * cellSize;
                double minZ = baseZ + row * cellSize;
                double maxX = minX + cellSize;
                double maxZ = minZ + cellSize;

                Vec3 nw = new Vec3(minX, Y, minZ);
                Vec3 ne = new Vec3(maxX, Y, minZ);
                Vec3 se = new Vec3(maxX, Y, maxZ);
                Vec3 sw = new Vec3(minX, Y, maxZ);

                drawLine(poseStack, buffer, nw, ne, camPos, color);
                drawLine(poseStack, buffer, ne, se, camPos, color);
                drawLine(poseStack, buffer, se, sw, camPos, color);
                drawLine(poseStack, buffer, sw, nw, camPos, color);
            }
        }
    }

    private static void renderFlowArrows(Region region, Grid flow, PoseStack poseStack,
                                         BufferBuilder buffer, Vec3 camPos) {
        int baseX = region.regionX() * regionSize;
        int baseZ = region.regionZ() * regionSize;
        double cellSize = (double) regionSize / cellsPerRegion;

        for (int row = 0; row < cellsPerRegion; row++) {
            for (int col = 0; col < cellsPerRegion; col++) {
                Direction dir = flow.getFlowAt(row, col);
                if (dir == Direction.NONE) {
                    continue;
                }

                double centerX = baseX + (col + 0.5) * cellSize;
                double centerZ = baseZ + (row + 0.5) * cellSize;
                Vec3 center = new Vec3(centerX, Y, centerZ);

                Vec3 arrowEnd = calculateArrowEnd(center, dir);
                drawLine(poseStack, buffer, center, arrowEnd, camPos, COLOR_FLOW_ARROW);

                Vec3[] arrowhead = calculateArrowhead(arrowEnd, dir);
                drawLine(poseStack, buffer, arrowEnd, arrowhead[0], camPos, COLOR_FLOW_ARROW);
                drawLine(poseStack, buffer, arrowEnd, arrowhead[1], camPos, COLOR_FLOW_ARROW);
            }
        }
    }

    private static Vec3 calculateArrowEnd(Vec3 center, Direction dir) {
        return switch (dir) {
            case NORTH -> new Vec3(center.x, center.y, center.z - ARROW_LENGTH);
            case SOUTH -> new Vec3(center.x, center.y, center.z + ARROW_LENGTH);
            case EAST -> new Vec3(center.x + ARROW_LENGTH, center.y, center.z);
            case WEST -> new Vec3(center.x - ARROW_LENGTH, center.y, center.z);
            case NORTHEAST -> new Vec3(center.x + DIAG_COMPONENT, center.y, center.z - DIAG_COMPONENT);
            case NORTHWEST -> new Vec3(center.x - DIAG_COMPONENT, center.y, center.z - DIAG_COMPONENT);
            case SOUTHEAST -> new Vec3(center.x + DIAG_COMPONENT, center.y, center.z + DIAG_COMPONENT);
            case SOUTHWEST -> new Vec3(center.x - DIAG_COMPONENT, center.y, center.z + DIAG_COMPONENT);
            case NONE -> center;
        };
    }

    private static Vec3[] calculateArrowhead(Vec3 tip, Direction dir) {
        return switch (dir) {
            case NORTH -> new Vec3[]{
                new Vec3(tip.x - ARROWHEAD_SIZE, tip.y, tip.z + ARROWHEAD_SIZE),
                new Vec3(tip.x + ARROWHEAD_SIZE, tip.y, tip.z + ARROWHEAD_SIZE)
            };
            case SOUTH -> new Vec3[]{
                new Vec3(tip.x - ARROWHEAD_SIZE, tip.y, tip.z - ARROWHEAD_SIZE),
                new Vec3(tip.x + ARROWHEAD_SIZE, tip.y, tip.z - ARROWHEAD_SIZE)
            };
            case EAST -> new Vec3[]{
                new Vec3(tip.x - ARROWHEAD_SIZE, tip.y, tip.z - ARROWHEAD_SIZE),
                new Vec3(tip.x - ARROWHEAD_SIZE, tip.y, tip.z + ARROWHEAD_SIZE)
            };
            case WEST -> new Vec3[]{
                new Vec3(tip.x + ARROWHEAD_SIZE, tip.y, tip.z - ARROWHEAD_SIZE),
                new Vec3(tip.x + ARROWHEAD_SIZE, tip.y, tip.z + ARROWHEAD_SIZE)
            };
            case NORTHEAST -> new Vec3[]{
                new Vec3(tip.x - ARROWHEAD_SIZE, tip.y, tip.z),
                new Vec3(tip.x, tip.y, tip.z + ARROWHEAD_SIZE)
            };
            case NORTHWEST -> new Vec3[]{
                new Vec3(tip.x + ARROWHEAD_SIZE, tip.y, tip.z),
                new Vec3(tip.x, tip.y, tip.z + ARROWHEAD_SIZE)
            };
            case SOUTHEAST -> new Vec3[]{
                new Vec3(tip.x - ARROWHEAD_SIZE, tip.y, tip.z),
                new Vec3(tip.x, tip.y, tip.z - ARROWHEAD_SIZE)
            };
            case SOUTHWEST -> new Vec3[]{
                new Vec3(tip.x + ARROWHEAD_SIZE, tip.y, tip.z),
                new Vec3(tip.x, tip.y, tip.z - ARROWHEAD_SIZE)
            };
            case NONE -> new Vec3[]{tip, tip};
        };
    }

    private static long regionKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static float[] getBorderColor(RegionType type) {
        return switch (type) {
            case OCEAN -> COLOR_OCEAN;
            case COASTAL -> COLOR_COASTAL;
            case FLUVIAL -> COLOR_FLUVIAL;
            case INLAND -> COLOR_INLAND;
        };
    }

    private static void drawLine(PoseStack poseStack, BufferBuilder buffer, Vec3 start, Vec3 end, Vec3 camPos, float[] color) {
        float x1 = (float) (start.x - camPos.x);
        float y1 = (float) (start.y - camPos.y);
        float z1 = (float) (start.z - camPos.z);
        float x2 = (float) (end.x - camPos.x);
        float y2 = (float) (end.y - camPos.y);
        float z2 = (float) (end.z - camPos.z);

        buffer.vertex(poseStack.last().pose(), x1, y1, z1)
            .color(color[0], color[1], color[2], color[3])
            .endVertex();
        buffer.vertex(poseStack.last().pose(), x2, y2, z2)
            .color(color[0], color[1], color[2], color[3])
            .endVertex();
    }
}
