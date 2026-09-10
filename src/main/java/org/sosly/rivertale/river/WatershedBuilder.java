package org.sosly.rivertale.river;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;

public final class WatershedBuilder {
    private static final Comparator<Node> NODE_ORDER = Comparator.comparingInt((Node node) -> node.pos().x())
        .thenComparingInt(node -> node.pos().z()).thenComparing(Node::crossing);
    private final Map<Node, Set<Node>> candidates = new HashMap<>();
    private final Map<Node, Node> downstream = new HashMap<>();
    private final Map<Link, Integer> support = new HashMap<>();
    private final Set<Node> outlets = new HashSet<>();
    private final Set<Node> sources = new HashSet<>();
    private final Set<CellPos> crossings;
    private int cycleRepairs;

    private WatershedBuilder(List<Flowline> paths) {
        crossings = findCrossings(paths);
        List<List<Node>> expanded = new ArrayList<>();
        for (Flowline path : paths) {
            List<Node> nodes = expand(path);
            expanded.add(nodes);
            sources.add(nodes.get(0));
            outlets.add(nodes.get(nodes.size() - 1));
            for (int i = 1; i < nodes.size(); i++) {
                candidates.computeIfAbsent(nodes.get(i - 1), key -> new HashSet<>()).add(nodes.get(i));
            }
        }
        countSupport(expanded);
    }

    public static List<Watershed> build(Collection<Flowline> flowlines) {
        Timer.Record timer = Store.getTimer(WatershedBuilder.class, "build").start();
        try {
            List<Flowline> paths = flowlines.stream().filter(Flowline::isValid).filter(path -> !path.cells().isEmpty()).distinct().toList();
            WatershedBuilder builder = new WatershedBuilder(paths);
            builder.chooseDownstream();
            builder.connectCycles();
            List<Watershed> watersheds = builder.assemble();
            builder.recordMetrics(watersheds);
            return watersheds;
        } finally {
            timer.stop();
        }
    }

    private Set<CellPos> findCrossings(List<Flowline> paths) {
        Map<CellPos, Integer> diagonals = new HashMap<>();
        for (Flowline path : paths) {
            if (!path.cells().get(path.cells().size() - 1).equals(path.terminus())) {
                throw new IllegalArgumentException("A flowline must end at its terminus");
            }
            for (int i = 1; i < path.cells().size(); i++) {
                CellPos from = path.cells().get(i - 1);
                CellPos to = path.cells().get(i);
                int dx = to.x() - from.x();
                int dz = to.z() - from.z();
                if (Math.abs(dx) > 1 || Math.abs(dz) > 1 || (dx == 0 && dz == 0)) {
                    throw new IllegalArgumentException("Flowline cells must be distinct adjacent neighbors");
                }
                if (dx != 0 && dz != 0) {
                    diagonals.merge(square(from, to), dx == dz ? 1 : 2, (first, second) -> first | second);
                }
            }
        }
        Set<CellPos> result = new HashSet<>();
        diagonals.forEach((pos, mask) -> {
            if (mask == 3) {
                result.add(pos);
            }
        });
        return result;
    }

