package io.smithy.beam.erlang.codegen.protocol;

import io.smithy.beam.erlang.codegen.DefaultErlangProtocolIntegration;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Erlang codegen integration for the {@code aws.protocols#awsQuery} protocol.
 *
 * <p>Adds {@code SMITHY_QUERY} and {@code SMITHY_HTTP_CLIENT} runtime dependencies.
 */
public final class ErlangAwsQueryIntegration extends DefaultErlangProtocolIntegration {

    @Override
    public ShapeId protocolId() {
        return ShapeId.from("aws.protocols#awsQuery");
    }
}
