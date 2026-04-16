package io.smithy.beam.elixir.symbol;

import java.util.Collections;
import java.util.Set;

/**
 * Elixir reserved words and escaping utilities.
 *
 * <p>Reserved words are escaped by appending an underscore suffix.
 * For example, {@code "case"} becomes {@code "case_"}.
 *
 * <p>Built-in type names (e.g. {@code node}, {@code pid}) that clash with Elixir's
 * kernel types are escaped by appending {@code "_t"}.
 */
public final class ElixirReservedWords {

    public static final String ESCAPE_SUFFIX = "_";

    private static final Set<String> RESERVED = Set.of(
        "after", "and", "case", "cond", "do", "else", "end", "false",
        "fn", "for", "if", "in", "nil", "not", "or", "quote",
        "raise", "receive", "rescue", "true", "try", "unquote",
        "unquote_splicing", "when", "with"
    );

    /**
     * Elixir / Erlang built-in type names that cannot be redefined with {@code @type}.
     *
     * <p>When a Smithy shape name maps to one of these (after snake_casing), the
     * generated type name is suffixed with {@code "_t"} to avoid a compile error.
     */
    private static final Set<String> BUILTIN_TYPES = Set.of(
        "any", "atom", "binary", "bitstring", "boolean", "byte", "char", "charlist",
        "float", "fun", "function", "identifier", "integer", "iodata", "iolist",
        "keyword", "list", "map", "maybe_improper_list", "mfa", "module",
        "neg_integer", "node", "no_return", "non_neg_integer", "nonempty_binary",
        "nonempty_bitstring", "nonempty_charlist", "nonempty_improper_list",
        "nonempty_list", "nonempty_maybe_improper_list", "nonempty_string",
        "number", "pid", "port", "pos_integer", "reference", "string", "struct",
        "term", "timeout", "tuple"
    );

    private ElixirReservedWords() {}

    public static boolean isReserved(String word) {
        return word != null && RESERVED.contains(word);
    }

    public static String escape(String word) {
        return isReserved(word) ? word + ESCAPE_SUFFIX : word;
    }

    public static Set<String> getReservedWords() {
        return Collections.unmodifiableSet(RESERVED);
    }

    /**
     * Returns {@code true} when {@code typeName} (already snake_cased) clashes with
     * an Elixir built-in type that cannot be redefined with {@code @type}.
     */
    public static boolean isBuiltinType(String typeName) {
        return typeName != null && BUILTIN_TYPES.contains(typeName);
    }

    /**
     * Escapes a snake_case type name that would conflict with an Elixir built-in type
     * by appending {@code "_t"}.
     *
     * <p>Example: {@code "node" → "node_t"}, {@code "activation" → "activation"}.
     */
    public static String escapeTypeName(String typeName) {
        return isBuiltinType(typeName) ? typeName + "_t" : typeName;
    }
}