    private List<Node> expand(Flowline path) {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < path.cells().size(); i++) {
            CellPos cell = path.cells().get(i);
            if (i > 0) {
                CellPos previous = path.cells().get(i - 1);
                CellPos square = square(previous, cell);
                if (previous.x() != cell.x() && previous.z() != cell.z() && crossings.contains(square)) {
                    nodes.add(new Node(square, true));
                }
            }
            nodes.add(new Node(cell, false));
        }
        return nodes;
    }

    private void countSupport(List<List<Node>> paths) {
        Map<Link, Set<CellPos>> contributors = new HashMap<>();
        for (List<Node> path : paths) {
            Set<CellPos> prefix = new HashSet<>();
            for (int i = 0; i < path.size() - 1; i++) {
                Node from = path.get(i);
                if (!from.crossing()) {
                    prefix.add(from.pos());
                }
                if (candidates.get(from).size() > 1) {
                    contributors.computeIfAbsent(new Link(from, path.get(i + 1)), key -> new HashSet<>()).addAll(prefix);
                }
            }
        }
        contributors.forEach((link, cells) -> support.put(link, cells.size()));
    }

    private void chooseDownstream() {
        for (Map.Entry<Node, Set<Node>> entry : candidates.entrySet()) {
            if (outlets.contains(entry.getKey())) {
                continue;
            }
            Link preferred = entry.getValue().stream().map(to -> new Link(entry.getKey(), to))
                .min(this::compareLinks).orElseThrow();
            downstream.put(preferred.from(), preferred.to());
        }
    }

    private int compareLinks(Link first, Link second) {
        int weight = Integer.compare(support.getOrDefault(second, 0), support.getOrDefault(first, 0));
        if (weight != 0) {
            return weight;
        }
        int from = NODE_ORDER.compare(first.from(), second.from());
        return from == 0 ? NODE_ORDER.compare(first.to(), second.to()) : from;
    }

    private Map<Node, Set<Node>> reverseLinks() {
        Map<Node, Set<Node>> upstream = new HashMap<>();
        downstream.forEach((from, to) -> upstream.computeIfAbsent(to, key -> new HashSet<>()).add(from));
        return upstream;
    }

    private Set<Node> connectedToOutlets() {
        Map<Node, Set<Node>> upstream = reverseLinks();
        Set<Node> connected = new HashSet<>(outlets);
        ArrayDeque<Node> pending = new ArrayDeque<>(outlets);
        while (!pending.isEmpty()) {
            for (Node tributary : upstream.getOrDefault(pending.remove(), Set.of())) {
                if (connected.add(tributary)) {
                    pending.add(tributary);
                }
            }
        }
        return connected;
    }

    private void connectCycles() {
        Set<Node> connected = connectedToOutlets();
        while (!connected.containsAll(downstream.keySet())) {
            Link exit = null;
            for (Map.Entry<Node, Set<Node>> entry : candidates.entrySet()) {
                if (connected.contains(entry.getKey())) {
                    continue;
                }
                for (Node to : entry.getValue()) {
                    Link candidate = new Link(entry.getKey(), to);
                    if (connected.contains(to) && (exit == null || compareLinks(candidate, exit) < 0)) {
                        exit = candidate;
                    }
                }
            }
            if (exit == null) {
                throw new IllegalStateException("Traced river component has no route to an outlet");
            }
            downstream.put(exit.from(), exit.to());
            cycleRepairs++;
            connected = connectedToOutlets();
        }
    }

    private Map<Node, Set<Node>> retainedUpstream() {
        Set<Node> retained = new HashSet<>();
        for (Node source : sources) {
            Node current = source;
            while (current != null && retained.add(current)) {
                current = downstream.get(current);
            }
        }
        Map<Node, Set<Node>> upstream = new HashMap<>();
        for (Node from : retained) {
            Node to = downstream.get(from);
            if (to != null) {
                upstream.computeIfAbsent(to, key -> new HashSet<>()).add(from);
            }
        }
        return upstream;
    }

    private List<Watershed> assemble() {
        Map<Node, Set<Node>> upstream = retainedUpstream();
        List<Watershed> watersheds = new ArrayList<>();
        for (Node outlet : outlets.stream().sorted(NODE_ORDER).toList()) {
            if (!upstream.containsKey(outlet) && !sources.contains(outlet)) {
                continue;
            }
            Map<CellPos, CellPos> links = new LinkedHashMap<>();
            ArrayDeque<Node> pending = new ArrayDeque<>();
            pending.add(outlet);
            while (!pending.isEmpty()) {
                Node current = pending.remove();
                for (Node tributary : upstream.getOrDefault(current, Set.of()).stream().sorted(NODE_ORDER).toList()) {
                    if (!tributary.crossing()) {
                        Node target = current.crossing() ? downstream.get(current) : current;
                        links.put(tributary.pos(), target.pos());
                    }
                    pending.add(tributary);
                }
            }
            watersheds.add(Watershed.fromLinks(outlet.pos(), links));
        }
        return List.copyOf(watersheds);
    }

    private void recordMetrics(List<Watershed> watersheds) {
        Store.getDistribution(WatershedBuilder.class, "watersheds").record(watersheds.size());
        Store.getDistribution(WatershedBuilder.class, "crossings").record(crossings.size());
        Store.getDistribution(WatershedBuilder.class, "conflictingCells").record(candidates.entrySet().stream()
            .filter(entry -> !entry.getKey().crossing() && !outlets.contains(entry.getKey()) && entry.getValue().size() > 1).count());
        Store.getDistribution(WatershedBuilder.class, "cycleRepairs").record(cycleRepairs);
        for (Watershed watershed : watersheds) {
            Store.getDistribution(WatershedBuilder.class, "riverCells").record(watershed.allCells().size());
            Store.getDistribution(WatershedBuilder.class, "sources").record(watershed.allCells().stream()
                .filter(cell -> watershed.upstream(cell).isEmpty()).count());
            Store.getDistribution(WatershedBuilder.class, "confluences").record(watershed.allCells().stream()
                .filter(cell -> watershed.upstream(cell).size() > 1).count());
        }
    }

    private CellPos square(CellPos first, CellPos second) {
        return new CellPos(Math.min(first.x(), second.x()), Math.min(first.z(), second.z()));
    }

    private record Node(CellPos pos, boolean crossing) {
    }

    private record Link(Node from, Node to) {
    }
}
