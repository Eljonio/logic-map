package com.logicmap.controller;

import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/projects")
public class ProjectsController {

    // Project markers — if a directory contains one of these, it's a project
    private static final List<String> PROJECT_MARKERS = List.of(
        "package.json", "pom.xml", "build.gradle", "go.mod",
        "Cargo.toml", "requirements.txt", "composer.json",
        "Gemfile", "mix.exs", "pubspec.yaml"
    );

    private static final List<String> SKIP_DIRS = List.of(
        "node_modules", ".git", "dist", "build", "target",
        ".cache", ".npm", ".m2", "AppData", "Library",
        "Windows", "Program Files", "Program Files (x86)"
    );

    // Scan history
    private final List<Map<String, Object>> scanHistory = new CopyOnWriteArrayList<>();

    @GetMapping("/discover")
    public List<Map<String, String>> discover(@RequestParam(defaultValue = "") String root) {
        var searchRoot = root.isEmpty() ? getDefaultRoot() : root;
        var projects = new ArrayList<Map<String, String>>();

        try {
            Files.walkFileTree(Path.of(searchRoot), Set.of(), 4, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    var name = dir.getFileName().toString();
                    if (SKIP_DIRS.contains(name) || name.startsWith(".")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    var fileName = file.getFileName().toString();
                    if (PROJECT_MARKERS.contains(fileName)) {
                        var dir = file.getParent();
                        var type = detectProjectType(fileName);
                        projects.add(Map.of(
                            "path", dir.toString(),
                            "name", dir.getFileName().toString(),
                            "type", type,
                            "marker", fileName
                        ));
                        return FileVisitResult.SKIP_SIBLINGS;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // ignore
        }

        return projects;
    }

    @GetMapping("/history")
    public List<Map<String, Object>> getHistory() {
        return List.copyOf(scanHistory);
    }

    @PostMapping("/history")
    public Map<String, String> addToHistory(@RequestBody Map<String, String> body) {
        var entry = new HashMap<String, Object>();
        entry.put("path", body.get("path"));
        entry.put("name", body.getOrDefault("name", ""));
        entry.put("timestamp", System.currentTimeMillis());

        // Remove duplicate if exists
        scanHistory.removeIf(h -> body.get("path").equals(h.get("path")));
        scanHistory.addFirst(entry);

        // Keep only last 20
        while (scanHistory.size() > 20) {
            scanHistory.removeLast();
        }

        return Map.of("status", "saved");
    }

    private String getDefaultRoot() {
        // WSL: scan /mnt/c/Users/
        var wslPath = Path.of("/mnt/c/Users");
        if (Files.isDirectory(wslPath)) {
            return wslPath.toString();
        }
        // Fallback to home
        return System.getProperty("user.home");
    }

    private String detectProjectType(String marker) {
        return switch (marker) {
            case "package.json" -> "node";
            case "pom.xml" -> "java-maven";
            case "build.gradle" -> "java-gradle";
            case "go.mod" -> "go";
            case "Cargo.toml" -> "rust";
            case "requirements.txt" -> "python";
            case "composer.json" -> "php";
            case "Gemfile" -> "ruby";
            case "mix.exs" -> "elixir";
            case "pubspec.yaml" -> "flutter";
            default -> "unknown";
        };
    }
}
