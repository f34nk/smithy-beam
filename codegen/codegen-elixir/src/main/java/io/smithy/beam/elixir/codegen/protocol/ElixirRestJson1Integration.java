package io.smithy.beam.elixir.codegen.protocol;

import io.smithy.beam.elixir.codegen.DefaultElixirProtocolIntegration;
import io.smithy.beam.elixir.codegen.ElixirDependency;
import java.util.List;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Elixir codegen integration for the {@code aws.protocols#restJson1} protocol.
 *
 * <p>Adds {@code SMITHY_JSON} and {@code SMITHY_HTTP_CLIENT} runtime dependencies.
 */
public final class ElixirRestJson1Integration extends DefaultElixirProtocolIntegration {

    @Override
    public ShapeId protocolId() {
        return ShapeId.from("aws.protocols#restJson1");
    }

    @Override
    protected List<ElixirDependency> protocolDependencies() {
        return List.of(ElixirDependency.SMITHY_JSON, ElixirDependency.SMITHY_HTTP_CLIENT);
    }
}
