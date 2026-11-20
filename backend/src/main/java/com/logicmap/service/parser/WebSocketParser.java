package com.logicmap.service.parser;

import com.logicmap.model.RawFinding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WebSocketParser implements FileParser {

    // socket.emit('event', ...) or io.emit('event', ...)
    private static final Pattern EMIT_PATTERN = Pattern.compile(
        "(?:socket|io|ws)\\s*\\.\\s*emit\\s*\\(\\s*['\"]([^'\"]+)['\"]"
    );

    // socket.on('event', ...) or io.on('event', ...)
    private static final Pattern ON_PATTERN = Pattern.compile(
        "(?:socket|io|ws)\\s*\\.\\s*on\\s*\\(\\s*['\"]([^'\"]+)['\"]"
    );

    // socket.to(room).emit('event', ...)
    private static final Pattern TO_EMIT_PATTERN = Pattern.compile(
        "(?:socket|io)\\s*\\.\\s*to\\s*\\([^)]+\\)\\s*\\.\\s*emit\\s*\\(\\s*['\"]([^'\"]+)['\"]"
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

            Matcher toEmitMatcher = TO_EMIT_PATTERN.matcher(line);
            while (toEmitMatcher.find()) {
                findings.add(new RawFinding(
                    "SOCKET_EMIT",
                    "emit: " + toEmitMatcher.group(1),
                    "targeted emit",
                    filePath,
                    i + 1,
                    currentFunction
                ));
            }

            // Only match regular emit if not already matched as to().emit()
            if (!TO_EMIT_PATTERN.matcher(line).find()) {
                Matcher emitMatcher = EMIT_PATTERN.matcher(line);
                while (emitMatcher.find()) {
                    findings.add(new RawFinding(
                        "SOCKET_EMIT",
                        "emit: " + emitMatcher.group(1),
                        "broadcast",
                        filePath,
                        i + 1,
                        currentFunction
                    ));
                }
            }

            Matcher onMatcher = ON_PATTERN.matcher(line);
            while (onMatcher.find()) {
                var event = onMatcher.group(1);
                if (!isBuiltinEvent(event)) {
                    findings.add(new RawFinding(
                        "SOCKET_ON",
                        "on: " + event,
                        "listener",
                        filePath,
                        i + 1,
                        currentFunction
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
            "(?:async\\s+)?(?:function\\s+(\\w+)|(?:const|let|var)\\s+(\\w+)\\s*=)"
        );
        Matcher m = funcPattern.matcher(line);
        if (m.find()) {
            return m.group(1) != null ? m.group(1) : m.group(2);
        }
        return null;
    }

    private boolean isBuiltinEvent(String event) {
        return List.of("connection", "disconnect", "error", "connect",
            "connect_error", "reconnect", "ping", "pong"
        ).contains(event);
    }
}
