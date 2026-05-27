package io.smithy.beam.core;

import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.DocumentationTrait;

import java.util.Optional;

/**
 * Extracts the @documentation trait value for a shape, cleaned for embedding in
 * generated source comments.
 */
public final class BeamDocumentation {

    private BeamDocumentation() {}

    /**
     * Returns the documentation string for the shape if the trait is present,
     * with HTML stripped and lines trimmed.
     */
    public static Optional<String> forShape(Shape shape) {
        return shape.getTrait(DocumentationTrait.class)
                .map(DocumentationTrait::getValue)
                .map(BeamDocumentation::stripHtml)
                .filter(s -> !s.isBlank());
    }

    private static String stripHtml(String doc) {
        // Remove HTML tags and normalize whitespace.
        return doc.replaceAll("<[^>]+>", "")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&")
                .replaceAll("&quot;", "\"")
                .lines()
                .map(String::trim)
                .filter(l -> !l.isBlank())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }
}
