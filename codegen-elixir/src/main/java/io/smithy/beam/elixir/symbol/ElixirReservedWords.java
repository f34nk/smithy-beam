package io.smithy.beam.elixir.symbol;

import java.util.Collections;
import java.util.Set;

/**
 * Elixir reserved words and escaping utilities.
 *
 * <p>Reserved words are escaped by appending an underscore suffix.
 * For example, {@code "case"} becomes {@code "case_"}.
 */
public final class ElixirReservedWords {

    public static final String ESCAPE_SUFFIX = "_";

    private static final Set<String> RESERVED = Set.of(
        "after", "and", "case", "cond", "do", "else", "end", "false",
        "fn", "for", "if", "in", "nil", "not", "or", "quote",
        "raise", "receive", "rescue", "true", "try", "unquote",
        "unquote_splicing", "when", "with"
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
}
