package com.company.acts.act.adapter.in.event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Небольшой разборщик JSON для конвертов событий: объекты → Map, массивы → List, строки, числа (как String,
 * чтобы не терять точность), логические значения и null. Независим от версии Jackson.
 */
final class JsonReader {

    private final String s;
    private int i;

    private JsonReader(String s) {
        this.s = s;
    }

    static Object parse(String json) {
        JsonReader r = new JsonReader(json);
        r.skip();
        Object value = r.value();
        r.skip();
        if (r.i != r.s.length()) {
            throw new IllegalArgumentException("Лишние символы после JSON в позиции " + r.i);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(String json) {
        Object value = parse(json);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Ожидался объект JSON");
        }
        return (Map<String, Object>) value;
    }

    private Object value() {
        char c = s.charAt(i);
        return switch (c) {
            case '{' -> objectValue();
            case '[' -> arrayValue();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> objectValue() {
        Map<String, Object> map = new LinkedHashMap<>();
        i++;
        skip();
        if (s.charAt(i) == '}') {
            i++;
            return map;
        }
        while (true) {
            skip();
            String key = string();
            skip();
            expect(':');
            skip();
            map.put(key, value());
            skip();
            if (s.charAt(i) == ',') {
                i++;
            } else {
                expect('}');
                return map;
            }
        }
    }

    private List<Object> arrayValue() {
        List<Object> list = new ArrayList<>();
        i++;
        skip();
        if (s.charAt(i) == ']') {
            i++;
            return list;
        }
        while (true) {
            skip();
            list.add(value());
            skip();
            if (s.charAt(i) == ',') {
                i++;
            } else {
                expect(']');
                return list;
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case '"', '\\', '/' -> sb.append(e);
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> throw new IllegalArgumentException("Неверная escape-последовательность");
                }
            } else {
                sb.append(c);
            }
        }
    }

    private String number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        if (start == i) {
            throw new IllegalArgumentException("Неожиданный символ JSON в позиции " + i);
        }
        return s.substring(start, i);
    }

    private Object literal(String word, Object value) {
        if (!s.startsWith(word, i)) {
            throw new IllegalArgumentException("Неожиданный символ JSON в позиции " + i);
        }
        i += word.length();
        return value;
    }

    private void expect(char c) {
        if (i >= s.length() || s.charAt(i) != c) {
            throw new IllegalArgumentException("Ожидался символ '" + c + "' в позиции " + i);
        }
        i++;
    }

    private void skip() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }
}
