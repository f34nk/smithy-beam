package io.smithy.beam.erlang.codegen.protocol;

import io.smithy.beam.erlang.codegen.DefaultErlangProtocolIntegration;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Erlang codegen integration for the {@code aws.protocols#awsJson1_1} protocol.
 *
 * <p>Adds {@code SMITHY_JSON} and {@code SMITHY_HTTP_CLIENT} runtime dependencies.
 */
public final class ErlangAwsJson11Integration extends DefaultErlangProtocolIntegration {

    @Override
    public ShapeId protocolId() {
        return ShapeId.from("aws.protocols#awsJson1_1");
    }
}
