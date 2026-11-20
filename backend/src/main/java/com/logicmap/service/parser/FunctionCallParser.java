package com.logicmap.service.parser;

import com.logicmap.model.RawFinding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses function definitions and their bodies to understand call chains.
 * Tracks: which functions are defined, what they call, and what SQL/emit they contain.
 */
@Component
public class FunctionCallParser implements FileParser {

    // Named function: function name(...) or async function name(...)
    private static final Pattern FUNC_DEF = Pattern.compile(
        "(?:async\\s+)?function\\s+(\\w+)\\s*\\("
    );

    // Arrow/const function: const name = (...) => or const name = async (...) =>
    private static final Pattern ARROW_DEF = Pattern.compile(
        "(?:const|let|var)\\s+(\\w+)\\s*=\\s*(?:async\\s*)?(?:\\([^)]*\\)|\\w+)\\s*=>"
    );

    // Method in object: name(...) { or name: function(...) or name: async (
    private static final Pattern METHOD_DEF = Pattern.compile(
        "^\\s*(\\w+)\\s*(?::\\s*(?:async\\s+)?function)?\\s*\\([^)]*\\)\\s*\\{"
    );

    // Function call: name(...) — captures the function name being called
    private static final Pattern FUNC_CALL = Pattern.compile(
        "(?<!function\\s)(?<!\\.)\\b(\\w+)\\s*\\("
    );

    // Method call on imported object: object.method(...)
    private static final Pattern METHOD_CALL = Pattern.compile(
        "\\b(\\w+)\\.(\\w+)\\s*\\("
    );

    @Override
    public List<RawFinding> parse(String filePath, String content) {
        var findings = new ArrayList<RawFinding>();
        var lines = content.split("\n");

        // First pass: find all function definitions and their line ranges
        var functions = findFunctions(lines);

        // Second pass: for each function, find what it calls
        for (var func : functions) {
            var body = getBody(lines, func.startLine, func.endLine);

            // Find all function calls within this function's body
            var calls = findCalls(body);
            for (var call : calls) {
                findings.add(new RawFinding(
                    "FUNC_CALL",
                    func.name + " -> " + call,
                    "caller: " + func.name,
                    filePath,
                    func.startLine + 1,
                    func.name
                ));
            }
        }

        // Also parse route handlers inline (anonymous functions in app.get/post etc)
        parseRouteHandlers(lines, filePath, findings);

        return findings;
    }

    @Override
    public boolean supports(String filePath) {
        return filePath.endsWith(".js") || filePath.endsWith(".ts");
    }

    private void parseRouteHandlers(String[] lines, String filePath, List<RawFinding> findings) {
        var routePattern = Pattern.compile(
            "(?:router|app)\\.(get|post|put|delete|patch)\\s*\\(\\s*['\"]([^'\"]+)['\"]"
        );

        for (int i = 0; i < lines.length; i++) {
            Matcher m = routePattern.matcher(lines[i]);
            if (m.find()) {
                var method = m.group(1).toUpperCase();
                var path = m.group(2);
                var routeName = method + " " + path;

                // Find the end of this route handler
                var endLine = findBlockEnd(lines, i);
                var body = getBody(lines, i, endLine);
                var calls = findCalls(body);

                for (var call : calls) {
                    findings.add(new RawFinding(
                        "ROUTE_CALLS",
                        routeName + " -> " + call,
                        "route: " + routeName,
                        filePath,
                        i + 1,
                        routeName
                    ));
                }
            }
        }
    }

    private List<FuncInfo> findFunctions(String[] lines) {
        var functions = new ArrayList<FuncInfo>();

        for (int i = 0; i < lines.length; i++) {
            var line = lines[i];
            String name = null;

            Matcher m1 = FUNC_DEF.matcher(line);
            if (m1.find()) name = m1.group(1);

            if (name == null) {
                Matcher m2 = ARROW_DEF.matcher(line);
                if (m2.find()) name = m2.group(1);
            }

            if (name != null) {
                var endLine = findBlockEnd(lines, i);
                functions.add(new FuncInfo(name, i, endLine));
            }
        }

        return functions;
    }

    private int findBlockEnd(String[] lines, int startLine) {
        int braceCount = 0;
        boolean started = false;

        for (int i = startLine; i < lines.length; i++) {
            var line = lines[i];
            for (char c : line.toCharArray()) {
                if (c == '{') {
                    braceCount++;
                    started = true;
                } else if (c == '}') {
                    braceCount--;
                    if (started && braceCount <= 0) {
                        return i;
                    }
                }
            }
        }

        // If we can't find the end, return a reasonable range
        return Math.min(startLine + 50, lines.length - 1);
    }

    private String getBody(String[] lines, int start, int end) {
        var sb = new StringBuilder();
        for (int i = start; i <= Math.min(end, lines.length - 1); i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString();
    }

    private List<String> findCalls(String body) {
        var calls = new ArrayList<String>();
        var keywords = List.of(
            "if", "else", "for", "while", "switch", "case", "return", "throw",
            "new", "typeof", "instanceof", "async", "await", "try", "catch",
            "const", "let", "var", "function", "require", "import", "export",
            "console", "JSON", "Object", "Array", "String", "Number", "Math",
            "parseInt", "parseFloat", "setTimeout", "setInterval", "clearTimeout",
            "clearInterval", "Promise", "Error", "Date", "RegExp", "Map", "Set"
        );

        // Find standalone function calls: name(...)
        Matcher m = FUNC_CALL.matcher(body);
        while (m.find()) {
            var name = m.group(1);
            if (!keywords.contains(name) && !calls.contains(name)) {
                calls.add(name);
            }
        }

        // Find method calls: obj.method(...)
        Matcher m2 = METHOD_CALL.matcher(body);
        while (m2.find()) {
            var obj = m2.group(1);
            var method = m2.group(2);
            var full = obj + "." + method;
            if (!keywords.contains(obj) && !calls.contains(full)) {
                calls.add(full);
            }
        }

        return calls;
    }

    private record FuncInfo(String name, int startLine, int endLine) {}
}
