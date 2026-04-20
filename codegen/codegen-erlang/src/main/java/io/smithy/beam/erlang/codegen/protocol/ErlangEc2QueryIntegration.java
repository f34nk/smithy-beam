package io.smithy.beam.erlang.codegen.protocol;

import io.smithy.beam.erlang.codegen.DefaultErlangProtocolIntegration;
import io.smithy.beam.erlang.codegen.codec.Ec2QueryCodec;
import io.smithy.beam.erlang.codegen.codec.ErlangCodec;
import io.smithy.beam.erlang.codegen.codec.ErlangTransport;
import io.smithy.beam.erlang.codegen.http.RpcTransport;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Erlang codegen integration for the {@code aws.protocols#ec2Query} protocol.
 *
 * <p>Pairs the {@link Ec2QueryCodec} with the {@link RpcTransport}.
 */
public final class ErlangEc2QueryIntegration extends DefaultErlangProtocolIntegration {

    @Override
    public ShapeId protocolId() {
        return ShapeId.from("aws.protocols#ec2Query");
    }

    @Override
    protected ErlangCodec codec() {
        return new Ec2QueryCodec();
    }

    @Override
    protected ErlangTransport transport() {
        return new RpcTransport();
    }
}
