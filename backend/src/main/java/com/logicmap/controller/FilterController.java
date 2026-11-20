package com.logicmap.controller;

import com.logicmap.service.FilterService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/filters")
public class FilterController {

    private final FilterService filterService;

    public FilterController(FilterService filterService) {
        this.filterService = filterService;
    }

    @GetMapping
    public Set<String> getRejected(@RequestParam String projectPath) {
        return filterService.getRejected(projectPath);
    }

    @PostMapping("/reject")
    public Map<String, String> reject(@RequestBody Map<String, String> body) {
        filterService.reject(body.get("projectPath"), body.get("label"));
        return Map.of("status", "rejected", "label", body.get("label"));
    }

    @PostMapping("/accept")
    public Map<String, String> accept(@RequestBody Map<String, String> body) {
        filterService.accept(body.get("projectPath"), body.get("label"));
        return Map.of("status", "accepted", "label", body.get("label"));
    }

    @DeleteMapping
    public Map<String, String> clearFilters(@RequestParam String projectPath) {
        filterService.clearFilters(projectPath);
        return Map.of("status", "cleared");
    }
}
