package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

public final class BeamAwsJson11ProtocolCodegen implements BeamProtocolCodegen {

    public static final ShapeId AWS_JSON_1_1 = ShapeId.from("aws.protocols#awsJson1_1");

    @Override
    public ShapeId protocolTraitId() {
        return AWS_JSON_1_1;
    }

    @Override
    public void emitOperationBindings(CodegenContext<?, ?, ?> ctx,
            ServiceShape service, OperationShape operation) {
        // Language emitters own codec function bodies.
    }
}
