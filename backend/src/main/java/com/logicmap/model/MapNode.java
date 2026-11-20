package com.logicmap.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

public record MapNode(
    String id,
    String type,
    Position position,
    NodeData data
) {
    public record Position(double x, double y) {}

    public record NodeData(
        String label,
        String nodeType,
        String color,
        String description,
        String origin,
        Map<String, String> meta,
        @JsonInclude(JsonInclude.Include.NON_NULL) float[] embedding
    ) {
        // Convenience constructor without embedding (for scanner/MCP that don't generate vectors)
        public NodeData(String label, String nodeType, String color, String description,
                        String origin, Map<String, String> meta) {
            this(label, nodeType, color, description, origin, meta, null);
        }
    }
}
