package com.portal.auth.shared;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SimpleToml {
    private SimpleToml() {
    }

    public static Map<String, String> parseFlat(Path path) {
        Map<String, String> out = new HashMap<>();
        String section = "";
        List<String> lines;

        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return out;
        }

        for (String raw : lines) {
            String line = stripComment(raw).trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim();
                continue;
            }

            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }

            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (key.isEmpty()) {
                continue;
            }

            String qualified = section.isEmpty() ? key : section + "." + key;
            out.put(qualified, unquote(value));
        }

        return out;
    }

    private static String stripComment(String line) {
        boolean inString = false;
        boolean escape = false;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escape) {
                sb.append(c);
                escape = false;
                continue;
            }
            if (c == '\\') {
                sb.append(c);
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                sb.append(c);
                continue;
            }
            if (c == '#' && !inString) {
                break;
            }
            sb.append(c);
        }

        return sb.toString();
    }

    private static String unquote(String value) {
        String v = value.trim();
        if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
            return v.substring(1, v.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return v;
    }
}
