package io.smithy.beam.erlang.codegen.protocol;

import io.smithy.beam.erlang.codegen.DefaultErlangProtocolIntegration;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Erlang codegen integration for the {@code aws.protocols#restJson1} protocol.
 *
 * <p>Adds {@code SMITHY_JSON} and {@code SMITHY_HTTP_CLIENT} runtime dependencies.
 */
public final class ErlangRestJson1Integration extends DefaultErlangProtocolIntegration {

    @Override
    public ShapeId protocolId() {
        return ShapeId.from("aws.protocols#restJson1");
    }
}
