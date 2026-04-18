package io.smithy.beam.erlang.codegen;

import java.net.URL;
import software.amazon.smithy.codegen.core.ReservedWords;
import software.amazon.smithy.codegen.core.ReservedWordsBuilder;

/**
 * Erlang reserved-word escapers for two naming contexts.
 *
 * <p>Both instances are built at class-load time from line-delimited
 * classpath resources. The escape strategy for both contexts is to
 * append a trailing underscore (e.g. {@code receive} → {@code receive_}).
 *
 * <p>The distinction between contexts matters because {@code _} (bare
 * underscore) is legal as a module name but is the anonymous variable in
 * Erlang pattern matches and must therefore be avoided as a record-field
 * name.
 */
public final class ErlangReservedWords {

    /** Reserved-word escaper for module and type names. */
    public static final ReservedWords MODULE_NAMES;

    /** Reserved-word escaper for record-field and function-argument names. */
    public static final ReservedWords MEMBER_NAMES;

    static {
        MODULE_NAMES = load("reservedwords-erlang-modules.txt");
        MEMBER_NAMES = load("reservedwords-erlang-members.txt");
    }

    private ErlangReservedWords() {}

    private static ReservedWords load(String resourceName) {
        URL url = ErlangReservedWords.class.getClassLoader().getResource(resourceName);
        if (url == null) {
            throw new IllegalStateException(
                    "Classpath resource not found: " + resourceName);
        }
        return new ReservedWordsBuilder()
                .loadWords(url, word -> word + "_")
                .build();
    }
}
