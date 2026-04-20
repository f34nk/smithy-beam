package io.smithy.beam.elixir.codegen.protocol;

import io.smithy.beam.elixir.codegen.DefaultElixirProtocolIntegration;
import io.smithy.beam.elixir.codegen.codec.Ec2QueryCodec;
import io.smithy.beam.elixir.codegen.codec.ElixirCodec;
import io.smithy.beam.elixir.codegen.codec.ElixirTransport;
import io.smithy.beam.elixir.codegen.http.RpcTransport;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Elixir codegen integration for the {@code aws.protocols#ec2Query} protocol.
 *
 * <p>Pairs the {@link Ec2QueryCodec} with the {@link RpcTransport}.
 */
public final class ElixirEc2QueryIntegration extends DefaultElixirProtocolIntegration {

    @Override
    public ShapeId protocolId() {
        return ShapeId.from("aws.protocols#ec2Query");
    }

    @Override
    protected ElixirCodec codec() {
        return new Ec2QueryCodec();
    }

    @Override
    protected ElixirTransport transport() {
        return new RpcTransport();
    }
}
