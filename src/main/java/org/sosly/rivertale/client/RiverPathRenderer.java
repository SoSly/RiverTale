package org.sosly.rivertale.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sosly.rivertale.RiverTale;
import org.sosly.rivertale.network.VisualizeCellsPacket.CellData;
import org.sosly.rivertale.worldgen.river.CellClassification;
import org.sosly.rivertale.worldgen.river.FlowDirection;
import org.sosly.rivertale.worldgen.river.RiverCellKey;

import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = RiverTale.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class RiverPathRenderer {

    private static final double SEA_LEVEL = 63.0;

    private static int getCellSize() {
        return RiverCellKey.getCellSize();
    }

    private static double getSubcellStep() {
        return getCellSize() / 7.0;
    }

    private static final float[] COLOR_SOURCE = {0.0f, 0.5f, 1.0f, 0.8f};
    private static final float[] COLOR_TERMINUS = {1.0f, 0.2f, 0.2f, 0.8f};
    private static final float[] COLOR_BOUNDARY = {0.7f, 0.3f, 0.9f, 0.8f};
    private static final float[] COLOR_CONFLUENCE = {0.2f, 0.9f, 0.3f, 0.8f};
    private static final float[] COLOR_LINE = {0.3f, 0.7f, 1.0f, 0.6f};

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        if (!ClientCellCache.isEnabled()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();

        for (CellData cell : ClientCellCache.getCells()) {
            renderCell(cell, poseStack, camPos, bufferBuilder);
        }

        renderCrossBoundaryConnections(poseStack, camPos, bufferBuilder);
    }

    private static void renderCrossBoundaryConnections(PoseStack poseStack, Vec3 camPos, BufferBuilder bufferBuilder) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(2.0f);

        bufferBuilder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        for (CellData cell : ClientCellCache.getCells()) {
            int cellWorldX = cell.cellX * getCellSize();
            int cellWorldZ = cell.cellZ * getCellSize();

            for (List<int[]> path : cell.riverPaths.values()) {
                if (path.isEmpty()) {
                    continue;
                }

                int[] startPoint = path.get(0);
                FlowDirection startEdge = getEdgeDirection(startPoint);
                if (startEdge != null) {
                    CellData neighbor = getNeighborCell(cell, startEdge);
                    if (neighbor != null) {
                        Vec3 thisPos = subcellToWorld(startPoint, cellWorldX, cellWorldZ);
                        int[] neighborPoint = getOppositeEdgePoint(startPoint, startEdge);
                        int neighborWorldX = neighbor.cellX * getCellSize();
                        int neighborWorldZ = neighbor.cellZ * getCellSize();
                        Vec3 neighborPos = subcellToWorld(neighborPoint, neighborWorldX, neighborWorldZ);
                        drawLine(poseStack, bufferBuilder, thisPos, neighborPos, camPos, COLOR_BOUNDARY);
                    }
                }

                int[] endPoint = path.get(path.size() - 1);
                FlowDirection endEdge = getEdgeDirection(endPoint);
                if (endEdge != null) {
                    CellData neighbor = getNeighborCell(cell, endEdge);
                    if (neighbor != null) {
                        Vec3 thisPos = subcellToWorld(endPoint, cellWorldX, cellWorldZ);
                        int[] neighborPoint = getOppositeEdgePoint(endPoint, endEdge);
                        int neighborWorldX = neighbor.cellX * getCellSize();
                        int neighborWorldZ = neighbor.cellZ * getCellSize();
                        Vec3 neighborPos = subcellToWorld(neighborPoint, neighborWorldX, neighborWorldZ);
                        drawLine(poseStack, bufferBuilder, thisPos, neighborPos, camPos, COLOR_BOUNDARY);
                    }
                }
            }
        }

        Tesselator.getInstance().end();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static FlowDirection getEdgeDirection(int[] point) {
        int row = point[0];
        int col = point[1];

        if (row == 0) {
            return FlowDirection.NORTH;
        }
        if (row == 6) {
            return FlowDirection.SOUTH;
        }
        if (col == 0) {
            return FlowDirection.WEST;
        }
        if (col == 6) {
            return FlowDirection.EAST;
        }
        return null;
    }

    private static CellData getNeighborCell(CellData cell, FlowDirection direction) {
        int neighborX = cell.cellX + direction.dx();
        int neighborZ = cell.cellZ + direction.dz();
        return ClientCellCache.getCell(neighborX, neighborZ);
    }

    private static int[] getOppositeEdgePoint(int[] point, FlowDirection edge) {
        return switch (edge) {
            case NORTH -> new int[]{6, point[1]};
            case SOUTH -> new int[]{0, point[1]};
            case EAST -> new int[]{point[0], 0};
            case WEST -> new int[]{point[0], 6};
            default -> point;
        };
    }

    private static void renderCell(CellData cell, PoseStack poseStack, Vec3 camPos, BufferBuilder bufferBuilder) {
        int cellWorldX = cell.cellX * getCellSize();
        int cellWorldZ = cell.cellZ * getCellSize();

        java.util.Set<String> confluencePoints = findConfluencePoints(cell);

        for (Map.Entry<FlowDirection, List<int[]>> entry : cell.riverPaths.entrySet()) {
            List<int[]> path = entry.getValue();
            if (path.isEmpty()) {
                continue;
            }

            renderPath(path, cellWorldX, cellWorldZ, poseStack, camPos, bufferBuilder);
            renderPathMarkers(path, cellWorldX, cellWorldZ, cell, poseStack, camPos, bufferBuilder);
        }

        for (String confluenceKey : confluencePoints) {
            String[] parts = confluenceKey.split(",");
            int row = Integer.parseInt(parts[0]);
            int col = Integer.parseInt(parts[1]);
            Vec3 pos = subcellToWorld(new int[]{row, col}, cellWorldX, cellWorldZ);
            renderBox(poseStack, bufferBuilder, pos, camPos, COLOR_CONFLUENCE);
        }
    }

    private static java.util.Set<String> findConfluencePoints(CellData cell) {
        java.util.Set<String> confluences = new java.util.HashSet<>();
        List<List<int[]>> paths = new java.util.ArrayList<>(cell.riverPaths.values());

        for (int i = 0; i < paths.size(); i++) {
            for (int j = i + 1; j < paths.size(); j++) {
                String mergePoint = findFirstIntersection(paths.get(i), paths.get(j));
                if (mergePoint != null) {
                    confluences.add(mergePoint);
                }
            }
        }

        return confluences;
    }

    private static String findFirstIntersection(List<int[]> pathA, List<int[]> pathB) {
        java.util.Set<String> pointsInB = new java.util.HashSet<>();
        for (int[] point : pathB) {
            pointsInB.add(point[0] + "," + point[1]);
        }

        for (int[] point : pathA) {
            String key = point[0] + "," + point[1];
            int row = point[0];
            int col = point[1];
            boolean isEdge = row == 0 || row == 6 || col == 0 || col == 6;
            if (!isEdge && pointsInB.contains(key)) {
                return key;
            }
        }

        return null;
    }

    private static void renderPath(List<int[]> path, int cellWorldX, int cellWorldZ,
                                   PoseStack poseStack, Vec3 camPos, BufferBuilder bufferBuilder) {
        if (path.size() < 2) {
            return;
        }

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(2.0f);

        bufferBuilder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < path.size() - 1; i++) {
            int[] current = path.get(i);
            int[] next = path.get(i + 1);

            Vec3 startPos = subcellToWorld(current, cellWorldX, cellWorldZ);
            Vec3 endPos = subcellToWorld(next, cellWorldX, cellWorldZ);

            drawLine(poseStack, bufferBuilder, startPos, endPos, camPos, COLOR_LINE);
        }

        Tesselator.getInstance().end();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void renderPathMarkers(List<int[]> path, int cellWorldX, int cellWorldZ, CellData cell,
                                         PoseStack poseStack, Vec3 camPos, BufferBuilder bufferBuilder) {
        if (path.isEmpty()) {
            return;
        }

        int[] startPoint = path.get(0);
        int[] endPoint = path.get(path.size() - 1);

        float[] startColor = getStartMarkerColor(startPoint);
        if (startColor != null) {
            Vec3 startPos = subcellToWorld(startPoint, cellWorldX, cellWorldZ);
            renderBox(poseStack, bufferBuilder, startPos, camPos, startColor);
        }

        float[] endColor = getEndMarkerColor(endPoint, cell);
        if (endColor != null) {
            Vec3 endPos = subcellToWorld(endPoint, cellWorldX, cellWorldZ);
            renderBox(poseStack, bufferBuilder, endPos, camPos, endColor);
        }
    }

    private static float[] getStartMarkerColor(int[] point) {
        int row = point[0];
        int col = point[1];

        if (row == 0 || row == 6 || col == 0 || col == 6) {
            return COLOR_BOUNDARY;
        }

        return COLOR_SOURCE;
    }

    private static float[] getEndMarkerColor(int[] point, CellData cell) {
        int row = point[0];
        int col = point[1];

        boolean isTerminus = cell.isBasin ||
                cell.classification == CellClassification.COASTAL ||
                cell.classification == CellClassification.LAKESHORE ||
                cell.classification == CellClassification.OCEAN ||
                cell.classification == CellClassification.LAKE;

        if (isTerminus) {
            return COLOR_TERMINUS;
        }

        if (row == 0 || row == 6 || col == 0 || col == 6) {
            return COLOR_BOUNDARY;
        }

        return null;
    }

    private static Vec3 subcellToWorld(int[] subcell, int cellWorldX, int cellWorldZ) {
        int row = subcell[0];
        int col = subcell[1];

        double worldX = cellWorldX + (col * getSubcellStep()) + (getSubcellStep() / 2);
        double worldZ = cellWorldZ + (row * getSubcellStep()) + (getSubcellStep() / 2);

        return new Vec3(worldX, SEA_LEVEL, worldZ);
    }

    private static void renderBox(PoseStack poseStack, BufferBuilder bufferBuilder,
                                  Vec3 pos, Vec3 camPos, float[] color) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        double minX = pos.x - camPos.x - 2.0;
        double minY = pos.y - camPos.y;
        double minZ = pos.z - camPos.z - 2.0;
        double maxX = pos.x - camPos.x + 2.0;
        double maxY = pos.y - camPos.y + 1.0;
        double maxZ = pos.z - camPos.z + 2.0;

        LevelRenderer.addChainedFilledBoxVertices(
                poseStack, bufferBuilder,
                minX, minY, minZ,
                maxX, maxY, maxZ,
                color[0], color[1], color[2], color[3]
        );

        Tesselator.getInstance().end();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
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
