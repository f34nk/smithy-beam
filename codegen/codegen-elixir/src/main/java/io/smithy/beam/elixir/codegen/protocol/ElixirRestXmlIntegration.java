package io.smithy.beam.elixir.codegen.protocol;

import io.smithy.beam.elixir.codegen.DefaultElixirProtocolIntegration;
import io.smithy.beam.elixir.codegen.codec.ElixirCodec;
import io.smithy.beam.elixir.codegen.codec.ElixirTransport;
import io.smithy.beam.elixir.codegen.codec.XmlCodec;
import io.smithy.beam.elixir.codegen.http.RestTransport;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Elixir codegen integration for the {@code aws.protocols#restXml} protocol.
 *
 * <p>Pairs the {@link XmlCodec} with the {@link RestTransport}.
 */
public final class ElixirRestXmlIntegration extends DefaultElixirProtocolIntegration {

    @Override
    public ShapeId protocolId() {
        return ShapeId.from("aws.protocols#restXml");
    }

    @Override
    protected ElixirCodec codec() {
        return new XmlCodec();
    }

    @Override
    protected ElixirTransport transport() {
        return new RestTransport();
    }
}
