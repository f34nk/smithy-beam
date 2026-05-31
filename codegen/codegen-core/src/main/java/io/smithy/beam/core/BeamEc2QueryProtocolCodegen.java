package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

public final class BeamEc2QueryProtocolCodegen implements BeamProtocolCodegen {

    public static final ShapeId EC2_QUERY = ShapeId.from("aws.protocols#ec2Query");

    @Override
    public ShapeId protocolTraitId() {
        return EC2_QUERY;
    }

    @Override
    public void emitOperationBindings(CodegenContext<?, ?, ?> ctx,
            ServiceShape service, OperationShape operation) {
        // Language emitters own codec function bodies.
    }
}
