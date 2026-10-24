package com.company.acts.mail.adapter.out.event;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * Минимальная запись JSON для конвертов событий: строки, числа, логические значения, null, списки и Map.
 * Своя реализация — чтобы формат событий не зависел от настроек сериализатора веб-слоя.
 */
final class Json {

    private Json() {
    }

    static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        append(sb, value);
        return sb.toString();
    }

    private static void append(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof BigDecimal d) {
            sb.append(d.stripTrailingZeros().toPlainString());
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                string(sb, String.valueOf(entry.getKey()));
                sb.append(':');
                append(sb, entry.getValue());
            }
            sb.append('}');
        } else if (value instanceof Collection<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                append(sb, item);
            }
            sb.append(']');
        } else {
            string(sb, value.toString());
        }
    }

    private static void string(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
