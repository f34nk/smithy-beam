package io.smithy.beam.elixir.codegen;

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
 * Elixir reserved-word sets for module names and struct-field / function-argument names.
 *
 * <p>Loaded from classpath resources at class-load time.
 * Module escaper: append {@code "Ex"} suffix (e.g. {@code Do} → {@code DoEx}).
 * Member escaper: append {@code "_field"} suffix.
 */
public final class ElixirReservedWords {

    /** Reserved words for module and type names. PascalCase words get an {@code "Ex"} suffix. */
    public static final ReservedWords MODULE_NAMES =
            load("/reservedwords-elixir-modules.txt", word -> {
                String escaped = Character.toUpperCase(word.charAt(0)) + word.substring(1);
                return escaped + "Ex";
            });

    /** Reserved words for struct fields and function argument names. */
    public static final ReservedWords MEMBER_NAMES =
            load("/reservedwords-elixir-members.txt", word -> word + "_field");

    private ElixirReservedWords() {}

    private static ReservedWords load(String resource, java.util.function.UnaryOperator<String> escaper) {
        ReservedWordsBuilder builder = new ReservedWordsBuilder();
        Set<String> words = readLines(resource);
        for (String word : words) {
            builder.put(word, escaper.apply(word));
        }
        return builder.build();
    }

    private static Set<String> readLines(String resource) {
        InputStream in = ElixirReservedWords.class.getResourceAsStream(resource);
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
