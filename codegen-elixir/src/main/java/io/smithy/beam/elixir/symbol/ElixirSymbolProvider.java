package io.smithy.beam.elixir.symbol;

/**
 * Converts Smithy names to Elixir identifiers.
 *
 * <p>Naming rules:
 * <ul>
 *   <li>Module names: PascalCase preserved — {@code "WeatherService" → "WeatherService"}</li>
 *   <li>Function names: {@code CamelCase → snake_case} — {@code "GetWeather" → "get_weather"}</li>
 *   <li>Type names: {@code "GetWeatherInput" → "GetWeatherInput.t()"} (Elixir module-type notation)</li>
 *   <li>Variable names: {@code snake_case} — reserved words are escaped with {@code _}</li>
 * </ul>
 */
public final class ElixirSymbolProvider {

    private ElixirSymbolProvider() {}

    /**
     * Returns the Elixir module name for a Smithy name (PascalCase preserved).
     *
     * <p>Example: {@code "WeatherService" → "WeatherService"}
     */
    public static String toModuleName(String smithyName) {
        if (smithyName == null || smithyName.isEmpty()) {
            return smithyName;
        }
        return toPascalCase(smithyName);
    }

    /**
     * Returns the Elixir function name for a Smithy name (snake_case).
     *
     * <p>Example: {@code "GetWeather" → "get_weather"}
     */
    public static String toFunctionName(String smithyName) {
        return ElixirReservedWords.escape(toSnakeCase(smithyName));
    }

    /**
     * Returns the Elixir module-type reference for a Smithy type name.
     *
     * <p>Uses Elixir's {@code Module.t()} convention for module-level type references.
     *
     * <p>Example: {@code "GetWeatherInput" → "GetWeatherInput.t()"}
     */
    public static String toTypeName(String smithyName) {
        return toPascalCase(smithyName) + ".t()";
    }

    /**
     * Returns the Elixir variable name for a Smithy name (snake_case).
     *
     * <p>Reserved words are escaped by appending {@code _}.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "cityId" → "city_id"}</li>
     *   <li>{@code "CityId" → "city_id"}</li>
     *   <li>{@code "case" → "case_"}</li>
     * </ul>
     */
    public static String toVarName(String smithyName) {
        if (smithyName == null || smithyName.isEmpty()) {
            return smithyName;
        }
        return ElixirReservedWords.escape(toSnakeCase(smithyName));
    }

    /**
     * Returns the snake_case form of the name, suitable for inline type annotations
     * within the same module.
     *
     * <p>Built-in type names that cannot be redefined (e.g. {@code node}) are
     * suffixed with {@code "_t"} to avoid a compile error.
     *
     * <p>Examples: {@code "GetWeatherInput" → "get_weather_input()"}, {@code "Node" → "node_t()"}
     */
    public static String toInlineTypeName(String smithyName) {
        return ElixirReservedWords.escapeTypeName(toSnakeCase(smithyName)) + "()";
    }

    /**
     * Returns the atom tag for a Smithy member name (e.g. map key or enum variant).
     *
     * <p>Example: {@code "cityId" → ":city_id"}, {@code "unit" → ":unit"}
     */
    public static String toAtomTag(String smithyName) {
        return ":" + toSnakeCase(smithyName);
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
     * <p>Splits on underscores and capitalises each segment.
     * For camelCase/PascalCase input, capitalises just the first character.
     */
    public static String toPascalCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
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
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
