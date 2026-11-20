package com.logicmap.service.parser;

import com.logicmap.model.RawFinding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DatabaseQueryParser implements FileParser {

    private static final Pattern SQL_FROM = Pattern.compile(
        "(?:FROM|JOIN|INTO|UPDATE|TABLE)\\s+[`'\"]?(\\w+)[`'\"]?",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern QUERY_CALL = Pattern.compile(
        "(?:db|pool|connection|conn|knex|sequelize)\\s*\\.\\s*(?:query|raw|execute)\\s*\\(",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SQL_OPERATION = Pattern.compile(
        "\\b(SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP)\\b",
        Pattern.CASE_INSENSITIVE
    );

    // Catches function calls with a string arg that looks like a table name
    // e.g. batchInsert(conn, 'orders', ...) or Model.findAll('users')
    private static final Pattern FUNC_WITH_TABLE_ARG = Pattern.compile(
        "\\w+\\s*\\(.*?['\"]([a-z][a-z0-9_]+)['\"]",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public List<RawFinding> parse(String filePath, String content) {
        var findings = new ArrayList<RawFinding>();
        var lines = content.split("\n");
        var currentFunction = "unknown";

        for (int i = 0; i < lines.length; i++) {
            var line = lines[i];

            var funcName = detectFunctionName(line);
            if (funcName != null) {
                currentFunction = funcName;
            }

            // SQL queries
            if (QUERY_CALL.matcher(line).find() || SQL_OPERATION.matcher(line).find()) {
                var block = getBlock(lines, i, 5);
                var tables = extractTables(block);
                var operation = extractOperation(block);

                for (var table : tables) {
                    findings.add(new RawFinding(
                        "DB_QUERY", operation + " " + table, "table: " + table,
                        filePath, i + 1, currentFunction
                    ));
                }
            }

            // Function calls with table-like string arguments
            Matcher funcM = FUNC_WITH_TABLE_ARG.matcher(line);
            while (funcM.find()) {
                var candidate = funcM.group(1).toLowerCase();
                // Accept any plausible identifier — verifier filters mistakes later
                if (!isSqlKeyword(candidate) && candidate.length() >= 4 && candidate.matches("[a-z][a-z0-9_]*")) {
                    findings.add(new RawFinding(
                        "DB_QUERY", "QUERY " + candidate, "table: " + candidate,
                        filePath, i + 1, currentFunction
                    ));
                }
            }
        }

        return findings;
    }

    @Override
    public boolean supports(String filePath) {
        return filePath.endsWith(".js") || filePath.endsWith(".ts");
    }

    private String detectFunctionName(String line) {
        Pattern funcPattern = Pattern.compile(
            "(?:async\\s+)?(?:function\\s+(\\w+)|(?:const|let|var)\\s+(\\w+)\\s*=\\s*(?:async\\s*)?(?:\\(|\\w+\\s*=>))"
        );
        Matcher m = funcPattern.matcher(line);
        if (m.find()) return m.group(1) != null ? m.group(1) : m.group(2);
        Pattern methodPattern = Pattern.compile("^\\s*(\\w+)\\s*(?::\\s*(?:async\\s+)?function)?\\s*\\(");
        Matcher mm = methodPattern.matcher(line);
        if (mm.find()) return mm.group(1);
        return null;
    }

    private String getBlock(String[] lines, int start, int count) {
        var sb = new StringBuilder();
        for (int i = start; i < Math.min(start + count, lines.length); i++) {
            sb.append(lines[i]).append(" ");
        }
        return sb.toString();
    }

    private List<String> extractTables(String block) {
        var tables = new ArrayList<String>();
        Matcher m = SQL_FROM.matcher(block);
        while (m.find()) {
            var table = m.group(1).toLowerCase();
            if (!isSqlKeyword(table) && table.length() > 2 && !tables.contains(table)) {
                tables.add(table);
            }
        }
        return tables;
    }

    private String extractOperation(String block) {
        Matcher m = SQL_OPERATION.matcher(block);
        if (m.find()) return m.group(1).toUpperCase();
        return "QUERY";
    }

    /**
     * Only SQL language keywords — this is SQL spec, not hardcoding.
     */
    private boolean isSqlKeyword(String word) {
        return SQL_KEYWORDS.contains(word);
    }

    private static final java.util.Set<String> SQL_KEYWORDS = java.util.Set.of(
        "select", "from", "where", "join", "inner", "left", "right", "outer", "on",
        "insert", "into", "update", "delete", "create", "alter", "drop", "table",
        "index", "set", "values", "and", "or", "not", "null", "true", "false",
        "group", "order", "by", "having", "limit", "offset", "as", "in",
        "like", "between", "exists", "case", "when", "then", "else", "end",
        "count", "sum", "avg", "min", "max", "distinct", "all",
        "asc", "desc", "primary", "key", "foreign", "references",
        "default", "constraint", "unique", "check", "column",
        "int", "varchar", "text", "date", "timestamp", "boolean",
        "bigint", "unsigned", "auto_increment", "engine", "charset",
        "collate", "character", "using", "type", "begin", "commit", "rollback",
        "information_schema", "mysql", "performance_schema", "sys",
        "pg_catalog", "pg_tables", "sqlite_master", "dual"
    );
}
