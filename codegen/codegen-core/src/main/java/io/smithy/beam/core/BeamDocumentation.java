package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.SymbolWriter;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.DocumentationTrait;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extracts the @documentation trait value for a shape, cleaned for embedding in
 * generated source comments.
 */
public final class BeamDocumentation {

    private static final Pattern PRE_BLOCK =
            Pattern.compile("(?is)<pre[^>]*>(.*?)</pre>");
    private static final Pattern REMAINING_TAG = Pattern.compile("<[^>]+>");

    private BeamDocumentation() {}

    /**
     * Returns the documentation string for the shape if the trait is present,
     * converted to markdown with line breaks preserved.
     */
    public static Optional<String> forShape(Shape shape) {
        return shape.getTrait(DocumentationTrait.class)
                .map(DocumentationTrait::getValue)
                .map(BeamDocumentation::toMarkdown)
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

    static String toMarkdown(String doc) {
        String normalized = doc.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.contains("<")) {
            return decodeEntities(normalized);
        }
        String s = decodeEntities(normalized);
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        for (int level = 6; level >= 1; level--) {
            String prefix = "#".repeat(level) + " ";
            s = s.replaceAll("(?is)<h" + level + "[^>]*>(.*?)</h" + level + ">", prefix + "$1\n\n");
        }
        Matcher preMatcher = PRE_BLOCK.matcher(s);
        StringBuffer preBuffer = new StringBuffer();
        while (preMatcher.find()) {
            String code = stripRemainingTags(preMatcher.group(1)).strip();
            String replacement = "\n\n```\n" + code + "\n```\n\n";
            preMatcher.appendReplacement(preBuffer, Matcher.quoteReplacement(replacement));
        }
        preMatcher.appendTail(preBuffer);
        s = preBuffer.toString();
        s = s.replaceAll("(?is)<p[^>]*>(.*?)</p>", "$1\n\n");
        s = s.replaceAll("(?is)<li[^>]*>(.*?)</li>", "- $1\n");
        s = s.replaceAll("(?is)<(?:ul|ol)[^>]*>", "\n");
        s = s.replaceAll("(?is)</(?:ul|ol)>", "\n");
        s = s.replaceAll("(?is)<code>(.*?)</code>", "`$1`");
        s = s.replaceAll("(?is)<(?:strong|b)>(.*?)</(?:strong|b)>", "**$1**");
        s = s.replaceAll("(?is)<(?:em|i)>(.*?)</(?:em|i)>", "_$1_");
        s = s.replaceAll("(?is)<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", "[$2]($1)");
        s = stripRemainingTags(s);
        return s;
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

    private static String stripRemainingTags(String text) {
        return REMAINING_TAG.matcher(text).replaceAll("");
    }

    private static String decodeEntities(String doc) {
        return doc.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private static String escapeElixirString(String doc) {
        return doc.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
