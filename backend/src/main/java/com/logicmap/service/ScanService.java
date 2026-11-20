package com.logicmap.service;

import com.logicmap.model.*;
import com.logicmap.service.parser.FileParser;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScanService {

    private final List<FileParser> parsers;
    private final FilterService filterService;
    private final EmbeddingService embeddingService;

    public ScanService(List<FileParser> parsers, FilterService filterService, EmbeddingService embeddingService) {
        this.parsers = parsers;
        this.filterService = filterService;
        this.embeddingService = embeddingService;
    }

    private String currentProjectPath;

    public ScanResult scan(ScanRequest request) throws IOException {
        currentProjectPath = request.projectPath();
        var projectPath = Path.of(request.projectPath());
        if (!Files.isDirectory(projectPath)) {
            throw new IllegalArgumentException("Path is not a directory: " + request.projectPath());
        }

        var extensions = request.fileExtensions();
        if (extensions == null || extensions.isEmpty()) {
            extensions = List.of(".js", ".ts");
        }

        var files = collectFiles(projectPath, extensions);

        var allFindings = new ArrayList<RawFinding>();
        for (var file : files) {
            var content = Files.readString(file);
            var relativePath = projectPath.relativize(file).toString();

            for (var parser : parsers) {
                if (parser.supports(relativePath)) {
                    allFindings.addAll(parser.parse(relativePath, content));
                }
            }
        }

        return buildGraph(allFindings);
    }

    private List<Path> collectFiles(Path root, List<String> extensions) throws IOException {
        var files = new ArrayList<Path>();

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                var name = dir.getFileName().toString();
                if (name.equals("node_modules") || name.equals(".git") || name.equals("dist")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                var fileName = file.toString();
                for (var ext : extensions) {
                    if (fileName.endsWith(ext)) {
                        files.add(file);
                        break;
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return files;
    }

    private ScanResult buildGraph(List<RawFinding> findings) {
        var nodes = new ArrayList<MapNode>();
        var edges = new ArrayList<MapEdge>();
        var nodeIds = new HashSet<String>();

        // Group findings by type
        var routes = findings.stream().filter(f -> "ROUTE".equals(f.type())).toList();
        var dbQueries = findings.stream().filter(f -> "DB_QUERY".equals(f.type())).toList();
        var socketEmits = findings.stream().filter(f -> "SOCKET_EMIT".equals(f.type())).toList();
        var cacheOps = findings.stream().filter(f -> "CACHE".equals(f.type())).toList();
        var httpRequests = findings.stream().filter(f -> "HTTP_REQUEST".equals(f.type())).toList();
        var fileOps = findings.stream().filter(f -> "FILE_OP".equals(f.type())).toList();
        var envVars = findings.stream().filter(f -> "ENV_VAR".equals(f.type())).toList();
        var middleware = findings.stream().filter(f -> "MIDDLEWARE".equals(f.type())).toList();
        var routeDb = findings.stream().filter(f -> "ROUTE_DB".equals(f.type())).toList();
        var routeDbColumn = findings.stream().filter(f -> "ROUTE_DB_COLUMN".equals(f.type())).toList();
        var routeEmit = findings.stream().filter(f -> "ROUTE_EMIT".equals(f.type())).toList();
        var routeCache = findings.stream().filter(f -> "ROUTE_CACHE".equals(f.type())).toList();
        var routeAuth = findings.stream().filter(f -> "ROUTE_AUTH".equals(f.type())).toList();
        var routeTx = findings.stream().filter(f -> "ROUTE_TX".equals(f.type())).toList();
        var routeParams = findings.stream().filter(f -> "ROUTE_PARAMS".equals(f.type())).toList();
        var imports = findings.stream().filter(f -> "IMPORT".equals(f.type())).toList();
        var routeCalls = findings.stream().filter(f -> "ROUTE_CALLS".equals(f.type())).toList();
        var funcCalls = findings.stream().filter(f -> "FUNC_CALL".equals(f.type())).toList();

        // Build import map: file -> {importedName -> resolvedFile}
        var importMap = buildImportMap(imports, findings);

        // Build function -> file map
        var funcFileMap = buildFuncFileMap(funcCalls);

        // Build function call graph: funcName -> set of functions it calls
        var callGraph = buildCallGraph(funcCalls);

        // Build per-route metadata: auth, tx, params
        Map<String, List<String>> authByRoute = new HashMap<>();
        Map<String, Boolean> txByRoute = new HashMap<>();
        Map<String, List<String>> paramsByRoute = new HashMap<>();

        for (var ra : routeAuth) {
            var detail = ra.detail();
            authByRoute.computeIfAbsent(ra.calledFrom(), k -> new ArrayList<>()).add(detail);
        }
        for (var rt : routeTx) {
            txByRoute.put(rt.calledFrom(), true);
        }
        for (var rp : routeParams) {
            paramsByRoute.computeIfAbsent(rp.calledFrom(), k -> new ArrayList<>()).add(rp.detail());
        }

        // Create route nodes (with auth/tx/params info in description)
        for (var route : routes) {
            var id = toId("route", route.name());
            if (nodeIds.add(id)) {
                var sb = new StringBuilder();
                sb.append(route.file()).append(":").append(route.line());

                var auths = authByRoute.get(route.name());
                if (auths != null && !auths.isEmpty()) {
                    sb.append(" | 🔒 ").append(String.join(", ", auths));
                }
                if (Boolean.TRUE.equals(txByRoute.get(route.name()))) {
                    sb.append(" | 💾 transaction");
                }
                var params = paramsByRoute.get(route.name());
                if (params != null && !params.isEmpty()) {
                    sb.append(" | 📥 ").append(String.join("; ", params));
                }

                nodes.add(createNode(id, route.name(), "route", "#22c55e", sb.toString()));
            }
        }

        // Create DB table nodes — combine route-scoped (precise) AND global SQL findings.
        // This way:
        //  - In a typical Express app, route-scoped catches everything.
        //  - In split apps (e.g. a load tester where SQL lives in helper scripts that aren't
        //    reached from a route handler), global SQL still creates the table nodes.
        // The verifier (filterService) cleans up false positives either way.
        var allTables = new HashSet<String>();
        routeDb.forEach(f -> allTables.add(f.detail().replace("table: ", "")));
        dbQueries.forEach(f -> allTables.add(f.detail().replace("table: ", "")));
        for (var table : allTables) {
            var id = toId("table", table);
            if (nodeIds.add(id)) {
                nodes.add(createNode(id, table, "database", "#ef4444", "database table"));
            }
        }

        // Create socket event nodes — global findings + route-scoped (route-scoped already in routeEmit)
        var allEvents = new HashSet<String>();
        routeEmit.forEach(f -> allEvents.add(f.detail().replace("event: ", "")));
        socketEmits.forEach(f -> allEvents.add(f.name().replace("emit: ", "")));
        for (var event : allEvents) {
            var id = toId("event", event);
            if (nodeIds.add(id)) {
                nodes.add(createNode(id, "WS: " + event, "event", "#f97316", "websocket event"));
            }
        }

        // Create cache nodes — global findings + route-scoped
        var allCacheKeys = new HashSet<String>();
        routeCache.forEach(f -> allCacheKeys.add(f.detail().replace("cache: ", "")));
        cacheOps.forEach(f -> {
            var name = f.name();
            var colonIdx = name.indexOf(": ");
            allCacheKeys.add(colonIdx >= 0 ? name.substring(colonIdx + 2) : name);
        });
        for (var key : allCacheKeys) {
            var id = toId("cache", key);
            if (nodeIds.add(id)) {
                nodes.add(createNode(id, "Cache: " + key, "cache", "#a855f7", "redis cache"));
            }
        }

        // Create HTTP request nodes (external APIs)
        var httpUrls = httpRequests.stream()
            .map(RawFinding::name).distinct().toList();
        for (var url : httpUrls) {
            var id = toId("http", url);
            if (nodeIds.add(id)) {
                nodes.add(createNode(id, url, "http", "#3b82f6", "external HTTP request"));
            }
        }

        // Create file operation nodes
        var fileNames = fileOps.stream()
            .map(RawFinding::name).distinct().toList();
        for (var fname : fileNames) {
            var id = toId("file", fname);
            if (nodeIds.add(id)) {
                nodes.add(createNode(id, fname, "file", "#f59e0b", "file operation"));
            }
        }

        // Create env variable nodes
        for (var env : envVars) {
            var id = toId("env", env.name());
            if (nodeIds.add(id)) {
                nodes.add(createNode(id, "ENV: " + env.name(), "env", "#06b6d4",
                    "file: " + env.file() + ":" + env.line()));
            }
        }

        // Create middleware nodes
        var mwNames = middleware.stream()
            .map(RawFinding::name).distinct().toList();
        for (var mw : mwNames) {
            var id = toId("middleware", mw);
            if (nodeIds.add(id)) {
                nodes.add(createNode(id, mw, "middleware", "#ec4899", "middleware"));
            }
        }

        // === BUILD PRECISE EDGES ===
        // Use ROUTE_DB / ROUTE_EMIT / ROUTE_CACHE findings from RouteBodyAnalyzer.
        // These are extracted by walking the actual handler body + reachable local functions,
        // so a link is created only when the SQL/emit/cache is really inside the call chain.

        // Build columns map: (routeName, table) -> set of columns
        Map<String, Set<String>> routeColumns = new HashMap<>();
        for (var rdc : routeDbColumn) {
            var routeName = rdc.calledFrom();
            var detail = rdc.detail().replace("column: ", "");
            // detail format: "table.column"
            int dot = detail.indexOf('.');
            if (dot < 0) continue;
            var table = detail.substring(0, dot);
            var col = detail.substring(dot + 1);
            var key = routeName + "::" + table;
            routeColumns.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(col);
        }

        for (var rdb : routeDb) {
            var routeName = rdb.calledFrom();
            var table = rdb.detail().replace("table: ", "");
            var op = rdb.name().split(" ")[0];
            var routeId = toId("route", routeName);
            var tableId = toId("table", table);
            if (!nodeIds.contains(routeId) || !nodeIds.contains(tableId)) continue;

            // Build label with columns if known
            var cols = routeColumns.get(routeName + "::" + table);
            String label = op;
            Map<String, String> meta = new HashMap<>();
            if (cols != null && !cols.isEmpty()) {
                if (cols.contains("*")) {
                    label = op + " *";
                    meta.put("columns", "*");
                } else if (cols.size() <= 4) {
                    label = op + " " + String.join(",", cols);
                    meta.put("columns", String.join(",", cols));
                } else {
                    label = op + " (" + cols.size() + " cols)";
                    meta.put("columns", String.join(",", cols));
                }
            }
            addEdge(edges, routeId, tableId, label, 0.95, meta);
        }

        for (var re : routeEmit) {
            var routeName = re.calledFrom();
            var event = re.detail().replace("event: ", "");
            var routeId = toId("route", routeName);
            var eventId = toId("event", event);
            if (!nodeIds.contains(routeId) || !nodeIds.contains(eventId)) continue;
            addEdge(edges, routeId, eventId, "emits", 0.95);
        }

        for (var rc : routeCache) {
            var routeName = rc.calledFrom();
            var key = rc.detail().replace("cache: ", "");
            var routeId = toId("route", routeName);
            var cacheId = toId("cache", key);
            if (!nodeIds.contains(routeId) || !nodeIds.contains(cacheId)) continue;
            addEdge(edges, routeId, cacheId, "cache", 0.9);
        }

        // Apply learned filters — remove rejected nodes
        if (currentProjectPath != null) {
            var rejectedIds = new HashSet<String>();
            nodes.removeIf(n -> {
                if (filterService.isRejected(currentProjectPath, n.data().label())) {
                    rejectedIds.add(n.id());
                    return true;
                }
                return false;
            });
            edges.removeIf(e -> rejectedIds.contains(e.source()) || rejectedIds.contains(e.target()));
        }

        // Generate embeddings for semantic search (graceful — skipped if Ollama unavailable)
        if (embeddingService.isAvailable()) {
            for (int i = 0; i < nodes.size(); i++) {
                var node = nodes.get(i);
                var text = buildEmbeddingText(node);
                var vector = embeddingService.embed(text);
                if (vector != null) {
                    var d = node.data();
                    var newData = new MapNode.NodeData(
                        d.label(), d.nodeType(), d.color(), d.description(),
                        d.origin(), d.meta(), vector
                    );
                    nodes.set(i, new MapNode(node.id(), node.type(), node.position(), newData));
                }
            }
        }

        applyLayout(nodes);
        return new ScanResult(nodes, edges);
    }

    private String buildEmbeddingText(MapNode node) {
        var d = node.data();
        var sb = new StringBuilder();
        sb.append(d.nodeType()).append(": ").append(d.label());
        if (d.description() != null && !d.description().isBlank()) {
            sb.append(" — ").append(d.description());
        }
        return sb.toString();
    }

    /**
     * Expand a set of function names by following call chains up to maxDepth
     */
    private Set<String> expandCallChain(Set<String> startFunctions,
                                         Map<String, Set<String>> callGraph,
                                         int maxDepth) {
        var result = new HashSet<>(startFunctions);
        var frontier = new HashSet<>(startFunctions);

        for (int depth = 0; depth < maxDepth && !frontier.isEmpty(); depth++) {
            var nextFrontier = new HashSet<String>();
            for (var func : frontier) {
                // Check direct name
                var calls = callGraph.getOrDefault(func, Set.of());
                for (var call : calls) {
                    if (result.add(call)) {
                        nextFrontier.add(call);
                    }
                }
                // Check without object prefix
                var simpleName = func.contains(".") ? func.split("\\.")[1] : func;
                if (!simpleName.equals(func)) {
                    calls = callGraph.getOrDefault(simpleName, Set.of());
                    for (var call : calls) {
                        if (result.add(call)) {
                            nextFrontier.add(call);
                        }
                    }
                }
            }
            frontier = nextFrontier;
        }

        return result;
    }

    /**
     * Build map of function -> set of functions it calls
     */
    private Map<String, Set<String>> buildCallGraph(List<RawFinding> funcCalls) {
        var graph = new HashMap<String, Set<String>>();
        for (var fc : funcCalls) {
            // name format: "callerFunc -> calledFunc"
            var parts = fc.name().split(" -> ");
            if (parts.length == 2) {
                graph.computeIfAbsent(parts[0], k -> new HashSet<>()).add(parts[1]);
            }
        }
        return graph;
    }

    /**
     * Build map of function name -> file it's defined in
     */
    private Map<String, String> buildFuncFileMap(List<RawFinding> funcCalls) {
        var map = new HashMap<String, String>();
        for (var fc : funcCalls) {
            map.putIfAbsent(fc.calledFrom(), fc.file());
        }
        return map;
    }

    /**
     * Build import map from import findings
     */
    private Map<String, Set<String>> buildImportMap(List<RawFinding> imports, List<RawFinding> allFindings) {
        var graph = new HashMap<String, Set<String>>();
        for (var imp : imports) {
            var fromFile = imp.file();
            var importPath = imp.name();
            var resolved = resolveImportPath(fromFile, importPath);
            graph.computeIfAbsent(fromFile, k -> new HashSet<>()).add(resolved);
        }
        return graph;
    }

    private String resolveImportPath(String fromFile, String importPath) {
        var dir = "";
        var lastSlash = fromFile.lastIndexOf('/');
        if (lastSlash >= 0) {
            dir = fromFile.substring(0, lastSlash + 1);
        }
        var resolved = dir + importPath;
        while (resolved.contains("../")) {
            var idx = resolved.indexOf("../");
            if (idx > 0) {
                var before = resolved.substring(0, idx);
                var lastSep = before.lastIndexOf('/', before.length() - 2);
                resolved = (lastSep >= 0 ? before.substring(0, lastSep + 1) : "") + resolved.substring(idx + 3);
            } else break;
        }
        resolved = resolved.replace("./", "");
        if (!resolved.endsWith(".js") && !resolved.endsWith(".ts")) {
            resolved = resolved + ".js";
        }
        return resolved;
    }

    private MapNode createNode(String id, String label, String nodeType, String color, String description) {
        return new MapNode(id, "dynamic", new MapNode.Position(0, 0),
            new MapNode.NodeData(label, nodeType, color, description, "scanner", Map.of()));
    }

    private void addEdge(List<MapEdge> edges, String source, String target,
                          String label, double confidence) {
        addEdge(edges, source, target, label, confidence, Map.of());
    }

    private void addEdge(List<MapEdge> edges, String source, String target,
                          String label, double confidence, Map<String, String> meta) {
        var id = "edge_" + source + "_" + target;
        var exists = edges.stream().anyMatch(e -> e.id().equals(id));
        if (!exists) {
            edges.add(new MapEdge(id, source, target, label,
                new MapEdge.EdgeData("default", "scanner", confidence, meta)));
        }
    }

    private String toId(String prefix, String name) {
        return prefix + "_" + name.toLowerCase()
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_|_$", "");
    }

    private void applyLayout(List<MapNode> nodes) {
        var byType = nodes.stream()
            .collect(Collectors.groupingBy(n -> n.data().nodeType()));

        double xOffset = 0;
        for (var entry : byType.entrySet()) {
            double yOffset = 0;
            for (int i = 0; i < entry.getValue().size(); i++) {
                var node = entry.getValue().get(i);
                var newNode = new MapNode(node.id(), node.type(),
                    new MapNode.Position(xOffset, yOffset), node.data());
                var idx = nodes.indexOf(node);
                nodes.set(idx, newNode);
                yOffset += 120;
            }
            xOffset += 300;
        }
    }
}
