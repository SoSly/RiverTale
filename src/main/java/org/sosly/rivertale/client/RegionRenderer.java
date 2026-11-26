package org.sosly.rivertale.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sosly.rivertale.RiverTale;
import org.sosly.rivertale.command.VisMode;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.core.BoundaryType;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.CellPos;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = RiverTale.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class RegionRenderer {
    private static Integer seaLevel;
    private static final double ARROWHEAD_SIZE = 1.5;

    private static final float[] COLOR_OCEAN_BOUNDARY = {1.0f, 0.5f, 0.0f, 1.0f};
    private static final float[] COLOR_BASIN_BOUNDARY = {1.0f, 0.0f, 0.0f, 1.0f};
    private static final float[] COLOR_PATH = {0.1f, 0.45f, 1.0f, 1.0f};

    public static void setSeaLevel(Integer value) {
        seaLevel = value;
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        seaLevel = null;
        ClientRegionCache.setEnabledModes(EnumSet.noneOf(VisMode.class));
    }

    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        seaLevel = null;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!ClientRegionCache.isEnabled() || seaLevel == null) {
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

        int cellBlocks = CommonConfig.get().cellBlocks();

        for (List<Vec3> path : cache.preview().paths()) {
            for (int i = 1; i < path.size(); i++) {
                drawLine(poseStack, buffer, path.get(i - 1), path.get(i), camPos, COLOR_PATH);
            }
        }
        Set<CellPos> renderedEdges = new HashSet<>();
        Set<CellPos> renderedOutlets = new HashSet<>();
        Set<CellPos> renderedSources = new HashSet<>();
        for (ClientRegionCache.Region region : cache.getRegions()) {
            renderMarkers(region, poseStack, buffer, camPos, renderedEdges, renderedOutlets);
            region.edges().forEach(edge -> {
                renderedSources.add(edge.from());
                renderedSources.add(edge.to());
            });
            region.outlets().forEach(outlet -> renderedSources.add(outlet.pos()));
        }
        for (ClientRegionCache.Region region : cache.getRegions()) {
            if (ClientRegionCache.isModeEnabled(VisMode.REGIONS)) {
                renderRegionBorder(region, poseStack, buffer, camPos, seaLevel);
            }
            if (ClientRegionCache.isModeEnabled(VisMode.CELLS)) {
                renderCellBorders(region, cellBlocks, poseStack, buffer, camPos);
            }
            if (ClientRegionCache.isModeEnabled(VisMode.BOUNDARIES)) {
                renderBoundaries(region, poseStack, buffer, camPos, seaLevel);
            }
            renderFlowlines(region, poseStack, buffer, camPos, renderedSources);
        }

        tesselator.end();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        if (ClientRegionCache.isModeEnabled(VisMode.CELLS)) {
            RiverCellLabels.render(cache.getRegions(), poseStack, buffer, camPos);
        }
    }

    private static void renderRegionBorder(ClientRegionCache.Region region,
                                           PoseStack poseStack, BufferBuilder buffer, Vec3 camPos, double seaLevel) {
        float[] color = region.type().color;
        int minX = region.pos().getMinBlockX();
        int minZ = region.pos().getMinBlockZ();
        int maxX = region.pos().getMaxBlockX();
        int maxZ = region.pos().getMaxBlockZ();

        Vec3 nw = new Vec3(minX, seaLevel, minZ);
        Vec3 ne = new Vec3(maxX, seaLevel, minZ);
        Vec3 se = new Vec3(maxX, seaLevel, maxZ);
        Vec3 sw = new Vec3(minX, seaLevel, maxZ);

        drawLine(poseStack, buffer, nw, ne, camPos, color);
        drawLine(poseStack, buffer, ne, se, camPos, color);
        drawLine(poseStack, buffer, se, sw, camPos, color);
        drawLine(poseStack, buffer, sw, nw, camPos, color);
    }

    private static void renderBoundaries(ClientRegionCache.Region region, PoseStack poseStack,
                                         BufferBuilder buffer, Vec3 camPos, double seaLevel) {
        for (Boundary boundary : region.boundaries()) {
            float[] color = boundary.type() == BoundaryType.OCEAN
                ? COLOR_OCEAN_BOUNDARY
                : COLOR_BASIN_BOUNDARY;

            int minX = boundary.land().getMinBlockX();
            int maxX = boundary.land().getMaxBlockX();
            int minZ = boundary.land().getMinBlockZ();
            int maxZ = boundary.land().getMaxBlockZ();
            FlowDirection dir = boundary.direction();

            Vec3 start;
            Vec3 end;
            if (dir == FlowDirection.NORTH) {
                start = new Vec3(minX, seaLevel, minZ);
                end = new Vec3(maxX, seaLevel, minZ);
            } else if (dir == FlowDirection.SOUTH) {
                start = new Vec3(minX, seaLevel, maxZ);
                end = new Vec3(maxX, seaLevel, maxZ);
            } else if (dir == FlowDirection.WEST) {
                start = new Vec3(minX, seaLevel, minZ);
                end = new Vec3(minX, seaLevel, maxZ);
            } else {
                start = new Vec3(maxX, seaLevel, minZ);
                end = new Vec3(maxX, seaLevel, maxZ);
            }
            drawLine(poseStack, buffer, start, end, camPos, color);
        }
    }

    private static void renderCellBorders(ClientRegionCache.Region region, int cellBlocks,
                                          PoseStack poseStack, BufferBuilder buffer, Vec3 camPos) {
        for (ClientRegionCache.Cell cell : region.cells()) {
            float[] color = cell.feature().type.color;
            double y = cell.y();

            double minX = cell.pos().getMinBlockX();
            double minZ = cell.pos().getMinBlockZ();
            double maxX = minX + cellBlocks;
            double maxZ = minZ + cellBlocks;

            Vec3 nw = new Vec3(minX, y, minZ);
            Vec3 ne = new Vec3(maxX, y, minZ);
            Vec3 se = new Vec3(maxX, y, maxZ);
            Vec3 sw = new Vec3(minX, y, maxZ);

            drawLine(poseStack, buffer, nw, ne, camPos, color);
            drawLine(poseStack, buffer, ne, se, camPos, color);
            drawLine(poseStack, buffer, se, sw, camPos, color);
            drawLine(poseStack, buffer, sw, nw, camPos, color);
        }
    }

    private static void renderFlowlines(ClientRegionCache.Region region, PoseStack poseStack,
                                         BufferBuilder buffer, Vec3 camPos, Set<CellPos> renderedSources) {
        for (ClientRegionCache.Flowline path : region.paths()) {
            if (path.points().isEmpty() || !renderedSources.add(path.points().get(0).pos())) {
                continue;
            }
            for (int i = 0; i < path.points().size(); i++) {
                ClientRegionCache.Point point = path.points().get(i);
                Vec3 start = new Vec3(point.pos().getMiddleBlockX(), point.y() + 2, point.pos().getMiddleBlockZ());
                if (i == path.points().size() - 1) {
                    drawLine(poseStack, buffer, start.add(-2, 0, 0), start.add(2, 0, 0), camPos, COLOR_PATH);
                    drawLine(poseStack, buffer, start.add(0, 0, -2), start.add(0, 0, 2), camPos, COLOR_PATH);
                    continue;
                }
                ClientRegionCache.Point next = path.points().get(i + 1);
                Vec3 end = new Vec3(next.pos().getMiddleBlockX(), next.y() + 2, next.pos().getMiddleBlockZ());
                drawLine(poseStack, buffer, start, end, camPos, COLOR_PATH);
                FlowDirection direction = FlowDirection.toward(point.pos(), next.pos());
                Vec3[] arrowhead = calculateArrowhead(end, direction);
                drawLine(poseStack, buffer, end, arrowhead[0], camPos, COLOR_PATH);
                drawLine(poseStack, buffer, end, arrowhead[1], camPos, COLOR_PATH);
            }
        }
    }

    private static void renderMarkers(ClientRegionCache.Region region, PoseStack poseStack,
                                     BufferBuilder buffer, Vec3 camPos, Set<CellPos> renderedEdges, Set<CellPos> renderedOutlets) {
        for (ClientRegionCache.Edge edge : region.edges()) {
            if (!renderedEdges.add(edge.from())) {
                continue;
            }
            Vec3 end = new Vec3((edge.from().getMiddleBlockX() + (double) edge.to().getMiddleBlockX()) / 2,
                edge.toY(), (edge.from().getMiddleBlockZ() + (double) edge.to().getMiddleBlockZ()) / 2);
            Vec3[] arrowhead = calculateArrowhead(end, FlowDirection.toward(edge.from(), edge.to()));
            drawLine(poseStack, buffer, end, arrowhead[0], camPos, COLOR_PATH);
            drawLine(poseStack, buffer, end, arrowhead[1], camPos, COLOR_PATH);
        }
        for (ClientRegionCache.Point outlet : region.outlets()) {
            if (!renderedOutlets.add(outlet.pos())) {
                continue;
            }
            Vec3 point = new Vec3(outlet.pos().getMiddleBlockX(), outlet.y(), outlet.pos().getMiddleBlockZ());
            drawLine(poseStack, buffer, point.add(-2, 0, 0), point.add(2, 0, 0), camPos, COLOR_PATH);
            drawLine(poseStack, buffer, point.add(0, 0, -2), point.add(0, 0, 2), camPos, COLOR_PATH);
        }
    }

    private static Vec3[] calculateArrowhead(Vec3 tip, FlowDirection dir) {
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
