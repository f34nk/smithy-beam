package io.smithy.beam.erlang.codegen.protocol;

import io.smithy.beam.erlang.codegen.DefaultErlangProtocolIntegration;
import io.smithy.beam.erlang.codegen.codec.ErlangCodec;
import io.smithy.beam.erlang.codegen.codec.ErlangTransport;
import io.smithy.beam.erlang.codegen.codec.JsonCodec;
import io.smithy.beam.erlang.codegen.http.RpcTransport;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Erlang codegen integration for the {@code aws.protocols#awsJson1_0} protocol.
 *
 * <p>Pairs the AWS-flavoured {@link JsonCodec} (which understands {@code __type}
 * error discrimination) with the {@link RpcTransport}.
 */
public final class ErlangAwsJson10Integration extends DefaultErlangProtocolIntegration {

    @Override
    public ShapeId protocolId() {
        return ShapeId.from("aws.protocols#awsJson1_0");
    }

    @Override
    protected ErlangCodec codec() {
        return new JsonCodec(JsonCodec.AWS_FLAVOR);
    }

    @Override
    protected ErlangTransport transport() {
        return new RpcTransport();
    }
}
