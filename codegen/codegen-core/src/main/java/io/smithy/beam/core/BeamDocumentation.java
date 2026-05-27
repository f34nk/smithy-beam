package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.SymbolWriter;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.DocumentationTrait;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Extracts the @documentation trait value for a shape, cleaned for embedding in
 * generated source comments.
 */
public final class BeamDocumentation {

    private BeamDocumentation() {}

    /**
     * Returns the documentation string for the shape if the trait is present,
     * with line breaks preserved.
     */
    public static Optional<String> forShape(Shape shape) {
        return shape.getTrait(DocumentationTrait.class)
                .map(DocumentationTrait::getValue)
                .map(BeamDocumentation::normalizeLineEndings)
                .map(BeamDocumentation::dedent)
                .map(BeamDocumentation::normalizeWhitespace)
                .filter(s -> !s.isBlank());
    }

    /**
     * Writes an Elixir {@code @doc} attribute, using a heredoc when the text spans lines.
     */
    public static void writeElixirDoc(SymbolWriter<?, ?> writer, String doc) {
        if (!doc.contains("\n")) {
            writer.write("@doc \"$L\"", escapeElixirString(doc));
            return;
        }
        writer.openBlock("@doc \"\"\"");
        for (String line : doc.split("\n", -1)) {
            writer.write("$L", line);
        }
        writer.closeBlock("\"\"\"");
    }

    /**
     * Writes Erlang {@code %% @doc} lines, using edoc continuation lines when needed.
     */
    public static void writeErlangDoc(SymbolWriter<?, ?> writer, String doc) {
        if (!doc.contains("\n")) {
            writer.write("%% @doc $L", doc);
            return;
        }
        writer.write("%% @doc");
        for (String line : doc.split("\n", -1)) {
            if (line.isEmpty()) {
                writer.write("%%");
            } else {
                writer.write("%% $L", line);
            }
        }
    }

    static String normalizeLineEndings(String doc) {
        return doc.replace("\r\n", "\n").replace('\r', '\n');
    }

    static String dedent(String doc) {
        String[] lines = doc.split("\n", -1);
        int minIndent = Arrays.stream(lines)
                .filter(line -> !line.isBlank())
                .mapToInt(BeamDocumentation::countLeadingSpaces)
                .min()
                .orElse(0);
        if (minIndent == 0) {
            return doc;
        }
        return Arrays.stream(lines)
                .map(line -> line.length() >= minIndent ? line.substring(minIndent) : line.stripLeading())
                .collect(Collectors.joining("\n"))
                .stripTrailing();
    }

    static String normalizeWhitespace(String doc) {
        String[] lines = doc.lines().map(String::stripTrailing).toArray(String[]::new);
        String joined = String.join("\n", lines);
        return joined.replaceAll("\n{3,}", "\n\n").strip();
    }

    private static int countLeadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static String escapeElixirString(String doc) {
        return doc.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
