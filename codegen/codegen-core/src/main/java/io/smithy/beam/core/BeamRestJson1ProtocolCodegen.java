package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * First protocol implementation: REST JSON over HTTP bindings.
 *
 * <p>Keep JSON rules and HTTP framing here. Erlang or Elixir writers should stay thin
 * and call into helpers produced by this class.
 */
public final class BeamRestJson1ProtocolCodegen implements BeamProtocolCodegen {

    public static final ShapeId REST_JSON_1 = ShapeId.from("aws.protocols#restJson1");

    private final BeamHttpBindings httpBindings;

    public BeamRestJson1ProtocolCodegen(BeamHttpBindings httpBindings) {
        this.httpBindings = httpBindings;
    }

    @Override
    public ShapeId protocolTraitId() {
        return REST_JSON_1;
    }

    @Override
    public void emitOperationBindings(CodegenContext<?, ?, ?> ctx, ServiceShape service, OperationShape operation) {
        httpBindings.requestBindings(operation);
        httpBindings.responseBindings(operation);
        httpBindings.httpResponseCode(operation);
        // TODO: drive ErlangWriter / ElixirWriter from ctx to emit real codec functions.
    }

    @Override
    public void emitSerializerModulePreamble(CodegenContext<?, ?, ?> ctx, ServiceShape service) {
        // Language-specific writers belong in codegen-erlang or codegen-elixir.
    }
}
