package io.smithy.beam.erlang.codegen.protocol;

import io.smithy.beam.erlang.codegen.DefaultErlangProtocolIntegration;
import io.smithy.beam.erlang.codegen.codec.ErlangCodec;
import io.smithy.beam.erlang.codegen.codec.ErlangTransport;
import io.smithy.beam.erlang.codegen.codec.XmlCodec;
import io.smithy.beam.erlang.codegen.http.RestTransport;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Erlang codegen integration for the {@code aws.protocols#restXml} protocol.
 *
 * <p>Pairs the {@link XmlCodec} with the {@link RestTransport}.
 */
public final class ErlangRestXmlIntegration extends DefaultErlangProtocolIntegration {

    @Override
    public ShapeId protocolId() {
        return ShapeId.from("aws.protocols#restXml");
    }

    @Override
    protected ErlangCodec codec() {
        return new XmlCodec();
    }

    @Override
    protected ErlangTransport transport() {
        return new RestTransport();
    }
}
