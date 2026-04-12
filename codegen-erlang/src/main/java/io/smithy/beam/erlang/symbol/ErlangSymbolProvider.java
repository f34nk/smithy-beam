package io.smithy.beam.erlang.symbol;

/**
 * Converts Smithy names to Erlang identifiers.
 *
 * <p>Naming rules:
 * <ul>
 *   <li>Module/function names: {@code CamelCase → snake_case}</li>
 *   <li>Type names: {@code CamelCase → snake_case()} (trailing parentheses)</li>
 *   <li>Variable names: {@code camelCase/snake_case → PascalCase} (Erlang vars start with uppercase)</li>
 *   <li>Reserved words are escaped by appending {@code _}</li>
 * </ul>
 */
public final class ErlangSymbolProvider {

    private ErlangSymbolProvider() {}

    /**
     * Converts a Smithy name to an Erlang module name (snake_case).
     *
     * <p>Example: {@code "WeatherService" → "weather_service"}
     */
    public static String toModuleName(String smithyName) {
        return ErlangReservedWords.escape(toSnakeCase(smithyName));
    }

    /**
     * Converts a Smithy name to an Erlang function name (snake_case).
     *
     * <p>Example: {@code "GetWeather" → "get_weather"}
     */
    public static String toFunctionName(String smithyName) {
        return ErlangReservedWords.escape(toSnakeCase(smithyName));
    }

    /**
     * Converts a Smithy name to an Erlang type name (snake_case with trailing {@code ()}).
     *
     * <p>Example: {@code "GetWeatherInput" → "get_weather_input()"}
     */
    public static String toTypeName(String smithyName) {
        return toSnakeCase(smithyName) + "()";
    }

    /**
     * Converts a Smithy name to an Erlang variable name (PascalCase).
     *
     * <p>Erlang variables must start with an uppercase letter. Reserved words
     * cannot appear as variable names; they are escaped with an underscore suffix.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "cityId" → "CityId"}</li>
     *   <li>{@code "CityId" → "CityId"}</li>
     *   <li>{@code "get_weather" → "GetWeather"}</li>
     * </ul>
     */
    public static String toVarName(String smithyName) {
        if (smithyName == null || smithyName.isEmpty()) {
            return smithyName;
        }
        String pascal = toPascalCase(smithyName);
        // Reserved word check uses the lowercase variant
        String lower = pascal.toLowerCase();
        if (ErlangReservedWords.isReserved(lower)) {
            return pascal + ErlangReservedWords.ESCAPE_SUFFIX;
        }
        return pascal;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Converts an identifier to snake_case.
     *
     * <p>Handles PascalCase, camelCase, and already-snake_case inputs.
     */
    public static String toSnakeCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return name
            .replaceAll("([a-z])([A-Z])", "$1_$2")
            .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
            .replaceAll("([0-9])([A-Z])", "$1_$2")
            .toLowerCase();
    }

    /**
     * Converts an identifier to PascalCase.
     *
     * <p>Splits on underscores and capitalizes each segment.
     * For camelCase/PascalCase input, capitalizes just the first character.
     */
    static String toPascalCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        // If contains underscores, split and capitalize each part
        if (name.contains("_")) {
            StringBuilder sb = new StringBuilder();
            for (String part : name.split("_", -1)) {
                if (!part.isEmpty()) {
                    sb.append(Character.toUpperCase(part.charAt(0)));
                    sb.append(part.substring(1));
                }
            }
            return sb.toString();
        }
        // Otherwise just capitalize the first character
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
