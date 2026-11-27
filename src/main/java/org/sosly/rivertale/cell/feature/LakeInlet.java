package org.sosly.rivertale.cell.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.terrain.CarveProposal;

interface LakeInlet extends FeatureHandler {
    @Override
    default List<CarveProposal> carve(Cell lake, Watershed watershed, ChunkPos chunk, long seed) {
        if (lake.cellY().isEmpty()) {
            return List.of();
        }
        List<CarveProposal> cuts = new ArrayList<>();
        for (CellPos pos : watershed.upstream(lake.pos())) {
            Cell river = watershed.elevatedCell(pos);
            if (river.feature() == Feature.KETTLE || river.feature() == Feature.ENDORHEIC) {
                continue;
            }
            OptionalDouble flow = watershed.flow(pos);
            if (flow.isPresent()) {
                cuts.addAll(CourseCarver.inlet(CellCrossings.between(river, lake), CellCrossings.center(lake), flow.getAsDouble(), chunk));
            }
        }
        return cuts;
    }
}
