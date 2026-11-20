package com.logicmap.model;

import java.util.Map;

public record MapEdge(
    String id,
    String source,
    String target,
    String label,
    EdgeData data
) {
    public record EdgeData(
        String type,
        String origin,
        double confidence,
        Map<String, String> meta
    ) {}
}
