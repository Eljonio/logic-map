package com.logicmap.service.parser;

import com.logicmap.model.RawFinding;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Precisely extracts route handler bodies and finds operations INSIDE them.
 *
 * Unlike the loose "same file" approach, this:
 * 1. Finds each route definition: router.get('/path', ...handler)
 * 2. Extracts the FULL handler body using brace matching
 * 3. Looks for SQL/emit/cache operations ONLY within that exact body
 * 4. For function calls within the body, walks into local function bodies
 *    and continues searching transitively
 *
 * Result: a route is linked to a table only if there's actual SQL in its
 * call chain — not just because they're in the same file.
 */
@Component
public class RouteBodyAnalyzer implements FileParser {

    private static final Pattern ROUTE_DEF = Pattern.compile(
        "(?:router|app)\\.(get|post|put|delete|patch)\\s*\\(\\s*['\"]([^'\"]+)['\"]"
    );

    // Auth middleware in route signature: router.post('/x', authenticateToken, ...)
    // Captures everything between path and the handler arrow function
    private static final Pattern ROUTE_MIDDLEWARES_LINE = Pattern.compile(
        "(?:router|app)\\.(?:get|post|put|delete|patch)\\s*\\(\\s*['\"][^'\"]+['\"]\\s*,(.*)"
    );

    // Common auth function names
    private static final Pattern AUTH_NAME = Pattern.compile(
        "\\b(authenticate\\w*|requireAuth\\w*|isAuthenticated|verifyToken|jwtAuth\\w*|" +
        "requireRole\\w*|requireAdmin|requireManager|requireWaiter|adminOnly|managerOnly|" +
        "checkAuth|checkRole|protect|guard|authorize\\w*)\\b"
    );

    // Transaction markers: beginTransaction, commit, rollback, conn.beginTransaction()
    private static final Pattern TRANSACTION = Pattern.compile(
        "\\.\\s*(beginTransaction|commit|rollback)\\s*\\("
    );

    // Request body destructuring: const { x, y, z } = req.body
    private static final Pattern REQ_BODY_DESTRUCT = Pattern.compile(
        "const\\s*\\{\\s*([^}]+)\\}\\s*=\\s*req\\.body"
    );

    // Request param/query destructuring
    private static final Pattern REQ_PARAMS_DESTRUCT = Pattern.compile(
        "const\\s*\\{\\s*([^}]+)\\}\\s*=\\s*req\\.params"
    );

    private static final Pattern REQ_QUERY_DESTRUCT = Pattern.compile(
        "const\\s*\\{\\s*([^}]+)\\}\\s*=\\s*req\\.query"
    );

    // Direct field access: req.body.field
    private static final Pattern REQ_BODY_DIRECT = Pattern.compile(
        "req\\.body\\.(\\w+)"
    );

    private static final Pattern REQ_PARAMS_DIRECT = Pattern.compile(
        "req\\.params\\.(\\w+)"
    );

    private static final Pattern REQ_QUERY_DIRECT = Pattern.compile(
        "req\\.query\\.(\\w+)"
    );

    // SQL table reference inside a body
    private static final Pattern SQL_TABLE_REF = Pattern.compile(
        "(?:FROM|JOIN|INTO|UPDATE)\\s+[`'\"]?(\\w+)[`'\"]?",
        Pattern.CASE_INSENSITIVE
    );

    // Custom DB wrapper: batchInsert(conn, 'table_name', ...)
    private static final Pattern WRAPPER_TABLE = Pattern.compile(
        "(?:batchInsert|insertMany|bulkInsert|insertInto|deleteFrom|updateTable|truncateTable)" +
        "\\s*\\(.*?['\"](\\w+)['\"]",
        Pattern.CASE_INSENSITIVE
    );

    // Function definition we can step into
    private static final Pattern FUNCTION_DEF = Pattern.compile(
        "(?:async\\s+)?function\\s+(\\w+)\\s*\\(|" +
        "(?:const|let|var)\\s+(\\w+)\\s*=\\s*(?:async\\s*)?\\("
    );

    // Function call: name(...) — captures non-keyword identifiers
    private static final Pattern FUNCTION_CALL = Pattern.compile(
        "(?<![.\\w])(\\w+)\\s*\\("
    );

