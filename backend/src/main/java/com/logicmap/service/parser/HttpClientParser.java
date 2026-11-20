package com.logicmap.service.parser;

import com.logicmap.model.RawFinding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds HTTP requests to external services:
 * fetch(), axios, http.request, got, superagent, etc.
 */
@Component
public class HttpClientParser implements FileParser {

    private static final Pattern FETCH_PATTERN = Pattern.compile(
        "fetch\\s*\\(\\s*[`'\"]([^`'\"]+)[`'\"]"
    );

    private static final Pattern AXIOS_PATTERN = Pattern.compile(
        "axios\\s*\\.\\s*(get|post|put|delete|patch)\\s*\\(\\s*[`'\"]([^`'\"]+)[`'\"]",
        Pattern.CASE_INSENSITIVE
    );

    // Generic URL in http/https calls
    private static final Pattern URL_PATTERN = Pattern.compile(
        "['\"`](https?://[^'\"`\\s]+)['\"`]"
    );

    // Template literal with variable: `${baseUrl}/api/...`
    private static final Pattern TEMPLATE_URL = Pattern.compile(
        "`\\$\\{\\w+}(/[^`]+)`"
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

            // fetch()
            Matcher fetchM = FETCH_PATTERN.matcher(line);
            while (fetchM.find()) {
                findings.add(new RawFinding(
                    "HTTP_REQUEST", fetchM.group(1), "fetch",
                    filePath, i + 1, currentFunction
                ));
            }

            // axios.get/post/...
            Matcher axiosM = AXIOS_PATTERN.matcher(line);
            while (axiosM.find()) {
                findings.add(new RawFinding(
                    "HTTP_REQUEST",
                    axiosM.group(1).toUpperCase() + " " + axiosM.group(2),
                    "axios",
                    filePath, i + 1, currentFunction
                ));
            }

            // Template URLs
            Matcher templateM = TEMPLATE_URL.matcher(line);
            while (templateM.find()) {
                findings.add(new RawFinding(
                    "HTTP_REQUEST", templateM.group(1), "template",
                    filePath, i + 1, currentFunction
                ));
            }

            // Raw URLs (only if not already matched)
            if (!FETCH_PATTERN.matcher(line).find() && !AXIOS_PATTERN.matcher(line).find()) {
                Matcher urlM = URL_PATTERN.matcher(line);
                while (urlM.find()) {
                    var url = urlM.group(1);
                    if (!isStaticAsset(url) && !isComment(line)) {
                        findings.add(new RawFinding(
                            "HTTP_REQUEST", url, "url",
                            filePath, i + 1, currentFunction
                        ));
                    }
                }
            }
        }

        return findings;
    }

    @Override
    public boolean supports(String filePath) {
        return filePath.endsWith(".js") || filePath.endsWith(".ts") ||
               filePath.endsWith(".jsx") || filePath.endsWith(".tsx");
    }

    private boolean isStaticAsset(String url) {
        return url.endsWith(".css") || url.endsWith(".js") || url.endsWith(".png") ||
               url.endsWith(".jpg") || url.endsWith(".svg") || url.endsWith(".ico") ||
               url.contains("cdn.") || url.contains("fonts.");
    }

    private boolean isComment(String line) {
        var trimmed = line.trim();
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
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
