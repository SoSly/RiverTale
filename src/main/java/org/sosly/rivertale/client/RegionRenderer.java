package org.sosly.rivertale.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.EnumSet;
import java.util.HashSet;
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
    private static final double ARROW_LENGTH = 3.5;
    private static final double ARROWHEAD_SIZE = 1.5;
    private static final double DIAG_COMPONENT = ARROW_LENGTH / Math.sqrt(2.0);

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

        Set<CellPos> renderedSources = new HashSet<>();
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

    private static void renderFlowArrows(ClientRegionCache.Region region, int cellBlocks,
                                         PoseStack poseStack, BufferBuilder buffer, Vec3 camPos) {
        for (ClientRegionCache.Cell cell : region.cells()) {
            FlowDirection dir = cell.flow();
            if (dir == FlowDirection.NONE) {
                continue;
            }

            double centerX = cell.pos().getMinBlockX() + cellBlocks / 2.0;
            double centerZ = cell.pos().getMinBlockZ() + cellBlocks / 2.0;
            Vec3 center = new Vec3(centerX, cell.y(), centerZ);

            float[] arrowColor = cell.feature().color;

            Vec3 arrowEnd = calculateArrowEnd(center, dir);
            drawLine(poseStack, buffer, center, arrowEnd, camPos, arrowColor);

            Vec3[] arrowhead = calculateArrowhead(arrowEnd, dir);
            drawLine(poseStack, buffer, arrowEnd, arrowhead[0], camPos, arrowColor);
            drawLine(poseStack, buffer, arrowEnd, arrowhead[1], camPos, arrowColor);
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

    private static void renderPaths(ClientRegionCache.Region region, PoseStack poseStack,
                                     BufferBuilder buffer, Vec3 camPos) {
        for (ClientRegionCache.Edge edge : region.edges()) {
            double fromX = edge.from().getMiddleBlockX();
            double fromZ = edge.from().getMiddleBlockZ();
            double toX = edge.to().getMiddleBlockX();
            double toZ = edge.to().getMiddleBlockZ();

            Vec3 start = new Vec3(fromX, edge.fromY(), fromZ);
            Vec3 end = new Vec3(toX, edge.toY(), toZ);

            drawLine(poseStack, buffer, start, end, camPos, COLOR_PATH);
        }
    }

    private static Vec3 calculateArrowEnd(Vec3 center, FlowDirection dir) {
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
