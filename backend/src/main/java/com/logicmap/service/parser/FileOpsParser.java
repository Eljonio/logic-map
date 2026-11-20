package com.logicmap.service.parser;

import com.logicmap.model.RawFinding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds file system operations: fs.readFile, fs.writeFile, etc.
 */
@Component
public class FileOpsParser implements FileParser {

    private static final Pattern FS_OP = Pattern.compile(
        "fs(?:\\.promises)?\\s*\\.\\s*(readFile|writeFile|readFileSync|writeFileSync|appendFile|unlink|readdir|mkdir|rename|copyFile|stat|access)\\s*\\("
    );

    private static final Pattern FS_PATH = Pattern.compile(
        "fs(?:\\.promises)?\\s*\\.\\s*\\w+\\s*\\(\\s*[`'\"]([^`'\"]+)[`'\"]"
    );

    @Override
    public List<RawFinding> parse(String filePath, String content) {
        var findings = new ArrayList<RawFinding>();
        var lines = content.split("\n");
        var currentFunction = "unknown";

        for (int i = 0; i < lines.length; i++) {
            var line = lines[i];

            var funcName = detectFunctionName(line);
            if (funcName != null) currentFunction = funcName;

            Matcher opM = FS_OP.matcher(line);
            while (opM.find()) {
                var operation = opM.group(1);
                var path = extractPath(line);

                findings.add(new RawFinding(
                    "FILE_OP",
                    "fs." + operation + (path != null ? ": " + path : ""),
                    "operation: " + operation,
                    filePath, i + 1, currentFunction
                ));
            }
        }

        return findings;
    }

    @Override
    public boolean supports(String filePath) {
        return filePath.endsWith(".js") || filePath.endsWith(".ts");
    }

    private String extractPath(String line) {
        Matcher m = FS_PATH.matcher(line);
        if (m.find()) return m.group(1);
        return null;
    }

    private String detectFunctionName(String line) {
        Pattern funcPattern = Pattern.compile(
            "(?:async\\s+)?(?:function\\s+(\\w+)|(?:const|let|var)\\s+(\\w+)\\s*=)"
        );
        Matcher m = funcPattern.matcher(line);
        if (m.find()) return m.group(1) != null ? m.group(1) : m.group(2);
        return null;
    }
}
