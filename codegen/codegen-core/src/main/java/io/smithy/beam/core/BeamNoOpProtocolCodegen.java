package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

final class BeamNoOpProtocolCodegen implements BeamProtocolCodegen {

    private final ShapeId protocolTraitId;

    BeamNoOpProtocolCodegen(ShapeId protocolTraitId) {
        this.protocolTraitId = protocolTraitId;
    }

    @Override
    public ShapeId protocolTraitId() {
        return protocolTraitId;
    }

    @Override
    public void emitOperationBindings(
            CodegenContext<?, ?, ?> ctx, ServiceShape service, OperationShape operation) {
        // Language emitters own codec function bodies.
    }
}
