package org.sosly.rivertale.river;

import java.util.ArrayList;
import java.util.List;
import org.sosly.rivertale.core.CellPos;

public class PendingWatershed {
    private final CellPos terminus;
    private final List<Path> paths = new ArrayList<>();

    public PendingWatershed(CellPos terminus) {
        this.terminus = terminus;
    }

    public void addPath(Path path) {
        paths.add(path);
    }

    public CellPos terminus() {
        return terminus;
    }

    public List<Path> paths() {
        return paths;
    }
}
