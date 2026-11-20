package com.logicmap.service;

import com.logicmap.model.MapNode;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Semantic search using embeddings.
 * Compares query vector against all node vectors via cosine similarity,
 * returns top-K most relevant nodes.
 */
@Service
public class SemanticSearchService {

    private final EmbeddingService embeddingService;
    private final MapService mapService;

    public SemanticSearchService(EmbeddingService embeddingService, MapService mapService) {
        this.embeddingService = embeddingService;
        this.mapService = mapService;
    }

    public record SearchResult(MapNode node, double score) {}

    /**
     * Find top-K nodes most semantically similar to the query.
     */
    public List<SearchResult> search(String query, int topK) {
        if (query == null || query.isBlank()) return List.of();
        if (!embeddingService.isAvailable()) return List.of();

        var queryVector = embeddingService.embed(query);
        if (queryVector == null) return List.of();

        var nodes = mapService.getMap().nodes();

        return nodes.stream()
            .filter(n -> n.data().embedding() != null)
            .map(n -> new SearchResult(n, EmbeddingService.cosineSimilarity(queryVector, n.data().embedding())))
            .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
            .limit(topK)
            .toList();
    }

    /**
     * Find nodes similar to a given node (by ID).
     */
    public List<SearchResult> findSimilar(String nodeId, int topK) {
        var nodes = mapService.getMap().nodes();
        var target = nodes.stream().filter(n -> n.id().equals(nodeId)).findFirst().orElse(null);
        if (target == null || target.data().embedding() == null) return List.of();

        return nodes.stream()
            .filter(n -> !n.id().equals(nodeId) && n.data().embedding() != null)
            .map(n -> new SearchResult(n, EmbeddingService.cosineSimilarity(target.data().embedding(), n.data().embedding())))
            .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
            .limit(topK)
            .toList();
    }

    public Map<String, Object> status() {
        var nodes = mapService.getMap().nodes();
        long withEmbeddings = nodes.stream().filter(n -> n.data().embedding() != null).count();
        return Map.of(
            "ollamaAvailable", embeddingService.isAvailable(),
            "totalNodes", nodes.size(),
            "nodesWithEmbeddings", withEmbeddings
        );
    }
}
