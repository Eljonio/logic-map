package com.logicmap.controller;

import com.logicmap.service.FilterService;
import com.logicmap.service.MapService;
import com.logicmap.service.TableVerifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/verify")
public class VerifyController {

    private final TableVerifier verifier;
    private final MapService mapService;
    private final FilterService filterService;

    public VerifyController(TableVerifier verifier, MapService mapService, FilterService filterService) {
        this.verifier = verifier;
        this.mapService = mapService;
        this.filterService = filterService;
    }

    /**
     * Verify tables against SQL migration files.
     * Auto-rejects scanner findings that don't match real tables.
     */
    @PostMapping("/migrations")
    public Map<String, Object> verifyFromMigrations(@RequestBody Map<String, String> body) {
        var migrationsPath = body.get("migrationsPath");
        var projectPath = body.get("projectPath");

        if (migrationsPath == null || projectPath == null) {
            return Map.of("error", "migrationsPath and projectPath required");
        }

        var realTables = verifier.fromMigrations(migrationsPath);
        return doVerify(realTables, projectPath);
    }

    /**
     * Verify tables against a live database.
     * Auto-rejects scanner findings that don't match real tables.
     */
    @PostMapping("/database")
    public Map<String, Object> verifyFromDatabase(@RequestBody Map<String, String> body) {
        var jdbcUrl = body.get("jdbcUrl");
        var user = body.get("user");
        var password = body.get("password");
        var projectPath = body.get("projectPath");

        if (jdbcUrl == null || projectPath == null) {
            return Map.of("error", "jdbcUrl and projectPath required");
        }

        var realTables = verifier.fromDatabase(jdbcUrl, user != null ? user : "", password != null ? password : "");
        return doVerify(realTables, projectPath);
    }

    private Map<String, Object> doVerify(Set<String> realTables, String projectPath) {
        // An empty list means the source could not be read (no migrations found, failed connection,
        // missing JDBC driver), not that the project has no tables. Rejecting against it would throw
        // away every table node, so refuse instead.
        if (realTables.isEmpty()) {
            return Map.of("error", "no tables found in the source (unreadable migrations, failed database "
                + "connection or missing JDBC driver); nothing was rejected");
        }

        // Get current map's database nodes
        var map = mapService.getMap();
        var dbLabels = map.nodes().stream()
            .filter(n -> "database".equals(n.data().nodeType()))
            .map(n -> n.data().label())
            .toList();

        // Find what to reject
        var toReject = verifier.autoFilter(realTables, dbLabels);

        // Auto-reject
        for (var label : toReject) {
            filterService.reject(projectPath, label);
        }

        var result = new HashMap<String, Object>();
        result.put("realTables", realTables.stream().sorted().toList());
        result.put("rejected", toReject.stream().sorted().toList());
        result.put("verified", dbLabels.stream().filter(l -> realTables.contains(l.toLowerCase())).sorted().toList());
        result.put("totalReal", realTables.size());
        result.put("totalRejected", toReject.size());
        return result;
    }
}
