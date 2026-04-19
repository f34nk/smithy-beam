package io.smithy.beam.erlang.codegen.protocol;

import io.smithy.beam.erlang.codegen.DefaultErlangProtocolIntegration;
import io.smithy.beam.erlang.codegen.ErlangDependency;
import java.util.List;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Erlang codegen integration for the {@code aws.protocols#awsJson1_0} protocol.
 *
 * <p>Adds {@code SMITHY_JSON} and {@code SMITHY_HTTP_CLIENT} runtime dependencies.
 */
public final class ErlangAwsJson10Integration extends DefaultErlangProtocolIntegration {

    @Override
    public ShapeId protocolId() {
        return ShapeId.from("aws.protocols#awsJson1_0");
    }

    @Override
    protected List<ErlangDependency> protocolDependencies() {
        return List.of(ErlangDependency.SMITHY_JSON, ErlangDependency.SMITHY_HTTP_CLIENT);
    }
}
