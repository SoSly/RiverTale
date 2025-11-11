package org.sosly.rivertale.client.debug;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ArrowPathGenerator {

    private static final int SHAFT_LENGTH = 20;
    private static final double STEP_SIZE = 1.0;
    private static final double ARROWHEAD_LENGTH = 2.0;
    private static final double ARROWHEAD_WIDTH = 1.0;
    private static final double TERRAIN_OFFSET = 0.5;
    private static final double HEIGHT_OFFSET = 5.0;

    public static List<Vec3> generateRandomArrow(Vec3 origin, Level level) {
        List<Vec3> path = new ArrayList<>();
        Random random = new Random();
        double angle = random.nextDouble() * 2 * Math.PI;

        Vec3 direction = new Vec3(
            Math.cos(angle),
            0,
            Math.sin(angle)
        ).normalize();

        Vec3 startTerrain = snapToTerrain(origin, level);
        Vec3 startPos = startTerrain.add(0, HEIGHT_OFFSET, 0);

        Vec3 endPosition = origin.add(direction.scale(SHAFT_LENGTH * STEP_SIZE));
        Vec3 endTerrain = snapToTerrain(endPosition, level);
        Vec3 endPos = endTerrain.add(0, HEIGHT_OFFSET, 0);

        for (int i = 0; i <= SHAFT_LENGTH; i++) {
            double t = (double) i / (double) SHAFT_LENGTH;
            Vec3 point = lerp(startPos, endPos, t);
            path.add(point);
        }

        if (path.size() > 1) {
            addArrowhead(path, direction, endPos);
        }

        return path;
    }

    private static Vec3 lerp(Vec3 start, Vec3 end, double t) {
        return new Vec3(
            start.x + (end.x - start.x) * t,
            start.y + (end.y - start.y) * t,
            start.z + (end.z - start.z) * t
        );
    }

    private static Vec3 snapToTerrain(Vec3 position, Level level) {
        int groundY = level.getHeight(
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (int) position.x,
            (int) position.z
        );
        return new Vec3(position.x, groundY + TERRAIN_OFFSET, position.z);
    }

    private static void addArrowhead(List<Vec3> path, Vec3 direction, Vec3 tip) {
        Vec3 perpendicular = new Vec3(-direction.z, 0, direction.x).normalize();
        Vec3 base = tip.subtract(direction.scale(ARROWHEAD_LENGTH));

        Vec3 leftWing = base.add(perpendicular.scale(ARROWHEAD_WIDTH));
        Vec3 rightWing = base.subtract(perpendicular.scale(ARROWHEAD_WIDTH));

        path.add(leftWing);
        path.add(tip);
        path.add(rightWing);
        path.add(base);
    }
}
