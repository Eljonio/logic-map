package com.logicmap.service.parser;

import com.logicmap.model.RawFinding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ImportParser implements FileParser {

    // const x = require('./file') or require('../file')
    private static final Pattern REQUIRE_PATTERN = Pattern.compile(
        "require\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)"
    );

    // import x from './file' or import { x } from './file'
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
        "import\\s+.*?from\\s+['\"]([^'\"]+)['\"]"
    );

    @Override
    public List<RawFinding> parse(String filePath, String content) {
        var findings = new ArrayList<RawFinding>();
        var lines = content.split("\n");

        for (int i = 0; i < lines.length; i++) {
            var line = lines[i];

            Matcher reqMatcher = REQUIRE_PATTERN.matcher(line);
            while (reqMatcher.find()) {
                var importPath = reqMatcher.group(1);
                if (isLocalImport(importPath)) {
                    findings.add(new RawFinding(
                        "IMPORT",
                        importPath,
                        "require",
                        filePath,
                        i + 1,
                        ""
                    ));
                }
            }

            Matcher impMatcher = IMPORT_PATTERN.matcher(line);
            while (impMatcher.find()) {
                var importPath = impMatcher.group(1);
                if (isLocalImport(importPath)) {
                    findings.add(new RawFinding(
                        "IMPORT",
                        importPath,
                        "import",
                        filePath,
                        i + 1,
                        ""
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

    private boolean isLocalImport(String path) {
        return path.startsWith("./") || path.startsWith("../");
    }
}
