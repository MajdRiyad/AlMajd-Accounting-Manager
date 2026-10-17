package com.almajd.accounting.backup;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JSON reader/writer for the <b>flat</b> objects used inside backup
 * files ({@code manifest.json} and {@code settings.json}).
 * <p>
 * It is intentionally dependency-free (no {@code org.json}, no Android APIs) so
 * it can be covered by plain JVM unit tests. Supported values are string,
 * number, {@code true}/{@code false} and {@code null}. Nested objects/arrays
 * are rejected on read, which also makes it safe as a strict validator: any
 * unexpected structure in a random file is refused instead of silently
 * accepted.
 */
public final class SimpleJson {

    private SimpleJson() {
    }

    /** Serializes a flat map to a single-line JSON object. */
    public static String write(Map<String, ?> values) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(sb, entry.getKey());
            sb.append(':');
            Object value = entry.getValue();
            if (value == null) {
                sb.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value.toString());
            } else {
                writeString(sb, value.toString());
            }
        }
        sb.append('}');
        return sb.toString();
    }

    /** Parses a flat JSON object. Throws {@link JsonException} on any problem. */
    public static Map<String, Object> read(String json) throws JsonException {
        if (json == null) {
            throw new JsonException("Empty JSON document");
        }
        Parser parser = new Parser(json);
        parser.skipWhitespace();
        Map<String, Object> result = parser.parseObject();
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw new JsonException("Unexpected content after the JSON object");
        }
        return result;
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        sb.append('"');
    }

    /** Tiny recursive-descent parser for a flat JSON object. */
    private static final class Parser {

        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        boolean isAtEnd() {
            return pos >= text.length();
        }

        void skipWhitespace() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Map<String, Object> parseObject() throws JsonException {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == '}') {
                    pos++;
                    return map;
                }
                throw new JsonException("Expected ',' or '}' at position " + pos);
            }
        }

        private Object parseValue() throws JsonException {
            char c = peek();
            if (c == '"') {
                return parseString();
            }
            if (c == '{' || c == '[') {
                throw new JsonException("Nested objects/arrays are not supported at position " + pos);
            }
            if (c == 't') {
                expectWord("true");
                return Boolean.TRUE;
            }
            if (c == 'f') {
                expectWord("false");
                return Boolean.FALSE;
            }
            if (c == 'n') {
                expectWord("null");
                return null;
            }
            return parseNumber();
        }

        private void expectWord(String word) throws JsonException {
            if (pos + word.length() > text.length()
                    || !text.regionMatches(pos, word, 0, word.length())) {
                throw new JsonException("Invalid literal at position " + pos);
            }
            pos += word.length();
        }

        private Double parseNumber() throws JsonException {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    pos++;
                } else {
                    break;
                }
            }
            if (start == pos) {
                throw new JsonException("Invalid value at position " + pos);
            }
            String token = text.substring(start, pos);
            try {
                return Double.valueOf(token);
            } catch (NumberFormatException e) {
                throw new JsonException("Invalid number: " + token);
            }
        }

        private String parseString() throws JsonException {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= text.length()) {
                    throw new JsonException("Unterminated string");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (pos >= text.length()) {
                    throw new JsonException("Unterminated escape sequence");
                }
                char esc = text.charAt(pos++);
                switch (esc) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        if (pos + 4 > text.length()) {
                            throw new JsonException("Invalid unicode escape");
                        }
                        String hex = text.substring(pos, pos + 4);
                        try {
                            sb.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException e) {
                            throw new JsonException("Invalid unicode escape: " + hex);
                        }
                        pos += 4;
                        break;
                    default:
                        throw new JsonException("Invalid escape sequence: \\" + esc);
                }
            }
        }

        private char peek() throws JsonException {
            if (pos >= text.length()) {
                throw new JsonException("Unexpected end of JSON");
            }
            return text.charAt(pos);
        }

        private void expect(char c) throws JsonException {
            if (pos >= text.length() || text.charAt(pos) != c) {
                throw new JsonException("Expected '" + c + "' at position " + pos);
            }
            pos++;
        }
    }
}
