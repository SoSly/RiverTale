package org.sosly.rivertale.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.core.Direction;

public class FlowDirectionClientCache {
    private static final Map<ChunkPos, Direction[][]> CACHE = new ConcurrentHashMap<>();

    public static void put(ChunkPos pos, Direction[][] directions) {
        CACHE.put(pos, directions);
    }

    @Nullable
    public static Direction get(ChunkPos pos, int localX, int localZ) {
        Direction[][] directions = CACHE.get(pos);
        if (directions == null) {
            return null;
        }
        if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16) {
            return null;
        }
        return directions[localX][localZ];
    }

    public static void remove(ChunkPos pos) {
        CACHE.remove(pos);
    }

    public static void clear() {
        CACHE.clear();
    }
}
