package io.smithy.beam.elixir.codegen.protocol;

import io.smithy.beam.elixir.codegen.DefaultElixirProtocolIntegration;
import io.smithy.beam.elixir.codegen.ElixirDependency;
import java.util.List;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Elixir codegen integration for the {@code aws.protocols#restXml} protocol.
 *
 * <p>Adds {@code SMITHY_XML} and {@code SMITHY_HTTP_CLIENT} runtime dependencies.
 */
public final class ElixirRestXmlIntegration extends DefaultElixirProtocolIntegration {

    @Override
    public ShapeId protocolId() {
        return ShapeId.from("aws.protocols#restXml");
    }

    @Override
    protected List<ElixirDependency> protocolDependencies() {
        return List.of(ElixirDependency.SMITHY_XML, ElixirDependency.SMITHY_HTTP_CLIENT);
    }
}