    // Socket emits — multiple flavours:
    //   socket.emit('name', ...), io.emit('name'), io.to(room).emit('name')
    //   sendToStaff('event', ...), sendToCustomer(...), broadcast('event'), notifyXxx('event')
    private static final Pattern EMIT_PATTERN = Pattern.compile(
        "(?:" +
        "(?:socket|io|wss?)\\s*\\.\\s*(?:to\\([^)]*\\)\\s*\\.\\s*)?emit\\s*\\(\\s*['\"]([^'\"]+)['\"]" +
        "|(?:sendTo\\w*|broadcast(?:To\\w*)?|notify\\w*|publish(?:To\\w*)?|push\\w*Notification|fireEvent)" +
        "\\s*\\([^,)]*,\\s*['\"]([^'\"]+)['\"]" +
        "|(?:sendTo\\w*|broadcast|notify\\w*|publish|emit)\\s*\\(\\s*['\"]([^'\"]+)['\"]" +
        ")"
    );

    private static final Pattern CACHE_PATTERN = Pattern.compile(
        "(?:cacheGet|cacheSet|cacheInvalidate|cacheDel)\\s*\\(\\s*['\"`]([^'\"`]+)['\"`]"
    );

    @Override
    public List<RawFinding> parse(String filePath, String content) {
        var findings = new ArrayList<RawFinding>();
        var lines = content.split("\n");

        // 1) Build map of function name -> body in this file
        var functions = extractFunctions(lines);

        // 2) Find all routes and their bodies
        for (int i = 0; i < lines.length; i++) {
            Matcher m = ROUTE_DEF.matcher(lines[i]);
            if (!m.find()) continue;

            String method = m.group(1).toUpperCase();
            String path = m.group(2);
            String routeName = method + " " + path;

            // Extract the route handler body
            int handlerStart = findHandlerBlockStart(lines, i);
            if (handlerStart == -1) continue;
            int handlerEnd = matchClosingBrace(lines, handlerStart);
            if (handlerEnd == -1) continue;

            String body = joinLines(lines, handlerStart, handlerEnd);

            // Walk into the handler body and follow local function calls
            var visited = new HashSet<String>();
            var allText = new StringBuilder(body);
            collectReachableBodies(body, functions, visited, allText, 0, 5);

            String fullCode = allText.toString();

            // Extract auth from the route SIGNATURE LINE (middlewares before the handler)
            // and from any middleware references in the handler body
            String routeSignature = lines[i];
            // Continue capturing until we hit `{` (end of signature)
            for (int k = i + 1; k <= handlerStart && k < lines.length; k++) {
                routeSignature += " " + lines[k];
            }
            extractAuthFrom(routeSignature, fullCode, routeName, filePath, i + 1, findings);

            // Other operations from the combined body
            extractTablesFrom(fullCode, routeName, filePath, i + 1, findings);
            extractColumnsFrom(fullCode, routeName, filePath, i + 1, findings);
            extractEmitsFrom(fullCode, routeName, filePath, i + 1, findings);
            extractCachesFrom(fullCode, routeName, filePath, i + 1, findings);
            extractTransactionsFrom(fullCode, routeName, filePath, i + 1, findings);
            extractParamsFrom(body, routeName, filePath, i + 1, findings);
        }

        return findings;
    }

    @Override
    public boolean supports(String filePath) {
        return filePath.endsWith(".js") || filePath.endsWith(".ts");
    }

    /**
     * Recursively collect bodies of all functions called from `body`
     * that are defined in this file's `functions` map.
     */
    private void collectReachableBodies(
            String body,
            Map<String, String> functions,
            Set<String> visited,
            StringBuilder accumulator,
            int depth,
            int maxDepth) {
        if (depth >= maxDepth) return;

        Matcher m = FUNCTION_CALL.matcher(body);
        while (m.find()) {
            String name = m.group(1);
            if (isBuiltin(name)) continue;
            if (!functions.containsKey(name)) continue;
            if (!visited.add(name)) continue;

            String funcBody = functions.get(name);
            accumulator.append("\n").append(funcBody);
            collectReachableBodies(funcBody, functions, visited, accumulator, depth + 1, maxDepth);
        }
    }

    // Detect SQL operation type around a position in code
    private static final Pattern SQL_OP_AROUND = Pattern.compile(
        "\\b(SELECT|INSERT|UPDATE|DELETE)\\b",
        Pattern.CASE_INSENSITIVE
    );

