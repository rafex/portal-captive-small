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

        String s = json.trim();
        if (!s.startsWith("{") || !s.endsWith("}")) {
            throw new IllegalArgumentException("invalid_json");
        }

        int i = 1;
        while (i < s.length() - 1) {
            i = skipWs(s, i);
            if (i >= s.length() - 1) {
                break;
            }
            if (s.charAt(i) == ',') {
                i++;
                continue;
            }
            if (s.charAt(i) != '"') {
                throw new IllegalArgumentException("invalid_json_key");
            }

            Parse key = parseStringToken(s, i);
            i = skipWs(s, key.nextIndex);
            if (i >= s.length() || s.charAt(i) != ':') {
                throw new IllegalArgumentException("invalid_json_colon");
            }
            i = skipWs(s, i + 1);

            Parse value;
            char c = s.charAt(i);
            if (c == '"') {
                value = parseStringToken(s, i);
            } else {
                value = parseLiteralToken(s, i);
            }

            out.put(key.value, normalizeLiteral(value.value));
            i = value.nextIndex;
        }

        return out;
    }

    private static String normalizeLiteral(String value) {
        String v = value.trim();
        if ("null".equals(v)) {
            return null;
        }
        return v;
    }

    private static Parse parseStringToken(String s, int startQuote) {
        StringBuilder sb = new StringBuilder();
        int i = startQuote + 1;
        boolean escape = false;

        while (i < s.length()) {
            char c = s.charAt(i);
            if (escape) {
                sb.append(unescape(c));
                escape = false;
                i++;
                continue;
            }
            if (c == '\\') {
                escape = true;
                i++;
                continue;
            }
            if (c == '"') {
                return new Parse(sb.toString(), i + 1);
            }
            sb.append(c);
            i++;
        }

        throw new IllegalArgumentException("invalid_json_unterminated_string");
    }

    private static char unescape(char c) {
        return switch (c) {
            case '"' -> '"';
            case '\\' -> '\\';
            case '/' -> '/';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            default -> c;
        };
    }

    private static Parse parseLiteralToken(String s, int start) {
        int i = start;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ',' || c == '}') {
                break;
            }
            i++;
        }
        String value = s.substring(start, i).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("invalid_json_value");
        }
        return new Parse(value, i);
    }

    private static int skipWs(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private record Parse(String value, int nextIndex) {
    }
}
