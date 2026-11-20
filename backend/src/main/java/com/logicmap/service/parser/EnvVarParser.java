package com.logicmap.service.parser;

import com.logicmap.model.RawFinding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds environment variable usage: process.env.X, os.environ, etc.
 */
@Component
public class EnvVarParser implements FileParser {

    // process.env.VARIABLE_NAME
    private static final Pattern PROCESS_ENV = Pattern.compile(
        "process\\.env\\.(\\w+)"
    );

    // process.env['VARIABLE_NAME'] or process.env["VARIABLE_NAME"]
    private static final Pattern PROCESS_ENV_BRACKET = Pattern.compile(
        "process\\.env\\[['\"]([^'\"]+)['\"]\\]"
    );

    @Override
    public List<RawFinding> parse(String filePath, String content) {
        var findings = new ArrayList<RawFinding>();
        var lines = content.split("\n");
        var seenVars = new java.util.HashSet<String>();

        for (int i = 0; i < lines.length; i++) {
            var line = lines[i];
            if (isComment(line)) continue;

            Matcher m1 = PROCESS_ENV.matcher(line);
            while (m1.find()) {
                var varName = m1.group(1);
                if (seenVars.add(varName) && !isBoringEnvVar(varName)) {
                    findings.add(new RawFinding(
                        "ENV_VAR", varName, "env variable",
                        filePath, i + 1, ""
                    ));
                }
            }

            Matcher m2 = PROCESS_ENV_BRACKET.matcher(line);
            while (m2.find()) {
                var varName = m2.group(1);
                if (seenVars.add(varName) && !isBoringEnvVar(varName)) {
                    findings.add(new RawFinding(
                        "ENV_VAR", varName, "env variable",
                        filePath, i + 1, ""
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

    private boolean isBoringEnvVar(String name) {
        return List.of("NODE_ENV", "PATH", "HOME", "USER", "LANG", "SHELL", "PWD").contains(name);
    }

    private boolean isComment(String line) {
        var trimmed = line.trim();
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
    }
}
