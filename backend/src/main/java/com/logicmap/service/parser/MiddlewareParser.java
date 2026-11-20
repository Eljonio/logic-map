package com.logicmap.service.parser;

import com.logicmap.model.RawFinding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds Express middleware: app.use(), router.use(), and middleware in route definitions.
 */
@Component
public class MiddlewareParser implements FileParser {

    // app.use('/path', middleware) or app.use(middleware)
    private static final Pattern USE_WITH_PATH = Pattern.compile(
        "(?:app|router)\\.use\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*(\\w+)"
    );

    private static final Pattern USE_PLAIN = Pattern.compile(
        "(?:app|router)\\.use\\s*\\(\\s*(\\w+)(?:\\.\\w+)?\\s*(?:\\)|,)"
    );

    // Middleware in route: router.get('/path', auth, validate, handler)
    private static final Pattern ROUTE_MIDDLEWARE = Pattern.compile(
        "(?:app|router)\\.(get|post|put|delete|patch)\\s*\\(\\s*['\"][^'\"]+['\"]\\s*,\\s*(.+?)\\s*\\)"
    );

    @Override
    public List<RawFinding> parse(String filePath, String content) {
        var findings = new ArrayList<RawFinding>();
        var lines = content.split("\n");

        for (int i = 0; i < lines.length; i++) {
            var line = lines[i];

            // app.use('/api', router)
            Matcher m1 = USE_WITH_PATH.matcher(line);
            while (m1.find()) {
                findings.add(new RawFinding(
                    "MIDDLEWARE",
                    "use " + m1.group(1) + " → " + m1.group(2),
                    "mount",
                    filePath, i + 1, ""
                ));
            }

            // app.use(cors())
            if (!USE_WITH_PATH.matcher(line).find()) {
                Matcher m2 = USE_PLAIN.matcher(line);
                while (m2.find()) {
                    var name = m2.group(1);
                    if (!isBuiltinMiddleware(name)) {
                        findings.add(new RawFinding(
                            "MIDDLEWARE",
                            "use " + name,
                            "global",
                            filePath, i + 1, ""
                        ));
                    }
                }
            }
        }

        return findings;
    }

    @Override
    public boolean supports(String filePath) {
        return filePath.endsWith(".js") || filePath.endsWith(".ts");
    }

    private boolean isBuiltinMiddleware(String name) {
        return List.of("express", "require", "path", "http", "https", "fs").contains(name);
    }
}
