package com.logicmap.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logicmap.model.MapEdge;
import com.logicmap.model.MapNode;
import com.logicmap.model.ScanResult;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MapService {

    private static final Path DATA_DIR = Path.of(System.getProperty("user.home"), ".logic-map");
    private static final Path MAP_FILE = DATA_DIR.resolve("map.json");
    private static final ObjectMapper mapper = new ObjectMapper();

    private final List<MapNode> nodes = new ArrayList<>();
    private final List<MapEdge> edges = new ArrayList<>();
    private final AtomicLong nodeCounter = new AtomicLong(0);
    private final AtomicLong edgeCounter = new AtomicLong(0);

    @PostConstruct
    public void init() {
        loadFromDisk();
    }

    public synchronized ScanResult getMap() {
        return new ScanResult(List.copyOf(nodes), List.copyOf(edges));
    }

    public synchronized MapNode addNode(String label, String nodeType, String color,
                                         String description, Double x, Double y) {
        var id = "node_" + System.currentTimeMillis() + "_" + nodeCounter.incrementAndGet();
        var position = new MapNode.Position(
            x != null ? x : nodes.size() * 200.0,
            y != null ? y : 0
        );
        var data = new MapNode.NodeData(
            label,
            nodeType != null ? nodeType : "default",
            color != null ? color : "#6366f1",
            description != null ? description : "",
            "mcp",
            Map.of()
        );
        var node = new MapNode(id, "dynamic", position, data);
        nodes.add(node);
        saveToDisk();
        return node;
    }

    public synchronized MapNode updateNode(String nodeId, String label, String description, String color) {
        for (int i = 0; i < nodes.size(); i++) {
            var node = nodes.get(i);
            if (node.id().equals(nodeId)) {
                var updated = new MapNode(
                    node.id(),
                    node.type(),
                    node.position(),
                    new MapNode.NodeData(
                        label != null ? label : node.data().label(),
                        node.data().nodeType(),
                        color != null ? color : node.data().color(),
                        description != null ? description : node.data().description(),
                        node.data().origin(),
                        node.data().meta()
                    )
                );
                nodes.set(i, updated);
                saveToDisk();
                return updated;
            }
        }
        return null;
    }

    public synchronized boolean deleteNode(String nodeId) {
        var removed = nodes.removeIf(n -> n.id().equals(nodeId));
        if (removed) {
            edges.removeIf(e -> e.source().equals(nodeId) || e.target().equals(nodeId));
            saveToDisk();
        }
        return removed;
    }

    public synchronized MapEdge addEdge(String source, String target, String label, String edgeType) {
        var id = "edge_" + System.currentTimeMillis() + "_" + edgeCounter.incrementAndGet();
        var data = new MapEdge.EdgeData(
            edgeType != null ? edgeType : "default",
            "mcp",
            1.0,
            Map.of()
        );
        var edge = new MapEdge(id, source, target, label != null ? label : "", data);
        edges.add(edge);
        saveToDisk();
        return edge;
    }

    public synchronized MapEdge updateEdge(String edgeId, String label, String edgeType) {
        for (int i = 0; i < edges.size(); i++) {
            var edge = edges.get(i);
            if (edge.id().equals(edgeId)) {
                var updated = new MapEdge(
                    edge.id(),
                    edge.source(),
                    edge.target(),
                    label != null ? label : edge.label(),
                    new MapEdge.EdgeData(
                        edgeType != null ? edgeType : edge.data().type(),
                        edge.data().origin(),
                        edge.data().confidence(),
                        edge.data().meta()
                    )
                );
                edges.set(i, updated);
                saveToDisk();
                return updated;
            }
        }
        return null;
    }

    public synchronized boolean deleteEdge(String edgeId) {
        var removed = edges.removeIf(e -> e.id().equals(edgeId));
        if (removed) saveToDisk();
        return removed;
    }

    public synchronized List<MapNode> search(String query) {
        var q = query.toLowerCase();
        return nodes.stream()
            .filter(n -> n.data().label().toLowerCase().contains(q)
                || (n.data().description() != null && n.data().description().toLowerCase().contains(q)))
            .toList();
    }

    public synchronized void clearMap() {
        nodes.clear();
        edges.clear();
        saveToDisk();
    }

    public synchronized void importMap(ScanResult result) {
        nodes.clear();
        edges.clear();
        nodes.addAll(result.nodes());
        edges.addAll(result.edges());
        saveToDisk();
    }

    private void saveToDisk() {
        try {
            Files.createDirectories(DATA_DIR);
            var data = new ScanResult(List.copyOf(nodes), List.copyOf(edges));
            mapper.writeValue(MAP_FILE.toFile(), data);
        } catch (IOException e) {
            System.err.println("Failed to save map: " + e.getMessage());
        }
    }

    private void loadFromDisk() {
        if (!Files.exists(MAP_FILE)) return;
        try {
            var data = mapper.readValue(MAP_FILE.toFile(), ScanResult.class);
            nodes.addAll(data.nodes());
            edges.addAll(data.edges());
            System.out.println("Loaded map from disk: " + nodes.size() + " nodes, " + edges.size() + " edges");
        } catch (IOException e) {
            System.err.println("Failed to load map: " + e.getMessage());
        }
    }
}
