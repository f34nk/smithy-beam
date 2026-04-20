package io.smithy.beam.erlang.codegen.protocol;

import io.smithy.beam.erlang.codegen.DefaultErlangProtocolIntegration;
import io.smithy.beam.erlang.codegen.codec.ErlangCodec;
import io.smithy.beam.erlang.codegen.codec.ErlangTransport;
import io.smithy.beam.erlang.codegen.codec.QueryCodec;
import io.smithy.beam.erlang.codegen.http.RpcTransport;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Erlang codegen integration for the {@code aws.protocols#awsQuery} protocol.
 *
 * <p>Pairs the {@link QueryCodec} with the {@link RpcTransport}.
 */
public final class ErlangAwsQueryIntegration extends DefaultErlangProtocolIntegration {

    @Override
    public ShapeId protocolId() {
        return ShapeId.from("aws.protocols#awsQuery");
    }

    @Override
    protected ErlangCodec codec() {
        return new QueryCodec();
    }

    @Override
    protected ErlangTransport transport() {
        return new RpcTransport();
    }
}
