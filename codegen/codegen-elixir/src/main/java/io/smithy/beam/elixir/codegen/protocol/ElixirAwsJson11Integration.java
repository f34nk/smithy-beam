package io.smithy.beam.elixir.codegen.protocol;

import io.smithy.beam.elixir.codegen.DefaultElixirProtocolIntegration;
import io.smithy.beam.elixir.codegen.codec.ElixirCodec;
import io.smithy.beam.elixir.codegen.codec.ElixirTransport;
import io.smithy.beam.elixir.codegen.codec.JsonCodec;
import io.smithy.beam.elixir.codegen.http.RpcTransport;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Elixir codegen integration for the {@code aws.protocols#awsJson1_1} protocol.
 *
 * <p>Pairs the AWS-flavoured {@link JsonCodec} with the {@link RpcTransport}.
 */
public final class ElixirAwsJson11Integration extends DefaultElixirProtocolIntegration {

    @Override
    public ShapeId protocolId() {
        return ShapeId.from("aws.protocols#awsJson1_1");
    }

    @Override
    protected ElixirCodec codec() {
        return new JsonCodec(JsonCodec.AWS_FLAVOR);
    }

    @Override
    protected ElixirTransport transport() {
        return new RpcTransport();
    }
}
