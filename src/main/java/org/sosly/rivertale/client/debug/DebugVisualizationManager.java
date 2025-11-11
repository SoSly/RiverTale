package org.sosly.rivertale.client.debug;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

public class DebugVisualizationManager {

    private static boolean enabled = false;
    private static List<Vec3> arrowPath = new ArrayList<>();

    public static boolean toggle(ServerPlayer player) {
        enabled = !enabled;

        if (enabled) {
            arrowPath = ArrowPathGenerator.generateRandomArrow(
                player.position(),
                player.serverLevel()
            );
        } else {
            arrowPath.clear();
        }

        return enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static List<Vec3> getArrowPath() {
        return arrowPath;
    }

    public static void clear() {
        enabled = false;
        arrowPath.clear();
    }
}
