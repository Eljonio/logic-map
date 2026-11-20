package com.logicmap.model;

import java.util.List;

public record ScanResult(
    List<MapNode> nodes,
    List<MapEdge> edges
) {}
