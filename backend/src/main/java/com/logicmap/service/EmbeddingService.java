package com.logicmap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Wrapper around Ollama embedding API.
 * Generates vectors from text for semantic search.
 *
 * Falls back gracefully if Ollama is not running — returns null
 * and the rest of the app continues working without embeddings.
 */
@Service
public class EmbeddingService {

    private static final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build();

    @Value("${ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${ollama.embedding.model:nomic-embed-text}")
    private String embeddingModel;

    private Boolean available = null;

    /**
     * Check if Ollama is available and embedding model is loaded
     */
    public boolean isAvailable() {
        if (available != null) return available;
        try {
            var req = HttpRequest.newBuilder()
                .uri(URI.create(ollamaUrl + "/api/tags"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
            var res = http.send(req, HttpResponse.BodyHandlers.ofString());
            available = res.statusCode() == 200;
        } catch (Exception e) {
            available = false;
        }
        return available;
    }

    /**
     * Generate embedding for a text. Returns null if Ollama is unavailable.
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) return null;
        if (!isAvailable()) return null;

        try {
            var body = mapper.writeValueAsString(Map.of(
                "model", embeddingModel,
                "input", text
            ));

            var req = HttpRequest.newBuilder()
                .uri(URI.create(ollamaUrl + "/api/embed"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            var res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return null;

            JsonNode root = mapper.readTree(res.body());
            JsonNode embeddings = root.get("embeddings");
            if (embeddings == null || !embeddings.isArray() || embeddings.isEmpty()) return null;

            JsonNode first = embeddings.get(0);
            float[] vector = new float[first.size()];
            for (int i = 0; i < first.size(); i++) {
                vector[i] = first.get(i).floatValue();
            }
            return vector;
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    /**
     * Cosine similarity between two vectors. Returns 0..1 (1 = identical).
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0.0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
