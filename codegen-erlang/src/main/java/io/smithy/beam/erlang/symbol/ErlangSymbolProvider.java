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
 *   <li>Type names that clash with Erlang built-in types get a {@code _t} suffix</li>
 * </ul>
 */
public final class ErlangSymbolProvider {

    private ErlangSymbolProvider() {}

    /**
     * Erlang built-in predefined types that cannot be locally redefined without a compiler warning.
     * Smithy shapes whose snake_case name matches one of these get a {@code _t} suffix in the
     * generated {@code -type} declaration to avoid the redefinition warning.
     */
    private static final java.util.Set<String> ERLANG_BUILTIN_TYPES = java.util.Set.of(
            "any", "arity", "atom", "binary", "bitstring", "boolean", "byte", "char",
            "float", "fun", "function", "identifier", "integer", "iodata", "iolist", "map",
            "maybe_improper_list", "mfa", "module", "neg_integer", "nil", "no_return", "node",
            "non_neg_integer", "none", "nonempty_improper_list", "nonempty_list",
            "nonempty_maybe_improper_list", "nonempty_string", "number", "pid", "port",
            "pos_integer", "reference", "string", "term", "timeout"
    );

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
     * <p>Names that clash with Erlang built-in predefined types receive a {@code _t} suffix
     * to avoid a {@code local redefinition of built-in type} compiler warning.
     *
     * <p>Smithy shape names are always PascalCase. If {@code smithyName} is already lowercase
     * and matches a built-in (e.g. the {@code "map"} sentinel used by protocol analyzers for
     * unit outputs), it is returned as-is as a direct built-in type reference — no {@code _t}
     * suffix, since the intent is to reference the Erlang built-in, not declare a user type.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "GetWeatherInput" → "get_weather_input()"}</li>
     *   <li>{@code "Node" → "node_t()"} (user type renamed to avoid built-in clash)</li>
     *   <li>{@code "map"  → "map()"}  (built-in sentinel, used directly)</li>
     * </ul>
     */
    public static String toTypeName(String smithyName) {
        // Lowercase sentinel from protocol analyzers (e.g. "map" for unit outputs) — use built-in directly.
        if (ERLANG_BUILTIN_TYPES.contains(smithyName)) {
            return smithyName + "()";
        }
        String snake = toSnakeCase(smithyName);
        return (ERLANG_BUILTIN_TYPES.contains(snake) ? snake + "_t" : snake) + "()";
    }

    /**
     * Returns the safe snake_case base name for a type, applying the {@code _t} suffix when
     * the name would clash with an Erlang built-in type.  Use this when you need the name
     * without the trailing {@code ()} (e.g. when constructing qualified remote-type references).
     *
     * <p>As with {@link #toTypeName}, a lowercase sentinel that already matches a built-in
     * is returned unchanged (no {@code _t} suffix).
     */
    public static String toSafeSnakeCase(String smithyName) {
        // Lowercase sentinel — return built-in name directly.
        if (ERLANG_BUILTIN_TYPES.contains(smithyName)) {
            return smithyName;
        }
        String snake = toSnakeCase(smithyName);
        return ERLANG_BUILTIN_TYPES.contains(snake) ? snake + "_t" : snake;
    }

    /**
     * Converts a Smithy member/variant name to an Erlang atom suitable for use
     * as a map key or tagged-tuple tag.
     *
     * <p>Unlike {@link #toFunctionName}, which appends {@code _} to reserved words,
     * this method wraps them in single quotes so the original name is preserved.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "Prefix" → "prefix"}</li>
     *   <li>{@code "And"    → "'and'"}</li>
     *   <li>{@code "End"    → "'end'"}</li>
     * </ul>
     */
    public static String toAtomTag(String smithyName) {
        String snake = toSnakeCase(smithyName);
        return ErlangReservedWords.isReserved(snake) ? "'" + snake + "'" : snake;
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
