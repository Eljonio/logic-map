package com.logicmap.service.parser;

import com.logicmap.model.RawFinding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ExpressRouteParser implements FileParser {

    // router.get('/path', ...) or app.post('/path', ...)
    private static final Pattern ROUTE_PATTERN = Pattern.compile(
        "(?:router|app)\\.(get|post|put|delete|patch)\\s*\\(\\s*['\"]([^'\"]+)['\"]"
    );

    // router.use('/path', ...) for middleware mount
    private static final Pattern USE_PATTERN = Pattern.compile(
        "(?:router|app)\\.use\\s*\\(\\s*['\"]([^'\"]+)['\"]"
    );

    @Override
    public List<RawFinding> parse(String filePath, String content) {
        var findings = new ArrayList<RawFinding>();
        var lines = content.split("\n");

        for (int i = 0; i < lines.length; i++) {
            var line = lines[i];

            Matcher routeMatcher = ROUTE_PATTERN.matcher(line);
            while (routeMatcher.find()) {
                var method = routeMatcher.group(1).toUpperCase();
                var path = routeMatcher.group(2);
                var calledFrom = extractHandler(line);

                findings.add(new RawFinding(
                    "ROUTE",
                    method + " " + path,
                    "handler: " + calledFrom,
                    filePath,
                    i + 1,
                    calledFrom
                ));
            }

            Matcher useMatcher = USE_PATTERN.matcher(line);
            while (useMatcher.find()) {
                var path = useMatcher.group(1);
                findings.add(new RawFinding(
                    "MIDDLEWARE",
                    "USE " + path,
                    "",
                    filePath,
                    i + 1,
                    ""
                ));
            }
        }

        return findings;
    }

    @Override
    public boolean supports(String filePath) {
        return filePath.endsWith(".js") || filePath.endsWith(".ts");
    }

    private String extractHandler(String line) {
        // Try to find controller reference like menuController.getItems
        Pattern handlerPattern = Pattern.compile("(\\w+\\.\\w+)\\s*\\)");
        Matcher m = handlerPattern.matcher(line);
        if (m.find()) {
            return m.group(1);
        }
        // Try arrow function or function name
        Pattern funcPattern = Pattern.compile(",\\s*(\\w+)\\s*\\)");
        Matcher fm = funcPattern.matcher(line);
        if (fm.find()) {
            return fm.group(1);
        }
        return "anonymous";
    }
}
