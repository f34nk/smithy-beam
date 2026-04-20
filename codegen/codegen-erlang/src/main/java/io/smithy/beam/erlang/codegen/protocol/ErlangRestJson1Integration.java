package io.smithy.beam.erlang.codegen.protocol;

import io.smithy.beam.erlang.codegen.DefaultErlangProtocolIntegration;
import io.smithy.beam.erlang.codegen.codec.ErlangCodec;
import io.smithy.beam.erlang.codegen.codec.ErlangTransport;
import io.smithy.beam.erlang.codegen.codec.JsonCodec;
import io.smithy.beam.erlang.codegen.http.RestTransport;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Erlang codegen integration for the {@code aws.protocols#restJson1} protocol.
 *
 * <p>Pairs the standard {@link JsonCodec} with the {@link RestTransport}.
 */
public final class ErlangRestJson1Integration extends DefaultErlangProtocolIntegration {

    @Override
    public ShapeId protocolId() {
        return ShapeId.from("aws.protocols#restJson1");
    }

    @Override
    protected ErlangCodec codec() {
        return new JsonCodec();
    }

    @Override
    protected ErlangTransport transport() {
        return new RestTransport();
    }
}
