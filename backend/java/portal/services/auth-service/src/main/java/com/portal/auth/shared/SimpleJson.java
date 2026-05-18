package com.portal.auth.shared;

import java.util.HashMap;
import java.util.Map;

public final class SimpleJson {
    private SimpleJson() {
    }

    public static Map<String, String> parseFlatObject(String json) {
        Map<String, String> out = new HashMap<>();
        if (json == null) {
            return out;
        }

        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IllegalArgumentException("invalid_json");
        }

        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isEmpty()) {
            return out;
        }

        String[] pairs = body.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = unquote(kv[0].trim());
            String value = unquote(kv[1].trim());
            out.put(key, value);
        }
        return out;
    }

    private static String unquote(String value) {
        String result = value;
        if (result.startsWith("\"") && result.endsWith("\"")) {
            result = result.substring(1, result.length() - 1);
        }
        return result.trim();
    }
}
