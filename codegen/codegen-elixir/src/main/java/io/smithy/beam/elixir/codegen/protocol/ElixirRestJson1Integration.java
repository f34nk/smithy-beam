package io.smithy.beam.elixir.codegen.protocol;

import io.smithy.beam.elixir.codegen.DefaultElixirProtocolIntegration;
import io.smithy.beam.elixir.codegen.codec.ElixirCodec;
import io.smithy.beam.elixir.codegen.codec.ElixirTransport;
import io.smithy.beam.elixir.codegen.codec.JsonCodec;
import io.smithy.beam.elixir.codegen.http.RestTransport;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Elixir codegen integration for the {@code aws.protocols#restJson1} protocol.
 *
 * <p>Pairs the standard {@link JsonCodec} with the {@link RestTransport}.
 */
public final class ElixirRestJson1Integration extends DefaultElixirProtocolIntegration {

    @Override
    public ShapeId protocolId() {
        return ShapeId.from("aws.protocols#restJson1");
    }

    @Override
    protected ElixirCodec codec() {
        return new JsonCodec();
    }

    @Override
    protected ElixirTransport transport() {
        return new RestTransport();
    }
}
