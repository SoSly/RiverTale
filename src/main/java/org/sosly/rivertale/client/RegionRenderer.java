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
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.List;
import org.sosly.rivertale.RiverTale;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.terrain.OceanBoundary;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = RiverTale.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class RegionRenderer {
    private static final double Y = 63.0;
    private static final double ARROW_LENGTH = 3.5;
    private static final double ARROWHEAD_SIZE = 1.5;
    private static final double DIAG_COMPONENT = ARROW_LENGTH / Math.sqrt(2.0);

    private static final float[] COLOR_BOUNDARY = {1.0f, 0.5f, 0.0f, 1.0f};
    private static final float[] COLOR_PATH = {0.0f, 0.0f, 1.0f, 1.0f};

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!ClientRegionCache.isEnabled()) {
            return;
        }

        ClientRegionCache cache = ClientRegionCache.get();
        if (cache.getRegions().isEmpty()) {
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

        int regionSize = CommonConfig.get().regionSize();
        int cellsPerRegion = CommonConfig.get().cellsPerRegion();

        for (ClientRegionCache.Region region : cache.getRegions()) {
            renderRegionBorder(region, regionSize, poseStack, buffer, camPos);
            renderCellBorders(region, regionSize, cellsPerRegion, poseStack, buffer, camPos);
            renderOceanBoundaries(region, poseStack, buffer, camPos);
            renderFlowArrows(region, regionSize, cellsPerRegion, poseStack, buffer, camPos);
            renderPaths(region, poseStack, buffer, camPos);
        }

        tesselator.end();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void renderRegionBorder(ClientRegionCache.Region region, int regionSize,
                                           PoseStack poseStack, BufferBuilder buffer, Vec3 camPos) {
        float[] color = region.type().color;
        int minX = region.pos().getMinBlockX() + 1;
        int minZ = region.pos().getMinBlockZ() + 1;
        int maxX = minX + regionSize - 1;
        int maxZ = minZ + regionSize - 1;

        Vec3 nw = new Vec3(minX, Y, minZ);
        Vec3 ne = new Vec3(maxX, Y, minZ);
        Vec3 se = new Vec3(maxX, Y, maxZ);
        Vec3 sw = new Vec3(minX, Y, maxZ);

        drawLine(poseStack, buffer, nw, ne, camPos, color);
        drawLine(poseStack, buffer, ne, se, camPos, color);
        drawLine(poseStack, buffer, se, sw, camPos, color);
        drawLine(poseStack, buffer, sw, nw, camPos, color);
    }

    private static void renderOceanBoundaries(ClientRegionCache.Region region, PoseStack poseStack,
                                              BufferBuilder buffer, Vec3 camPos) {
        for (OceanBoundary boundary : region.boundaries()) {
            int landChunkX = boundary.land().x;
            int landChunkZ = boundary.land().z;
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
    }

    private static void renderCellBorders(ClientRegionCache.Region region, int regionSize, int cellsPerRegion,
                                          PoseStack poseStack, BufferBuilder buffer, Vec3 camPos) {
        int baseX = region.pos().getMinBlockX();
        int baseZ = region.pos().getMinBlockZ();
        double cellSize = (double) regionSize / cellsPerRegion;

        for (ClientRegionCache.Cell cell : region.cells()) {
            int localX = cell.pos().x() - region.pos().getMinCell().x();
            int localZ = cell.pos().z() - region.pos().getMinCell().z();

            float[] color = cell.feature().type.color;

            double minX = baseX + localX * cellSize;
            double minZ = baseZ + localZ * cellSize;
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

    private static void renderFlowArrows(ClientRegionCache.Region region, int regionSize, int cellsPerRegion,
                                         PoseStack poseStack, BufferBuilder buffer, Vec3 camPos) {
        int baseX = region.pos().getMinBlockX();
        int baseZ = region.pos().getMinBlockZ();
        double cellSize = (double) regionSize / cellsPerRegion;

        for (ClientRegionCache.Cell cell : region.cells()) {
            Direction dir = cell.flow();
            if (dir == Direction.NONE) {
                continue;
            }

            int localX = cell.pos().x() - region.pos().getMinCell().x();
            int localZ = cell.pos().z() - region.pos().getMinCell().z();

            double centerX = baseX + (localX + 0.5) * cellSize;
            double centerZ = baseZ + (localZ + 0.5) * cellSize;
            Vec3 center = new Vec3(centerX, Y, centerZ);

            float[] arrowColor = cell.feature().color;

            Vec3 arrowEnd = calculateArrowEnd(center, dir);
            drawLine(poseStack, buffer, center, arrowEnd, camPos, arrowColor);

            Vec3[] arrowhead = calculateArrowhead(arrowEnd, dir);
            drawLine(poseStack, buffer, arrowEnd, arrowhead[0], camPos, arrowColor);
            drawLine(poseStack, buffer, arrowEnd, arrowhead[1], camPos, arrowColor);
        }
    }

    private static void renderPaths(ClientRegionCache.Region region, PoseStack poseStack,
                                     BufferBuilder buffer, Vec3 camPos) {
        for (List<CellPos> path : region.paths()) {
            for (int i = 0; i < path.size() - 1; i++) {
                CellPos current = path.get(i);
                CellPos next = path.get(i + 1);

                double currentX = current.getMiddleBlockX();
                double currentZ = current.getMiddleBlockZ();
                double nextX = next.getMiddleBlockX();
                double nextZ = next.getMiddleBlockZ();

                Vec3 start = new Vec3(currentX, Y, currentZ);
                Vec3 end = new Vec3(nextX, Y, nextZ);

                drawLine(poseStack, buffer, start, end, camPos, COLOR_PATH);
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

    private static void drawLine(PoseStack poseStack, BufferBuilder buffer,
                                  Vec3 start, Vec3 end, Vec3 camPos, float[] color) {
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
