package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

public final class BeamRestXmlProtocolCodegen implements BeamProtocolCodegen {

    public static final ShapeId REST_XML = ShapeId.from("aws.protocols#restXml");

    private final BeamHttpBindings httpBindings;

    public BeamRestXmlProtocolCodegen(BeamHttpBindings httpBindings) {
        this.httpBindings = httpBindings;
    }

    @Override
    public ShapeId protocolTraitId() {
        return REST_XML;
    }

    @Override
    public void emitOperationBindings(CodegenContext<?, ?, ?> ctx,
            ServiceShape service, OperationShape operation) {
        httpBindings.requestBindings(operation);
        httpBindings.responseBindings(operation);
        httpBindings.httpResponseCode(operation);
    }
}
