package io.smithy.beam.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class BeamNameUtils {
    private BeamNameUtils() {}

    public static <T> Map<T, String> deconflict(
            Collection<T> values, Function<T, String> escapedName) {
        Map<String, Integer> counts = new HashMap<>();
        Map<T, String> result = new LinkedHashMap<>();
        for (T value : values) {
            String escaped = escapedName.apply(value);
            int count = counts.merge(escaped, 1, Integer::sum);
            result.put(value, count == 1 ? escaped : escaped + "_" + count);
        }
        return result;
    }

    public static String toSnakeCase(String name) {
        return name
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .toLowerCase();
    }

    /**
     * Erlang variable name for a snake_case record field per Inaka guidelines:
     * CamelCase, no underscores (e.g. {@code client_token} -> {@code ClientToken}).
     */
    public static String toCamelCaseVariable(String snakeField) {
        if (snakeField.isEmpty()) {
            return snakeField;
        }
        StringBuilder sb = new StringBuilder();
        for (String part : snakeField.split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }
}
