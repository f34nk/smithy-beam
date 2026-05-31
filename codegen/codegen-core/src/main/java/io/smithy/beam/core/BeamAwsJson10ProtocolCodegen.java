package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

public final class BeamAwsJson10ProtocolCodegen implements BeamProtocolCodegen {

    public static final ShapeId AWS_JSON_1_0 = ShapeId.from("aws.protocols#awsJson1_0");

    @Override
    public ShapeId protocolTraitId() {
        return AWS_JSON_1_0;
    }

    @Override
    public void emitOperationBindings(CodegenContext<?, ?, ?> ctx,
            ServiceShape service, OperationShape operation) {
        // Language emitters own codec function bodies.
    }
}
