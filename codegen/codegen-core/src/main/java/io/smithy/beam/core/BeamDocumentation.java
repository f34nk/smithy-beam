package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.SymbolWriter;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
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
     * Writes shape-level documentation when the trait is present.
     * No-op when absent.
     */
    public static void writeShapeDocIfPresent(SymbolWriter<?, ?> writer, Shape shape, DocTarget target) {
        forShape(shape).ifPresent(doc -> {
            switch (target) {
                case ERLANG -> writeErlangDoc(writer, doc);
                case ELIXIR_MODuledoc -> writeElixirModuledoc(writer, doc);
                case ELIXIR_TYPEDOC -> writeElixirTypedoc(writer, doc);
            }
        });
    }

    /**
     * Writes an Elixir {@code @doc} attribute, using a heredoc when the text spans lines.
     */
    public static void writeElixirDoc(SymbolWriter<?, ?> writer, String doc) {
        writeElixirAttribute(writer, "@doc", doc);
    }

    /**
     * Writes an Elixir {@code @moduledoc} attribute, using a heredoc when the text spans lines.
     */
    public static void writeElixirModuledoc(SymbolWriter<?, ?> writer, String doc) {
        writeElixirAttribute(writer, "@moduledoc", doc);
    }

    /** Writes {@code @typedoc} (heredoc when multiline). */
    public static void writeElixirTypedoc(SymbolWriter<?, ?> writer, String doc) {
        writeElixirAttribute(writer, "@typedoc", doc);
    }

    /**
     * Builds the Elixir {@code ## Members} appendix for documented structure members.
     * Returns empty when no member carries {@code @documentation}.
     */
    public static Optional<String> memberModuledocAppendix(StructureShape shape) {
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (MemberShape member : shape.members()) {
            Optional<String> doc = forShape(member);
            if (doc.isEmpty()) {
                continue;
            }
            if (!any) {
                sb.append("\n\n## Members\n\n");
                any = true;
            }
            sb.append("- `").append(member.getMemberName()).append("` - ")
                    .append(doc.get().replace("\n", " "))
                    .append("\n");
        }
        return any ? Optional.of(sb.toString()) : Optional.empty();
    }

    /** Combines shape and member docs for Elixir {@code @moduledoc}. */
    public static Optional<String> elixirStructureModuledoc(StructureShape shape) {
        Optional<String> shapeDoc = forShape(shape);
        Optional<String> members = memberModuledocAppendix(shape);
        if (shapeDoc.isEmpty() && members.isEmpty()) {
            return Optional.empty();
        }
        String combined = shapeDoc.orElse("") + members.orElse("");
        return Optional.of(combined.strip());
    }

    public enum DocTarget {
        ERLANG,
        ELIXIR_MODuledoc,
        ELIXIR_TYPEDOC
    }

    private static void writeElixirAttribute(SymbolWriter<?, ?> writer, String attribute, String doc) {
        if (!doc.contains("\n")) {
            writer.write("$L \"$L\"", attribute, escapeElixirString(doc));
            return;
        }
        writer.openBlock("$L \"\"\"", attribute);
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
        return doc.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\u2028 ", " ")
                .replace('\u2028', ' ')
                .replace('\u2029', '\n');
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
