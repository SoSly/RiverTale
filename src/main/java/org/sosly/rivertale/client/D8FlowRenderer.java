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
import org.sosly.rivertale.network.VisualizeD8Packet.D8RegionData;
import org.sosly.rivertale.worldgen.river.EdgeCrossing;
import org.sosly.rivertale.worldgen.river.FlowDirection;
import org.sosly.rivertale.worldgen.river.PathDirection;
import org.sosly.rivertale.worldgen.river.RiverRegionKey;

@Mod.EventBusSubscriber(modid = RiverTale.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class D8FlowRenderer {

    private static final double SEA_LEVEL = 63.0;
    private static final double ARROW_LENGTH = 3.5;
    private static final double ARROWHEAD_SIZE = 1.5;

    private static final double DIAG_COMPONENT = ARROW_LENGTH / Math.sqrt(2.0);
    private static final double DIAG_HEAD = ARROWHEAD_SIZE / Math.sqrt(2.0);

    private static int getRegionSize() {
        return RiverRegionKey.getRegionSize();
    }

    private static double getSubcellStep() {
        return getRegionSize() / 8.0;
    }

    private static final float[] COLOR_BORDER = {1.0f, 1.0f, 1.0f, 0.5f};
    private static final float[] COLOR_ARROW = {0.8f, 0.8f, 0.8f, 0.5f};
    private static final float[] COLOR_PRIMARY_OUTPUT = {0.6f, 0.0f, 0.8f, 0.8f};
    private static final float[] COLOR_SECONDARY_OUTPUT = {0.0f, 0.4f, 1.0f, 0.8f};
    private static final float[] COLOR_TERMINUS = {1.0f, 0.0f, 0.0f, 0.8f};
    private static final float[] COLOR_RIVER_PATH = {0.0f, 0.8f, 0.8f, 0.9f};
    private static final float[] COLOR_CONFLUENCE = {0.0f, 1.0f, 0.0f, 0.8f};
    private static final float[] COLOR_INPUT = {1.0f, 0.6f, 0.0f, 0.8f};

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        if (!ClientD8Cache.isEnabled()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();

        renderRegionBorders(poseStack, camPos, bufferBuilder);

        for (D8RegionData region : ClientD8Cache.getRegions()) {
            renderRegion(region, poseStack, camPos, bufferBuilder);
        }

        renderCrossRegionConnections(poseStack, camPos, bufferBuilder);
    }

    private static void renderRegionBorders(PoseStack poseStack, Vec3 camPos, BufferBuilder bufferBuilder) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(1.0f);

        bufferBuilder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        for (D8RegionData region : ClientD8Cache.getRegions()) {
            int regionSize = getRegionSize();
            double minX = region.regionX * regionSize;
            double maxX = minX + regionSize;
            double minZ = region.regionZ * regionSize;
            double maxZ = minZ + regionSize;

            Vec3 nw = new Vec3(minX, SEA_LEVEL, minZ);
            Vec3 ne = new Vec3(maxX, SEA_LEVEL, minZ);
            Vec3 sw = new Vec3(minX, SEA_LEVEL, maxZ);
            Vec3 se = new Vec3(maxX, SEA_LEVEL, maxZ);

            drawLine(poseStack, bufferBuilder, nw, ne, camPos, COLOR_BORDER);
            drawLine(poseStack, bufferBuilder, ne, se, camPos, COLOR_BORDER);
            drawLine(poseStack, bufferBuilder, se, sw, camPos, COLOR_BORDER);
            drawLine(poseStack, bufferBuilder, sw, nw, camPos, COLOR_BORDER);
        }

        Tesselator.getInstance().end();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void renderRegion(D8RegionData region, PoseStack poseStack, Vec3 camPos, BufferBuilder bufferBuilder) {
        int regionWorldX = region.regionX * getRegionSize();
        int regionWorldZ = region.regionZ * getRegionSize();

        renderFlowArrows(region, regionWorldX, regionWorldZ, poseStack, camPos, bufferBuilder);
        renderRiverPaths(region, regionWorldX, regionWorldZ, poseStack, camPos, bufferBuilder);
        renderTerminus(region, regionWorldX, regionWorldZ, poseStack, camPos, bufferBuilder);
        renderConfluences(region, regionWorldX, regionWorldZ, poseStack, camPos, bufferBuilder);
        renderCrossings(region, regionWorldX, regionWorldZ, poseStack, camPos, bufferBuilder);
    }

    private static void renderCrossRegionConnections(PoseStack poseStack, Vec3 camPos, BufferBuilder bufferBuilder) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(3.0f);

        bufferBuilder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        for (D8RegionData region : ClientD8Cache.getRegions()) {
            int regionWorldX = region.regionX * getRegionSize();
            int regionWorldZ = region.regionZ * getRegionSize();

            for (int dir = 0; dir < 4; dir++) {
                EdgeCrossing crossing = region.crossings[dir];
                if (crossing == null || crossing.direction() != EdgeCrossing.Direction.OUT) {
                    continue;
                }

                PathDirection pathDir = PathDirection.values()[dir];
                D8RegionData neighbor = findNeighborRegion(region.regionX, region.regionZ, pathDir);
                if (neighbor == null) {
                    continue;
                }

                PathDirection oppositeDir = getOppositeDirection(pathDir);
                EdgeCrossing neighborInput = neighbor.crossings[oppositeDir.ordinal()];
                if (neighborInput == null || neighborInput.direction() != EdgeCrossing.Direction.IN) {
                    continue;
                }

                int neighborWorldX = neighbor.regionX * getRegionSize();
                int neighborWorldZ = neighbor.regionZ * getRegionSize();

                Vec3 outputPos = subcellToWorld(new int[]{crossing.row(), crossing.col()}, regionWorldX, regionWorldZ);
                Vec3 inputPos = subcellToWorld(new int[]{neighborInput.row(), neighborInput.col()}, neighborWorldX, neighborWorldZ);

                drawLine(poseStack, bufferBuilder, outputPos, inputPos, camPos, COLOR_RIVER_PATH);
            }
        }

        Tesselator.getInstance().end();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static D8RegionData findNeighborRegion(int regionX, int regionZ, PathDirection dir) {
        int neighborX = regionX;
        int neighborZ = regionZ;

        switch (dir) {
            case NORTH -> neighborZ--;
            case SOUTH -> neighborZ++;
            case EAST -> neighborX++;
            case WEST -> neighborX--;
            default -> { }
        }

        for (D8RegionData region : ClientD8Cache.getRegions()) {
            if (region.regionX == neighborX && region.regionZ == neighborZ) {
                return region;
            }
        }
        return null;
    }

    private static PathDirection getOppositeDirection(PathDirection dir) {
        return switch (dir) {
            case NORTH -> PathDirection.SOUTH;
            case SOUTH -> PathDirection.NORTH;
            case EAST -> PathDirection.WEST;
            case WEST -> PathDirection.EAST;
            case NONE -> PathDirection.NONE;
        };
    }

    private static void renderCrossings(D8RegionData region, int regionWorldX, int regionWorldZ,
                                         PoseStack poseStack, Vec3 camPos, BufferBuilder bufferBuilder) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(2.0f);

        bufferBuilder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        double subcellStep = getSubcellStep();
        double margin = 1.0;

        for (int dir = 0; dir < 4; dir++) {
            EdgeCrossing crossing = region.crossings[dir];
            if (crossing == null) {
                continue;
            }
            if (crossing.direction() != EdgeCrossing.Direction.IN) {
                continue;
            }
            renderCrossingBox(regionWorldX, regionWorldZ, crossing, subcellStep, margin,
                poseStack, bufferBuilder, camPos, COLOR_INPUT);
        }

        for (int dir = 0; dir < 4; dir++) {
            EdgeCrossing crossing = region.crossings[dir];
            if (crossing == null) {
                continue;
            }
            if (crossing.direction() != EdgeCrossing.Direction.OUT || PathDirection.values()[dir] == region.primaryOutputDirection) {
                continue;
            }
            renderCrossingBox(regionWorldX, regionWorldZ, crossing, subcellStep, margin,
                poseStack, bufferBuilder, camPos, COLOR_SECONDARY_OUTPUT);
        }

        for (int dir = 0; dir < 4; dir++) {
            EdgeCrossing crossing = region.crossings[dir];
            if (crossing == null) {
                continue;
            }
            if (crossing.direction() != EdgeCrossing.Direction.OUT || PathDirection.values()[dir] != region.primaryOutputDirection) {
                continue;
            }
            renderCrossingBox(regionWorldX, regionWorldZ, crossing, subcellStep, margin,
                poseStack, bufferBuilder, camPos, COLOR_PRIMARY_OUTPUT);
        }

        Tesselator.getInstance().end();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void renderCrossingBox(int regionWorldX, int regionWorldZ, EdgeCrossing crossing,
                                           double subcellStep, double margin,
                                           PoseStack poseStack, BufferBuilder bufferBuilder,
                                           Vec3 camPos, float[] color) {
        int row = crossing.row();
        int col = crossing.col();

        double minX = regionWorldX + col * subcellStep + margin;
        double maxX = regionWorldX + (col + 1) * subcellStep - margin;
        double minZ = regionWorldZ + row * subcellStep + margin;
        double maxZ = regionWorldZ + (row + 1) * subcellStep - margin;

        Vec3 nw = new Vec3(minX, SEA_LEVEL, minZ);
        Vec3 ne = new Vec3(maxX, SEA_LEVEL, minZ);
        Vec3 sw = new Vec3(minX, SEA_LEVEL, maxZ);
        Vec3 se = new Vec3(maxX, SEA_LEVEL, maxZ);

        drawLine(poseStack, bufferBuilder, nw, ne, camPos, color);
        drawLine(poseStack, bufferBuilder, ne, se, camPos, color);
        drawLine(poseStack, bufferBuilder, se, sw, camPos, color);
        drawLine(poseStack, bufferBuilder, sw, nw, camPos, color);
    }

    private static void renderTerminus(D8RegionData region, int regionWorldX, int regionWorldZ,
                                        PoseStack poseStack, Vec3 camPos, BufferBuilder bufferBuilder) {
        if (region.terminusSubcells == null || region.terminusSubcells.length == 0) {
            return;
        }

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(2.0f);

        bufferBuilder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        double subcellStep = getSubcellStep();
        double margin = 1.0;

        for (int[] terminus : region.terminusSubcells) {
            int row = terminus[0];
            int col = terminus[1];

            double minX = regionWorldX + col * subcellStep + margin;
            double maxX = regionWorldX + (col + 1) * subcellStep - margin;
            double minZ = regionWorldZ + row * subcellStep + margin;
            double maxZ = regionWorldZ + (row + 1) * subcellStep - margin;

            Vec3 nw = new Vec3(minX, SEA_LEVEL, minZ);
            Vec3 ne = new Vec3(maxX, SEA_LEVEL, minZ);
            Vec3 sw = new Vec3(minX, SEA_LEVEL, maxZ);
            Vec3 se = new Vec3(maxX, SEA_LEVEL, maxZ);

            drawLine(poseStack, bufferBuilder, nw, ne, camPos, COLOR_TERMINUS);
            drawLine(poseStack, bufferBuilder, ne, se, camPos, COLOR_TERMINUS);
            drawLine(poseStack, bufferBuilder, se, sw, camPos, COLOR_TERMINUS);
            drawLine(poseStack, bufferBuilder, sw, nw, camPos, COLOR_TERMINUS);
        }

        Tesselator.getInstance().end();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void renderRiverPaths(D8RegionData region, int regionWorldX, int regionWorldZ,
                                          PoseStack poseStack, Vec3 camPos, BufferBuilder bufferBuilder) {
        if (region.riverPaths == null || region.riverPaths.isEmpty()) {
            return;
        }

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(3.0f);

        bufferBuilder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        for (java.util.List<int[]> path : region.riverPaths.values()) {
            for (int i = 0; i < path.size() - 1; i++) {
                Vec3 start = subcellToWorld(path.get(i), regionWorldX, regionWorldZ);
                Vec3 end = subcellToWorld(path.get(i + 1), regionWorldX, regionWorldZ);
                drawLine(poseStack, bufferBuilder, start, end, camPos, COLOR_RIVER_PATH);
            }
        }

        Tesselator.getInstance().end();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void renderConfluences(D8RegionData region, int regionWorldX, int regionWorldZ,
                                           PoseStack poseStack, Vec3 camPos, BufferBuilder bufferBuilder) {
        if (region.confluenceSubcells == null || region.confluenceSubcells.isEmpty()) {
            return;
        }

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(2.0f);

        bufferBuilder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        double subcellStep = getSubcellStep();
        double margin = 1.0;

        for (int[] confluence : region.confluenceSubcells) {
            int row = confluence[0];
            int col = confluence[1];

            double minX = regionWorldX + col * subcellStep + margin;
            double maxX = regionWorldX + (col + 1) * subcellStep - margin;
            double minZ = regionWorldZ + row * subcellStep + margin;
            double maxZ = regionWorldZ + (row + 1) * subcellStep - margin;

            Vec3 nw = new Vec3(minX, SEA_LEVEL, minZ);
            Vec3 ne = new Vec3(maxX, SEA_LEVEL, minZ);
            Vec3 sw = new Vec3(minX, SEA_LEVEL, maxZ);
            Vec3 se = new Vec3(maxX, SEA_LEVEL, maxZ);

            drawLine(poseStack, bufferBuilder, nw, ne, camPos, COLOR_CONFLUENCE);
            drawLine(poseStack, bufferBuilder, ne, se, camPos, COLOR_CONFLUENCE);
            drawLine(poseStack, bufferBuilder, se, sw, camPos, COLOR_CONFLUENCE);
            drawLine(poseStack, bufferBuilder, sw, nw, camPos, COLOR_CONFLUENCE);
        }

        Tesselator.getInstance().end();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void renderFlowArrows(D8RegionData region, int regionWorldX, int regionWorldZ,
                                        PoseStack poseStack, Vec3 camPos, BufferBuilder bufferBuilder) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(1.0f);

        bufferBuilder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                FlowDirection flowDirection = region.flowDirections[row][col];

                if (flowDirection == FlowDirection.SINK) {
                    continue;
                }

                Vec3 center = subcellToWorld(new int[]{row, col}, regionWorldX, regionWorldZ);
                Vec3 arrowEnd = calculateArrowEnd(center, flowDirection);

                drawLine(poseStack, bufferBuilder, center, arrowEnd, camPos, COLOR_ARROW);

                Vec3[] arrowhead = calculateArrowhead(arrowEnd, flowDirection);
                drawLine(poseStack, bufferBuilder, arrowEnd, arrowhead[0], camPos, COLOR_ARROW);
                drawLine(poseStack, bufferBuilder, arrowEnd, arrowhead[1], camPos, COLOR_ARROW);
            }
        }

        Tesselator.getInstance().end();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static Vec3 calculateArrowEnd(Vec3 center, FlowDirection flowDirection) {
        return switch (flowDirection) {
            case NORTH -> new Vec3(center.x, center.y, center.z - ARROW_LENGTH);
            case SOUTH -> new Vec3(center.x, center.y, center.z + ARROW_LENGTH);
            case EAST -> new Vec3(center.x + ARROW_LENGTH, center.y, center.z);
            case WEST -> new Vec3(center.x - ARROW_LENGTH, center.y, center.z);
            case NORTHEAST -> new Vec3(center.x + DIAG_COMPONENT, center.y, center.z - DIAG_COMPONENT);
            case NORTHWEST -> new Vec3(center.x - DIAG_COMPONENT, center.y, center.z - DIAG_COMPONENT);
            case SOUTHEAST -> new Vec3(center.x + DIAG_COMPONENT, center.y, center.z + DIAG_COMPONENT);
            case SOUTHWEST -> new Vec3(center.x - DIAG_COMPONENT, center.y, center.z + DIAG_COMPONENT);
            case SINK -> center;
        };
    }

    private static Vec3[] calculateArrowhead(Vec3 tip, FlowDirection flowDirection) {
        return switch (flowDirection) {
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
            case SINK -> new Vec3[]{tip, tip};
        };
    }

    private static Vec3 subcellToWorld(int[] subcell, int regionWorldX, int regionWorldZ) {
        int row = subcell[0];
        int col = subcell[1];

        double worldX = regionWorldX + (col * getSubcellStep()) + (getSubcellStep() / 2);
        double worldZ = regionWorldZ + (row * getSubcellStep()) + (getSubcellStep() / 2);

        return new Vec3(worldX, SEA_LEVEL, worldZ);
    }

    private static void drawLine(PoseStack poseStack, BufferBuilder bufferBuilder,
                                Vec3 start, Vec3 end, Vec3 camPos, float[] color) {
        double x1 = start.x - camPos.x;
        double y1 = start.y - camPos.y;
        double z1 = start.z - camPos.z;
        double x2 = end.x - camPos.x;
        double y2 = end.y - camPos.y;
        double z2 = end.z - camPos.z;

        bufferBuilder.vertex(poseStack.last().pose(), (float) x1, (float) y1, (float) z1)
                .color(color[0], color[1], color[2], color[3])
                .endVertex();

        bufferBuilder.vertex(poseStack.last().pose(), (float) x2, (float) y2, (float) z2)
                .color(color[0], color[1], color[2], color[3])
                .endVertex();
    }
}