    private void extractTablesFrom(String code, String routeName, String filePath, int line, List<RawFinding> findings) {
        var seen = new HashMap<String, String>(); // table -> operation
        Matcher m = SQL_TABLE_REF.matcher(code);
        while (m.find()) {
            String table = m.group(1).toLowerCase();
            if (isReservedSql(table)) continue;
            if (table.length() < 3) continue;
            // Look 80 chars back for SQL operation keyword
            int start = Math.max(0, m.start() - 80);
            String window = code.substring(start, m.start());
            String op = "QUERY";
            Matcher opM = SQL_OP_AROUND.matcher(window);
            String last = null;
            while (opM.find()) last = opM.group(1);
            if (last != null) op = last.toUpperCase();
            seen.putIfAbsent(table, op);
        }
        Matcher mw = WRAPPER_TABLE.matcher(code);
        while (mw.find()) {
            String table = mw.group(1).toLowerCase();
            if (isReservedSql(table)) continue;
            if (table.length() < 3) continue;
            // batchInsert/insertMany → INSERT, deleteFrom → DELETE, etc
            String fnName = mw.group(0).toLowerCase();
            String op = "QUERY";
            if (fnName.contains("insert")) op = "INSERT";
            else if (fnName.contains("delete") || fnName.contains("truncate")) op = "DELETE";
            else if (fnName.contains("update")) op = "UPDATE";
            seen.putIfAbsent(table, op);
        }
        for (var entry : seen.entrySet()) {
            findings.add(new RawFinding(
                "ROUTE_DB",
                entry.getValue() + " " + entry.getKey(),
                "table: " + entry.getKey(),
                filePath, line, routeName
            ));
        }
    }

    private void extractAuthFrom(String signature, String body, String routeName, String filePath, int line, List<RawFinding> findings) {
        var seen = new HashSet<String>();

        // 1) Auth middlewares in route signature: router.post('/x', authenticateToken, requireRole('admin'), handler)
        Matcher sm = ROUTE_MIDDLEWARES_LINE.matcher(signature);
        if (sm.find()) {
            String middlewareSection = sm.group(1);
            // Strip everything after the handler arrow function start
            int arrowIdx = middlewareSection.indexOf("=>");
            if (arrowIdx == -1) arrowIdx = middlewareSection.indexOf("function");
            if (arrowIdx > 0) middlewareSection = middlewareSection.substring(0, arrowIdx);

            Matcher am = AUTH_NAME.matcher(middlewareSection);
            while (am.find()) {
                String name = am.group(1);
                if (seen.add(name)) {
                    findings.add(new RawFinding(
                        "ROUTE_AUTH", routeName + " -> " + name, "auth: " + name,
                        filePath, line, routeName
                    ));
                }
            }
        }

        // 2) Inline auth checks: if (!req.user) ... or if (req.user.role !== 'admin')
        if (body.contains("req.user") || body.contains("req.session") || body.contains("authToken")) {
            if (!seen.contains("inlineAuth")) {
                findings.add(new RawFinding(
                    "ROUTE_AUTH", routeName + " -> inlineAuth", "auth: inline check",
                    filePath, line, routeName
                ));
                seen.add("inlineAuth");
            }
        }

        // 3) Role checks: requireRole('admin'), checkRole, hasPermission
        Matcher roleM = Pattern.compile("require(?:Role|Permission)\\s*\\(\\s*['\"](\\w+)['\"]").matcher(body + " " + signature);
        while (roleM.find()) {
            String role = "role:" + roleM.group(1);
            if (seen.add(role)) {
                findings.add(new RawFinding(
                    "ROUTE_AUTH", routeName + " -> " + role, "role: " + roleM.group(1),
                    filePath, line, routeName
                ));
            }
        }
    }

    // === COLUMN EXTRACTION ===

    // INSERT INTO table (col1, col2, ...) VALUES — most precise
    private static final Pattern INSERT_COLUMNS = Pattern.compile(
        "INSERT\\s+INTO\\s+`?(\\w+)`?\\s*\\(([^)]+)\\)",
        Pattern.CASE_INSENSITIVE
    );

