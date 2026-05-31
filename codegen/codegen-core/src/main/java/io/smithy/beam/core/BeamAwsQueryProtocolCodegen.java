package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

public final class BeamAwsQueryProtocolCodegen implements BeamProtocolCodegen {

    public static final ShapeId AWS_QUERY = ShapeId.from("aws.protocols#awsQuery");

    @Override
    public ShapeId protocolTraitId() {
        return AWS_QUERY;
    }

    @Override
    public void emitOperationBindings(CodegenContext<?, ?, ?> ctx,
            ServiceShape service, OperationShape operation) {
        // Language emitters own codec function bodies.
    }
}
