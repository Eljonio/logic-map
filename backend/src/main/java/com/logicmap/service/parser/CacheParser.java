package com.logicmap.service.parser;

import com.logicmap.model.RawFinding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CacheParser implements FileParser {

    // redis.get/set/del or cacheGet/cacheSet/cacheInvalidate
    private static final Pattern CACHE_GET = Pattern.compile(
        "(?:redis|cache|cacheGet|client)\\s*\\.\\s*(?:get|hget|hgetall|mget)\\s*\\(\\s*['\"`]([^'\"`]+)['\"`]",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CACHE_SET = Pattern.compile(
        "(?:redis|cache|cacheSet|client)\\s*\\.\\s*(?:set|hset|setex|mset)\\s*\\(\\s*['\"`]([^'\"`]+)['\"`]",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CACHE_DEL = Pattern.compile(
        "(?:redis|cache|cacheInvalidate|client)\\s*\\.\\s*(?:del|hdel|flushdb|invalidate)\\s*\\(\\s*['\"`]([^'\"`]+)['\"`]",
        Pattern.CASE_INSENSITIVE
    );

    // cacheGet('key') / cacheSet('key') helper pattern
    private static final Pattern CACHE_HELPER = Pattern.compile(
        "(cacheGet|cacheSet|cacheInvalidate)\\s*\\(\\s*['\"`]([^'\"`]+)['\"`]"
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

            Matcher helperMatcher = CACHE_HELPER.matcher(line);
            while (helperMatcher.find()) {
                var op = helperMatcher.group(1);
                var key = helperMatcher.group(2);
                findings.add(new RawFinding(
                    "CACHE",
                    op + ": " + key,
                    "cache operation",
                    filePath,
                    i + 1,
                    currentFunction
                ));
            }

            if (!CACHE_HELPER.matcher(line).find()) {
                addCacheFindings(findings, CACHE_GET, "cache_get", line, filePath, i, currentFunction);
                addCacheFindings(findings, CACHE_SET, "cache_set", line, filePath, i, currentFunction);
                addCacheFindings(findings, CACHE_DEL, "cache_del", line, filePath, i, currentFunction);
            }
        }

        return findings;
    }

    @Override
    public boolean supports(String filePath) {
        return filePath.endsWith(".js") || filePath.endsWith(".ts");
    }

    private void addCacheFindings(List<RawFinding> findings, Pattern pattern, String type,
                                   String line, String filePath, int lineNum, String calledFrom) {
        Matcher m = pattern.matcher(line);
        while (m.find()) {
            findings.add(new RawFinding(
                "CACHE",
                type + ": " + m.group(1),
                "cache operation",
                filePath,
                lineNum + 1,
                calledFrom
            ));
        }
    }

    private String detectFunctionName(String line) {
        Pattern funcPattern = Pattern.compile(
            "(?:async\\s+)?(?:function\\s+(\\w+)|(?:const|let|var)\\s+(\\w+)\\s*=)"
        );
        Matcher m = funcPattern.matcher(line);
        if (m.find()) {
            return m.group(1) != null ? m.group(1) : m.group(2);
        }
        return null;
    }
}