    // UPDATE table SET col1 = ?, col2 = ?
    private static final Pattern UPDATE_SET = Pattern.compile(
        "UPDATE\\s+`?(\\w+)`?\\s+SET\\s+(.+?)(?:\\s+WHERE|\\s*[`'\"]\\s*[,)])",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // SELECT col1, col2 FROM table   (excluding SELECT *)
    private static final Pattern SELECT_FROM = Pattern.compile(
        "SELECT\\s+(?!\\*)(.+?)\\s+FROM\\s+`?(\\w+)`?",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // SELECT * FROM table — wildcard
    private static final Pattern SELECT_STAR = Pattern.compile(
        "SELECT\\s+\\*\\s+FROM\\s+`?(\\w+)`?",
        Pattern.CASE_INSENSITIVE
    );

    // WHERE col = ? AND col2 > ?
    private static final Pattern WHERE_CONDITION = Pattern.compile(
        "WHERE\\s+(.+?)(?:\\s+(?:ORDER|GROUP|LIMIT|FOR\\s+UPDATE)|\\s*[`'\"]\\s*[,)])",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private void extractColumnsFrom(String code, String routeName, String filePath, int line, List<RawFinding> findings) {
        // table -> set of columns it touches
        Map<String, Set<String>> tableColumns = new HashMap<>();

        // 1) INSERT INTO orders (col1, col2, col3)
        Matcher mi = INSERT_COLUMNS.matcher(code);
        while (mi.find()) {
            String table = mi.group(1).toLowerCase();
            String cols = mi.group(2);
            for (String c : splitColumnList(cols)) {
                tableColumns.computeIfAbsent(table, k -> new LinkedHashSet<>()).add(c);
            }
        }

        // 2) UPDATE orders SET col1 = ?, col2 = ?
        Matcher mu = UPDATE_SET.matcher(code);
        while (mu.find()) {
            String table = mu.group(1).toLowerCase();
            String setClause = mu.group(2);
            for (String c : extractAssignedColumns(setClause)) {
                tableColumns.computeIfAbsent(table, k -> new LinkedHashSet<>()).add(c);
            }
        }

        // 3) SELECT col1, col2 FROM orders
        Matcher ms = SELECT_FROM.matcher(code);
        while (ms.find()) {
            String selectList = ms.group(1);
            String table = ms.group(2).toLowerCase();
            if (isReservedSql(table)) continue;
            for (String c : splitSelectList(selectList)) {
                tableColumns.computeIfAbsent(table, k -> new LinkedHashSet<>()).add(c);
            }
        }

        // 4) SELECT * FROM orders — mark as wildcard
        Matcher mss = SELECT_STAR.matcher(code);
        while (mss.find()) {
            String table = mss.group(1).toLowerCase();
            if (isReservedSql(table)) continue;
            tableColumns.computeIfAbsent(table, k -> new LinkedHashSet<>()).add("*");
        }

        // Emit findings
        for (var entry : tableColumns.entrySet()) {
            String table = entry.getKey();
            for (String col : entry.getValue()) {
                findings.add(new RawFinding(
                    "ROUTE_DB_COLUMN",
                    routeName + " -> " + table + "." + col,
                    "column: " + table + "." + col,
                    filePath, line, routeName
                ));
            }
        }
    }

    /** Split "col1, col2, col3" → [col1, col2, col3]. Strips backticks/quotes. */
    private List<String> splitColumnList(String list) {
        var result = new ArrayList<String>();
        for (String part : list.split(",")) {
            String c = part.trim().replaceAll("[`'\"]", "");
            if (c.matches("[a-z_][a-z0-9_]*")) result.add(c.toLowerCase());
        }
        return result;
    }

    /** Extract column names from "col1 = ?, col2 = NOW(), col3 = ?" */
    private List<String> extractAssignedColumns(String setClause) {
        var result = new ArrayList<String>();
        // Match "name =" patterns
        Matcher m = Pattern.compile("([a-z_][a-z0-9_]*)\\s*=").matcher(setClause);
        while (m.find()) {
            result.add(m.group(1).toLowerCase());
        }
        return result;
    }

    /** Extract columns from a SELECT list "id, t.name AS user_name, COUNT(*) as cnt" */
    private List<String> splitSelectList(String list) {
        var result = new ArrayList<String>();
        for (String part : list.split(",")) {
            String p = part.trim();
            // Skip aggregations and complex expressions for safety
            if (p.contains("(") || p.length() > 60) continue;
            // Strip "AS alias", "tableAlias.col" → col
            p = p.replaceAll("(?i)\\s+AS\\s+\\w+", "");
            if (p.contains(".")) {
                String[] parts = p.split("\\.");
                p = parts[parts.length - 1];
            }
            p = p.replaceAll("[`'\"]", "").trim();
            if (p.matches("[a-z_][a-z0-9_]*")) result.add(p.toLowerCase());
        }
        return result;
    }

    private void extractTransactionsFrom(String code, String routeName, String filePath, int line, List<RawFinding> findings) {
        Matcher m = TRANSACTION.matcher(code);
        boolean hasBegin = false, hasCommit = false, hasRollback = false;
        while (m.find()) {
            String op = m.group(1);
            if ("beginTransaction".equals(op)) hasBegin = true;
            else if ("commit".equals(op)) hasCommit = true;
            else if ("rollback".equals(op)) hasRollback = true;
        }
        if (hasBegin || (hasCommit && hasRollback)) {
            findings.add(new RawFinding(
                "ROUTE_TX", routeName + " -> transaction", "uses DB transaction",
                filePath, line, routeName
            ));
        }
    }

    private void extractParamsFrom(String body, String routeName, String filePath, int line, List<RawFinding> findings) {
        var bodyFields = new LinkedHashSet<String>();
        var paramFields = new LinkedHashSet<String>();
        var queryFields = new LinkedHashSet<String>();

        // Destructuring
        Matcher bd = REQ_BODY_DESTRUCT.matcher(body);
        while (bd.find()) addFields(bodyFields, bd.group(1));
        Matcher pd = REQ_PARAMS_DESTRUCT.matcher(body);
        while (pd.find()) addFields(paramFields, pd.group(1));
        Matcher qd = REQ_QUERY_DESTRUCT.matcher(body);
        while (qd.find()) addFields(queryFields, qd.group(1));

        // Direct access
        Matcher bdir = REQ_BODY_DIRECT.matcher(body);
        while (bdir.find()) bodyFields.add(bdir.group(1));
        Matcher pdir = REQ_PARAMS_DIRECT.matcher(body);
        while (pdir.find()) paramFields.add(pdir.group(1));
        Matcher qdir = REQ_QUERY_DIRECT.matcher(body);
        while (qdir.find()) queryFields.add(qdir.group(1));

        if (!bodyFields.isEmpty()) {
            findings.add(new RawFinding(
                "ROUTE_PARAMS", routeName + " -> body", "body: " + String.join(", ", bodyFields),
                filePath, line, routeName
            ));
        }
        if (!paramFields.isEmpty()) {
            findings.add(new RawFinding(
                "ROUTE_PARAMS", routeName + " -> params", "params: " + String.join(", ", paramFields),
                filePath, line, routeName
            ));
        }
        if (!queryFields.isEmpty()) {
            findings.add(new RawFinding(
                "ROUTE_PARAMS", routeName + " -> query", "query: " + String.join(", ", queryFields),
                filePath, line, routeName
            ));
        }
    }

    private void addFields(Set<String> target, String destructuredList) {
        // "x, y, z" or "x: alias, y" or "x = default"
        for (String f : destructuredList.split(",")) {
            String name = f.trim().split("[:=\\s]")[0].trim();
            if (!name.isEmpty()) target.add(name);
        }
    }

    private void extractEmitsFrom(String code, String routeName, String filePath, int line, List<RawFinding> findings) {
        var seen = new HashSet<String>();
        Matcher m = EMIT_PATTERN.matcher(code);
        while (m.find()) {
            // Try each capture group — only one will match per occurrence
            String event = null;
            for (int g = 1; g <= m.groupCount(); g++) {
                if (m.group(g) != null) { event = m.group(g); break; }
            }
            if (event == null || event.isBlank()) continue;
            if (isBuiltinEvent(event)) continue;
            if (!seen.add(event)) continue;
            findings.add(new RawFinding(
                "ROUTE_EMIT", routeName + " -> " + event, "event: " + event,
                filePath, line, routeName
            ));
        }
    }

    private void extractCachesFrom(String code, String routeName, String filePath, int line, List<RawFinding> findings) {
        var seen = new HashSet<String>();
        Matcher m = CACHE_PATTERN.matcher(code);
        while (m.find()) {
            String key = m.group(1);
            if (!seen.add(key)) continue;
            findings.add(new RawFinding(
                "ROUTE_CACHE", routeName + " -> " + key, "cache: " + key,
                filePath, line, routeName
            ));
        }
    }

    /**
     * Build map of function name -> body text by scanning the file.
     */
    private Map<String, String> extractFunctions(String[] lines) {
        var map = new HashMap<String, String>();
        for (int i = 0; i < lines.length; i++) {
            Matcher m = FUNCTION_DEF.matcher(lines[i]);
            if (!m.find()) continue;
            String name = m.group(1) != null ? m.group(1) : m.group(2);
            if (name == null) continue;

            int blockStart = findOpenBraceStart(lines, i);
            if (blockStart == -1) continue;
            int blockEnd = matchClosingBrace(lines, blockStart);
            if (blockEnd == -1) continue;

            map.put(name, joinLines(lines, blockStart, blockEnd));
        }
        return map;
    }

    /**
     * Find the line index where the route handler's body opens (the line with `{` after the route signature).
     */
    private int findHandlerBlockStart(String[] lines, int routeLine) {
        // Look ahead within ~5 lines for an opening brace `{`
        for (int i = routeLine; i < Math.min(routeLine + 8, lines.length); i++) {
            if (lines[i].contains("{")) return i;
        }
        return -1;
    }

    private int findOpenBraceStart(String[] lines, int defLine) {
        for (int i = defLine; i < Math.min(defLine + 5, lines.length); i++) {
            if (lines[i].contains("{")) return i;
        }
        return -1;
    }

    /**
     * Match closing brace for the block starting at `startLine`.
     * Counts braces from the first `{` on startLine onward.
     */
    private int matchClosingBrace(String[] lines, int startLine) {
        int depth = 0;
        boolean inString = false;
        char stringChar = 0;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean started = false;

        for (int i = startLine; i < lines.length; i++) {
            String line = lines[i];
            inLineComment = false;
            for (int j = 0; j < line.length(); j++) {
                char c = line.charAt(j);
                char next = j + 1 < line.length() ? line.charAt(j + 1) : 0;

                if (inLineComment) continue;
                if (inBlockComment) {
                    if (c == '*' && next == '/') { inBlockComment = false; j++; }
                    continue;
                }
                if (inString) {
                    if (c == '\\') { j++; continue; }
                    if (c == stringChar) inString = false;
                    continue;
                }

                if (c == '/' && next == '/') { inLineComment = true; break; }
                if (c == '/' && next == '*') { inBlockComment = true; j++; continue; }
                if (c == '\'' || c == '"' || c == '`') { inString = true; stringChar = c; continue; }

                if (c == '{') { depth++; started = true; }
                else if (c == '}') {
                    depth--;
                    if (started && depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private String joinLines(String[] lines, int from, int to) {
        var sb = new StringBuilder();
        for (int i = from; i <= Math.min(to, lines.length - 1); i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString();
    }

    private static final Set<String> BUILTINS = Set.of(
        "if", "else", "for", "while", "switch", "case", "return", "throw",
        "new", "typeof", "instanceof", "async", "await", "try", "catch", "finally",
        "const", "let", "var", "function", "require", "import", "export",
        "console", "JSON", "Object", "Array", "String", "Number", "Math", "Date",
        "parseInt", "parseFloat", "setTimeout", "setInterval", "clearTimeout",
        "clearInterval", "Promise", "Error", "RegExp", "Map", "Set", "Boolean",
        "isNaN", "isFinite", "encodeURIComponent", "decodeURIComponent",
        "Buffer", "process", "module"
    );

    private boolean isBuiltin(String name) {
        return BUILTINS.contains(name);
    }

    private boolean isBuiltinEvent(String event) {
        return Set.of("connection", "disconnect", "error", "connect",
            "connect_error", "reconnect", "ping", "pong", "close", "open"
        ).contains(event);
    }

    private static final Set<String> SQL_RESERVED = Set.of(
        "select", "from", "where", "join", "inner", "left", "right", "outer", "on",
        "insert", "into", "update", "delete", "create", "alter", "drop", "table",
        "set", "values", "and", "or", "not", "null", "true", "false",
        "group", "by", "having", "limit", "offset", "as", "in", "order",
        "like", "between", "exists", "case", "when", "then", "else", "end",
        "count", "sum", "avg", "min", "max", "distinct", "all", "asc", "desc",
        "primary", "key", "foreign", "references", "default", "constraint",
        "unique", "check", "column", "if", "int", "varchar", "text", "date",
        "timestamp", "boolean", "bigint", "unsigned", "character", "using",
        "information_schema", "mysql", "performance_schema", "sys",
        "pg_catalog", "sqlite_master", "dual"
    );

    private boolean isReservedSql(String word) {
        return SQL_RESERVED.contains(word);
    }
}
