package com.logicmap.controller;

import com.logicmap.service.SemanticSearchService;
import com.logicmap.service.SemanticSearchService.SearchResult;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SemanticSearchService searchService;

    public SearchController(SemanticSearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/semantic")
    public List<Map<String, Object>> semantic(@RequestBody Map<String, Object> body) {
        var query = (String) body.get("query");
        var topK = body.get("topK") != null ? ((Number) body.get("topK")).intValue() : 10;
        return searchService.search(query, topK).stream()
            .map(this::toResponse)
            .toList();
    }

    @PostMapping("/similar")
    public List<Map<String, Object>> similar(@RequestBody Map<String, Object> body) {
        var nodeId = (String) body.get("nodeId");
        var topK = body.get("topK") != null ? ((Number) body.get("topK")).intValue() : 10;
        return searchService.findSimilar(nodeId, topK).stream()
            .map(this::toResponse)
            .toList();
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return searchService.status();
    }

    private Map<String, Object> toResponse(SearchResult r) {
        var m = new HashMap<String, Object>();
        m.put("id", r.node().id());
        m.put("label", r.node().data().label());
        m.put("type", r.node().data().nodeType());
        m.put("description", r.node().data().description() != null ? r.node().data().description() : "");
        m.put("score", r.score());
        return m;
    }
}
