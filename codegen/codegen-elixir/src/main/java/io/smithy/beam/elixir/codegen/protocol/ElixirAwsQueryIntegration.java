package io.smithy.beam.elixir.codegen.protocol;

import io.smithy.beam.elixir.codegen.DefaultElixirProtocolIntegration;
import io.smithy.beam.elixir.codegen.codec.ElixirCodec;
import io.smithy.beam.elixir.codegen.codec.ElixirTransport;
import io.smithy.beam.elixir.codegen.codec.QueryCodec;
import io.smithy.beam.elixir.codegen.http.RpcTransport;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Elixir codegen integration for the {@code aws.protocols#awsQuery} protocol.
 *
 * <p>Pairs the {@link QueryCodec} with the {@link RpcTransport}.
 */
public final class ElixirAwsQueryIntegration extends DefaultElixirProtocolIntegration {

    @Override
    public ShapeId protocolId() {
        return ShapeId.from("aws.protocols#awsQuery");
    }

    @Override
    protected ElixirCodec codec() {
        return new QueryCodec();
    }

    @Override
    protected ElixirTransport transport() {
        return new RpcTransport();
    }
}
