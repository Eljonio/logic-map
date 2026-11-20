package com.logicmap.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies which table names are real by checking:
 * 1. SQL migration files (CREATE TABLE)
 * 2. Live database connection (SHOW TABLES)
 */
@Service
public class TableVerifier {

    private static final Pattern CREATE_TABLE = Pattern.compile(
        "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[`'\"]?(\\w+)[`'\"]?",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Get real table names from SQL migration files
     */
    public Set<String> fromMigrations(String migrationsPath) {
        var tables = new HashSet<String>();
        var path = Path.of(migrationsPath);

        if (!Files.isDirectory(path)) return tables;

        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    var name = file.toString();
                    if (name.endsWith(".sql")) {
                        try {
                            var content = Files.readString(file);
                            Matcher m = CREATE_TABLE.matcher(content);
                            while (m.find()) {
                                tables.add(m.group(1).toLowerCase());
                            }
                        } catch (IOException e) {
                            // skip unreadable files
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // skip
        }

        return tables;
    }

    /**
     * Get real table names from a live database
     */
    public Set<String> fromDatabase(String jdbcUrl, String user, String password) {
        var tables = new HashSet<String>();

        try (var conn = DriverManager.getConnection(jdbcUrl, user, password)) {
            var meta = conn.getMetaData();
            var rs = meta.getTables(null, null, "%", new String[]{"TABLE"});
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME").toLowerCase());
            }
        } catch (SQLException e) {
            System.err.println("DB verification failed: " + e.getMessage());
        }

        return tables;
    }

    /**
     * Auto-reject scanner findings that aren't real tables.
     * Returns set of labels that were rejected.
     */
    public Set<String> autoFilter(Set<String> realTables, List<String> foundLabels) {
        var rejected = new HashSet<String>();
        for (var label : foundLabels) {
            if (!realTables.contains(label.toLowerCase())) {
                rejected.add(label);
            }
        }
        return rejected;
    }
}
