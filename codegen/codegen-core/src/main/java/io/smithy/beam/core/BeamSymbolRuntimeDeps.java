package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Central policy for {@code Symbol.Builder#addDependency} calls for generated Beam
 * symbols. Extend this when a shape kind needs a runtime library on the target
 * platform.
 */
public final class BeamSymbolRuntimeDeps {

    private BeamSymbolRuntimeDeps() {}

    public static Symbol.Builder apply(Shape shape, Symbol.Builder builder) {
        if (shape.isDocumentShape()) {
            builder.addDependency("hex", "jsx", "3.1");
        }
        return builder;
    }
}
