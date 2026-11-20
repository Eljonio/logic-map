package com.logicmap.controller;

import com.logicmap.model.MapEdge;
import com.logicmap.model.MapNode;
import com.logicmap.model.ScanResult;
import com.logicmap.service.MapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/map")
public class MapController {

    private final MapService mapService;

    public MapController(MapService mapService) {
        this.mapService = mapService;
    }

    @GetMapping
    public ScanResult getMap() {
        return mapService.getMap();
    }

    @PutMapping
    public ScanResult syncMap(@RequestBody ScanResult data) {
        mapService.importMap(data);
        return mapService.getMap();
    }

    @DeleteMapping
    public Map<String, String> clearMap() {
        mapService.clearMap();
        return Map.of("status", "cleared");
    }

    // ─── NODES ──────────────────────────────────────────────

    @PostMapping("/nodes")
    public MapNode addNode(@RequestBody Map<String, Object> body) {
        return mapService.addNode(
            (String) body.get("label"),
            (String) body.get("nodeType"),
            (String) body.get("color"),
            (String) body.get("description"),
            body.get("x") != null ? ((Number) body.get("x")).doubleValue() : null,
            body.get("y") != null ? ((Number) body.get("y")).doubleValue() : null
        );
    }

    @PutMapping("/nodes/{nodeId}")
    public ResponseEntity<?> updateNode(@PathVariable String nodeId, @RequestBody Map<String, String> body) {
        var node = mapService.updateNode(nodeId, body.get("label"), body.get("description"), body.get("color"));
        if (node == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(node);
    }

    @DeleteMapping("/nodes/{nodeId}")
    public ResponseEntity<?> deleteNode(@PathVariable String nodeId) {
        if (mapService.deleteNode(nodeId)) {
            return ResponseEntity.ok(Map.of("status", "deleted"));
        }
        return ResponseEntity.notFound().build();
    }

    // ─── EDGES ──────────────────────────────────────────────

    @PostMapping("/edges")
    public MapEdge addEdge(@RequestBody Map<String, String> body) {
        return mapService.addEdge(
            body.get("source"),
            body.get("target"),
            body.get("label"),
            body.get("edgeType")
        );
    }

    @PutMapping("/edges/{edgeId}")
    public ResponseEntity<?> updateEdge(@PathVariable String edgeId, @RequestBody Map<String, String> body) {
        var edge = mapService.updateEdge(edgeId, body.get("label"), body.get("edgeType"));
        if (edge == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(edge);
    }

    @DeleteMapping("/edges/{edgeId}")
    public ResponseEntity<?> deleteEdge(@PathVariable String edgeId) {
        if (mapService.deleteEdge(edgeId)) {
            return ResponseEntity.ok(Map.of("status", "deleted"));
        }
        return ResponseEntity.notFound().build();
    }

    // ─── SEARCH ─────────────────────────────────────────────

    @GetMapping("/search")
    public List<MapNode> search(@RequestParam String q) {
        return mapService.search(q);
    }
}
