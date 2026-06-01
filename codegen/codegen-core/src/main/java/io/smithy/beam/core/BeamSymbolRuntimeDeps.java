package io.smithy.beam.core;

import software.amazon.smithy.aws.traits.auth.SigV4Trait;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.model.shapes.ServiceShape;
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
            builder.addDependency(BeamRuntimeDependency.JSX);
        }
        return builder;
    }

    public static Symbol.Builder applyService(ServiceShape service, Symbol.Builder builder) {
        if (service.hasTrait(SigV4Trait.class)) {
            builder.addDependency(BeamRuntimeDependency.AWS_SIGV4);
        }
        return builder;
    }

    public static Symbol.Builder applyElixirService(ServiceShape service, Symbol.Builder builder) {
        if (service.hasTrait(SigV4Trait.class)) {
            builder.addDependency("hex", "aws_signature", "0.3.2");
        }
        return builder;
    }
}
