package io.smithy.beam.erlang.codegen.protocol;

import io.smithy.beam.erlang.codegen.DefaultErlangProtocolIntegration;
import io.smithy.beam.erlang.codegen.ErlangDependency;
import java.util.List;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Erlang codegen integration for the {@code aws.protocols#restXml} protocol.
 *
 * <p>Adds {@code SMITHY_XML} and {@code SMITHY_HTTP_CLIENT} runtime dependencies.
 */
public final class ErlangRestXmlIntegration extends DefaultErlangProtocolIntegration {

    @Override
    public ShapeId protocolId() {
        return ShapeId.from("aws.protocols#restXml");
    }

    @Override
    protected List<ErlangDependency> protocolDependencies() {
        return List.of(ErlangDependency.SMITHY_XML, ErlangDependency.SMITHY_HTTP_CLIENT);
    }
}
