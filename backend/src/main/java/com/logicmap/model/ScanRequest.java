package com.logicmap.model;

import java.util.List;

public record ScanRequest(
    String projectPath,
    List<String> fileExtensions
) {}
