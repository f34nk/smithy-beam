package io.smithy.beam.erlang.codegen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import software.amazon.smithy.codegen.core.ReservedWords;
import software.amazon.smithy.codegen.core.ReservedWordsBuilder;

/**
 * Erlang reserved-word sets for module names and record-field / function-argument names.
 *
 * <p>Loaded from classpath resources at class-load time. Escape strategy: append a
 * trailing underscore (e.g. {@code receive} → {@code receive_}).
 */
public final class ErlangReservedWords {

    /** Reserved words for module and type names. */
    public static final ReservedWords MODULE_NAMES =
            load("/reservedwords-erlang-modules.txt", word -> word + "_");

    /** Reserved words for record fields and function argument names. */
    public static final ReservedWords MEMBER_NAMES =
            load("/reservedwords-erlang-members.txt", word -> word + "_");

    private ErlangReservedWords() {}

    private static ReservedWords load(String resource, java.util.function.UnaryOperator<String> escaper) {
        ReservedWordsBuilder builder = new ReservedWordsBuilder();
        Set<String> words = readLines(resource);
        for (String word : words) {
            builder.put(word, escaper.apply(word));
        }
        return builder.build();
    }

    private static Set<String> readLines(String resource) {
        InputStream in = ErlangReservedWords.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Classpath resource not found: " + resource);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            Set<String> words = new HashSet<>();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    words.add(line);
                }
            }
            return words;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + resource, e);
        }
    }
}
