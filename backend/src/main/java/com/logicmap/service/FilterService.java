package com.logicmap.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Learnable filter — stores rejected node labels per project.
 * When user marks a node as "not relevant", it's remembered
 * and automatically filtered on next scan.
 */
@Service
public class FilterService {

    private static final Path DATA_DIR = Path.of(System.getProperty("user.home"), ".logic-map");
    private static final Path FILTERS_FILE = DATA_DIR.resolve("filters.json");
    private static final ObjectMapper mapper = new ObjectMapper();

    // projectPath -> set of rejected labels
    private final Map<String, Set<String>> rejectedLabels = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadFromDisk();
    }

    /**
     * Mark a label as rejected for a project
     */
    public void reject(String projectPath, String label) {
        rejectedLabels
            .computeIfAbsent(normalize(projectPath), k -> ConcurrentHashMap.newKeySet())
            .add(label.toLowerCase());
        saveToDisk();
    }

    /**
     * Un-reject a label (user changed their mind)
     */
    public void accept(String projectPath, String label) {
        var set = rejectedLabels.get(normalize(projectPath));
        if (set != null) {
            set.remove(label.toLowerCase());
            saveToDisk();
        }
    }

    /**
     * Check if a label is rejected for a project
     */
    public boolean isRejected(String projectPath, String label) {
        var set = rejectedLabels.get(normalize(projectPath));
        return set != null && set.contains(label.toLowerCase());
    }

    /**
     * Get all rejected labels for a project
     */
    public Set<String> getRejected(String projectPath) {
        return Set.copyOf(rejectedLabels.getOrDefault(normalize(projectPath), Set.of()));
    }

    /**
     * Clear all filters for a project
     */
    public void clearFilters(String projectPath) {
        rejectedLabels.remove(normalize(projectPath));
        saveToDisk();
    }

    private String normalize(String path) {
        return path.toLowerCase().replaceAll("[/\\\\]+$", "");
    }

    private void saveToDisk() {
        try {
            Files.createDirectories(DATA_DIR);
            // Convert ConcurrentHashMap.KeySetView to regular sets for serialization
            var serializable = new HashMap<String, List<String>>();
            rejectedLabels.forEach((k, v) -> serializable.put(k, new ArrayList<>(v)));
            mapper.writeValue(FILTERS_FILE.toFile(), serializable);
        } catch (IOException e) {
            System.err.println("Failed to save filters: " + e.getMessage());
        }
    }

    private void loadFromDisk() {
        if (!Files.exists(FILTERS_FILE)) return;
        try {
            Map<String, List<String>> data = mapper.readValue(
                FILTERS_FILE.toFile(), new TypeReference<>() {}
            );
            data.forEach((k, v) -> {
                var set = ConcurrentHashMap.<String>newKeySet();
                set.addAll(v);
                rejectedLabels.put(k, set);
            });
            System.out.println("Loaded filters from disk: " + rejectedLabels.size() + " projects");
        } catch (IOException e) {
            System.err.println("Failed to load filters: " + e.getMessage());
        }
    }
}
