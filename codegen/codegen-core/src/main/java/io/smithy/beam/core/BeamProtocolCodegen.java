package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Language-neutral protocol hook. Implement exactly one concrete protocol first
 * (REST JSON), then register additional trait ids behind new implementations.
 */
public interface BeamProtocolCodegen {

    /**
     * Protocol trait shape id such as {@code aws.protocols#restJson1}.
     */
    ShapeId protocolTraitId();

    /**
     * Emit transport-facing helpers for an operation (paths, queries, JSON shape codecs).
     *
     * @param ctx Codegen context for the target language; cast inside implementations.
     */
    void emitOperationBindings(CodegenContext<?, ?, ?> ctx, ServiceShape service, OperationShape operation);

    /**
     * Optional module-level prelude for serializers (imports, shared helpers).
     */
    default void emitSerializerModulePreamble(CodegenContext<?, ?, ?> ctx, ServiceShape service) {
        // single-protocol baseline: override when codecs need shared helpers
    }
}
