package org.sosly.rivertale.river;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.river.PreparedRiverData.CellData;
import org.sosly.rivertale.river.PreparedRiverData.Routing;
import org.sosly.rivertale.river.PreparedRiverData.Step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class SpillSearchTest {
    private final CellPos source = new CellPos(-1, 0);
    private final CellPos coast = new CellPos(0, -2);
    private final Map<CellPos, CellData> cells = new HashMap<>();
    private final Map<CellPos, List<Step>> moves = new HashMap<>();
    private final Map<LakePlan.Inlet, LakePlan> lakes = new HashMap<>();

    @Test
    void outletThatCirclesIntoAnOffChannelPoolMemberIsNotAnEscape() {
        CellPos entry = new CellPos(0, 0);
        CellPos lip = new CellPos(1, 0);
        CellPos bend = new CellPos(1, 1);
        CellPos pocket = new CellPos(0, 1);
        cell(source, 130, entry);
        cell(entry, 119);
        cell(lip, 123, bend);
        cell(bend, 122, pocket);
        cell(pocket, 121, entry);
        Map<CellPos, Integer> depths = Map.of(entry, 4, pocket, 2);
        lakes.put(new LakePlan.Inlet(source, entry), new LakePlan(List.of(new LakeFill.Spill(List.of(entry, lip), depths)), depths));
        Flowline result = tracer().trace(source);
        assertEquals(List.of(source, entry), result.cells());
        assertEquals(List.of(new Flowline.Lake(Feature.ENDORHEIC, depths)), result.lakes());
    }

    @Test
    void reconvergingBasinChainDoesNotHideALaterCoastalExit() {
        CellPos entry = new CellPos(0, 0);
        cell(source, 130, entry);
        CellPos inlet = source;
        for (int i = 0; i < 26; i++) {
            CellPos pit = new CellPos(3 * i, 0);
            CellPos a = new CellPos(pit.x() + 1, 0);
            CellPos b = new CellPos(pit.x() + 1, 1);
            CellPos joint = new CellPos(pit.x() + 2, 0);
            CellPos next = new CellPos(pit.x() + 3, 0);
            cell(pit, 119);
            cell(a, 123, joint);
            cell(b, 123, joint);
            cell(joint, 121, next);
            cell(next, 119);
            List<LakeFill.Spill> exits = new ArrayList<>();
            Map<CellPos, Integer> depths = Map.of(pit, 4);
            exits.add(new LakeFill.Spill(List.of(pit, a), depths));
            exits.add(new LakeFill.Spill(List.of(pit, b), depths));
            if (i == 0) {
                CellPos shoreLip = new CellPos(0, -1);
                cell(shoreLip, 124, coast);
                exits.add(new LakeFill.Spill(List.of(pit, shoreLip), Map.of(pit, 5)));
            }
            lakes.put(new LakePlan.Inlet(inlet, pit), new LakePlan(exits, depths));
            inlet = joint;
        }
        FlowlineTracer tracer = tracer();
        Flowline path = assertTimeoutPreemptively(Duration.ofSeconds(3), () -> tracer.trace(source));
        assertEquals(List.of(source, entry, new CellPos(0, -1), coast), path.cells());
        assertEquals(List.of(new Flowline.Lake(Feature.KETTLE, Map.of(entry, 5))), path.lakes());
    }

    @Test
    void sharedLipCanDrainForOneInletButReturnToTheOtherPool() {
        CellPos first = new CellPos(0, 0);
        CellPos second = new CellPos(-1, 1);
        CellPos lip = new CellPos(0, 1);
        CellPos bend = new CellPos(1, 0);
        CellPos pocket = new CellPos(0, -1);
        cell(source, 123, first, second);
        cell(first, 119);
        cell(second, 120);
        cell(lip, 123, bend);
        cell(bend, 122, pocket);
        cell(pocket, 121, coast);
        Map<CellPos, Integer> wetPocket = Map.of(first, 4, pocket, 2);
        Map<CellPos, Integer> dryPocket = Map.of(second, 3);
        lakes.put(new LakePlan.Inlet(source, first), new LakePlan(List.of(new LakeFill.Spill(List.of(first, lip), wetPocket)), wetPocket));
        lakes.put(new LakePlan.Inlet(source, second), new LakePlan(List.of(new LakeFill.Spill(List.of(second, lip), dryPocket)), dryPocket));
        Flowline path = tracer().trace(source);
        assertEquals(List.of(source, second, lip, bend, pocket, coast), path.cells());
        assertEquals(List.of(new Flowline.Lake(Feature.KETTLE, dryPocket)), path.lakes());
    }

    @Test
    void mayUndoFourFlatStepsButNotFive() {
        CellPos first = new CellPos(0, 0);
        CellPos second = new CellPos(1, 0);
        CellPos third = new CellPos(2, 0);
        CellPos fourth = new CellPos(3, 0);
        CellPos fifth = new CellPos(4, 0);
        cell(source, 63, first, coast);
        cell(first, 63, second);
        cell(second, 63, third);
        cell(third, 63, fourth);
        cell(fourth, 63);
        assertEquals(List.of(source, coast), tracer().trace(source).cells());
        cell(fourth, 63, fifth);
        cell(fifth, 63);
        assertFalse(tracer().trace(source).isValid());
    }

    @Test
    void returnPathMayRiseFourBlocksButNotFiveEvenWhenTheForkIsLower() {
        CellPos ridge = new CellPos(0, 0);
        CellPos end = new CellPos(1, 0);
        cell(source, 65, ridge, coast);
        cell(ridge, 67, end);
        cell(end, 63);
        assertEquals(List.of(source, coast), tracer().trace(source).cells());
        cell(ridge, 68, end);
        assertFalse(tracer().trace(source).isValid());
    }

    @Test
    void failedFifthStepClosesTheLakeRatherThanTryingItsOtherOutlet() {
        CellPos entry = new CellPos(0, 0);
        CellPos lip = new CellPos(1, 0);
        CellPos second = new CellPos(2, 0);
        CellPos third = new CellPos(3, 0);
        CellPos fourth = new CellPos(4, 0);
        CellPos alternate = new CellPos(0, -1);
        Map<CellPos, Integer> depths = Map.of(entry, 4);
        cell(source, 130, entry);
        cell(entry, 119);
        cell(lip, 123, second);
        cell(second, 122, third);
        cell(alternate, 123, coast);
        lakes.put(new LakePlan.Inlet(source, entry), new LakePlan(List.of(
            new LakeFill.Spill(List.of(entry, lip), depths), new LakeFill.Spill(List.of(entry, alternate), depths)), depths));
        cell(third, 121, fourth);
        blockedWall(fourth);
        assertEquals(List.of(source, entry, alternate, coast), tracer().trace(source).cells());
        cell(lip, 125, second);
        assertEquals(List.of(new Flowline.Lake(Feature.ENDORHEIC, depths)), tracer().trace(source).lakes());
        cell(lip, 123, second);
        CellPos fifth = new CellPos(5, 0);
        cell(fourth, 121, fifth);
        blockedWall(fifth);
        Flowline result = tracer().trace(source);
        assertEquals(List.of(source, entry), result.cells());
        assertEquals(List.of(new Flowline.Lake(Feature.ENDORHEIC, depths)), result.lakes());
    }

    @Test
    void canRetryANearbyForkAfterMeetingATracedRiver() {
        CellPos join = new CellPos(0, 0);
        CellPos bend = new CellPos(1, 0);
        cell(source, 130, join, coast);
        cell(join, 128, bend);
        cell(bend, 127, coast);
        moves.put(source, List.of(new Step(join, 0, false), new Step(coast, 0, true)));
        moves.put(join, List.of(new Step(bend, 0, false)));
        assertEquals(coast, tracer().trace(source).terminus());
        FlowlineTracer traced = tracer();
        assertEquals(coast, traced.trace(join).terminus());
        assertEquals(List.of(source, coast), traced.trace(source).cells());
    }

    @Test
    void hollowTriesNudgeThenSpillBeforeTakingAnUpstreamAlternative() {
        CellPos entry = new CellPos(0, 0);
        CellPos lip = new CellPos(0, -1);
        CellPos nudge = new CellPos(1, -1);
        CellPos alternate = new CellPos(-1, -1);
        cell(source, 123, entry, alternate);
        cell(entry, 119);
        cell(lip, 123, coast);
        cell(alternate, 128, coast);
        cell(nudge, 120, coast);
        Map<CellPos, Integer> depths = Map.of(entry, 4);
        lakes.put(new LakePlan.Inlet(source, entry), new LakePlan(List.of(new LakeFill.Spill(List.of(entry, lip), depths)), depths));
        moves.put(entry, List.of(new Step(nudge, 1, true, true)));
        Flowline dry = tracer().trace(source);
        assertEquals(List.of(source, entry, nudge, coast), dry.cells());
        assertEquals(List.of(), dry.lakes());

        moves.put(nudge, List.of());
        Flowline spilling = tracer().trace(source);
        assertEquals(List.of(source, entry, lip, coast), spilling.cells());
        assertEquals(List.of(new Flowline.Lake(Feature.KETTLE, depths)), spilling.lakes());

        moves.put(lip, List.of());
        Flowline retried = tracer().trace(source);
        assertEquals(List.of(source, alternate, coast), retried.cells());
        assertEquals(List.of(), retried.lakes());

        moves.put(alternate, List.of());
        Flowline stranded = tracer().trace(source);
        assertEquals(List.of(source, entry), stranded.cells());
        assertEquals(List.of(new Flowline.Lake(Feature.ENDORHEIC, depths)), stranded.lakes());
    }

    private void blockedWall(CellPos pos) {
        cells.put(pos, new CellData(new Cell(pos, Set.of(), List.of(FlowDirection.WEST), Feature.NONE), 120));
        moves.put(pos, List.of(new Step(coast, 0, true, true)));
    }

    private void cell(CellPos pos, int height, CellPos... next) {
        cells.put(pos, new CellData(new Cell(pos, Set.of()), height));
        moves.put(pos, List.of(next).stream().map(to -> new Step(to, 0, true)).toList());
    }

    private FlowlineTracer tracer() {
        cell(coast, 63);
        Map<RegionPos, Routing> routing = new HashMap<>();
        for (CellPos pos : cells.keySet()) {
            routing.put(pos.getRegion(), new Routing(Set.of(pos.getRegion()), List.of(coast), Set.of(coast), Set.of()));
        }
        return new FlowlineTracer(new PreparedRiverData(Map.of(), cells, routing, moves, Map.of(), Map.of(), 1, 63, lakes));
    }
}
