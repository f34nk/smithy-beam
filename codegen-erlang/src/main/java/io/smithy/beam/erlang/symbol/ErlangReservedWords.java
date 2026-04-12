package io.smithy.beam.erlang.symbol;

import java.util.Collections;
import java.util.Set;

/**
 * Erlang reserved words and escaping utilities.
 *
 * <p>Reserved words are escaped by appending an underscore suffix.
 * For example, {@code "case"} becomes {@code "case_"}.
 */
public final class ErlangReservedWords {

    public static final String ESCAPE_SUFFIX = "_";

    private static final Set<String> RESERVED = Set.of(
        "after", "and", "andalso", "band", "begin", "bnot", "bor", "bsl",
        "bsr", "bxor", "case", "catch", "cond", "div", "end", "fun",
        "if", "let", "not", "of", "or", "orelse", "receive", "rem",
        "try", "when", "xor"
    );

    private ErlangReservedWords() {}

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
