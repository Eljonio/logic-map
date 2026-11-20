package com.logicmap.model;

public record RawFinding(
    String type,
    String name,
    String detail,
    String file,
    int line,
    String calledFrom
) {
    public enum Type {
        ROUTE,
        DB_QUERY,
        SOCKET_EVENT,
        CACHE_OPERATION,
        IMPORT
    }
}
