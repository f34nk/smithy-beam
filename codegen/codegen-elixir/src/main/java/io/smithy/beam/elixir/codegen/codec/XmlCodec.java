package io.smithy.beam.elixir.codegen.codec;

import io.smithy.beam.elixir.codegen.ElixirContext;
import io.smithy.beam.elixir.codegen.ElixirDependency;
import io.smithy.beam.elixir.codegen.ElixirWriter;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 * {@link ElixirCodec} for the {@code restXml} protocol.
 *
 * <p>Emits declarative fields into the {@code %SmithyClient.Operation{}} struct
 * that drive runtime XML encode / decode in the {@code SmithyXml} module.
 *
 * <p>Stateless; one instance may be shared across all operations of a service.
 */
public final class XmlCodec implements ElixirCodec {

    @Override
    public String contentType() {
        return "application/xml";
    }

    /**
     * Emits the {@code content_type:} and {@code encoding:} fields for the
     * {@code %SmithyClient.Operation{}} struct.
     *
     * <p>If the input shape has no members the encoding is {@code :none};
     * otherwise it is {@code :xml}.
     */
    @Override
    public void writeRequestEncode(ElixirWriter w, ElixirContext ctx, OperationShape op) {
        w.addDependency(ElixirDependency.SMITHY_XML);

        StructureShape inputShape =
                ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        String encoding = inputShape.getAllMembers().isEmpty() ? ":none" : ":xml";

        w.write("content_type: $S,", contentType());
        w.write("encoding: $L,", encoding);
    }

    /**
     * Emits the {@code decoding: :xml} field for the
     * {@code %SmithyClient.Operation{}} struct.
     */
    @Override
    public void writeResponseDecode(ElixirWriter w, ElixirContext ctx, OperationShape op) {
        w.addDependency(ElixirDependency.SMITHY_XML);

        w.write("decoding: :xml,");
    }

    /**
     * Emits the {@code parse_error_fn:} field pointing to the XML error
     * parser in the {@code SmithyXml} module.
     */
    @Override
    public void writeErrorDecode(ElixirWriter w, ElixirContext ctx, OperationShape op) {
        w.addDependency(ElixirDependency.SMITHY_XML);

        w.write("parse_error_fn: &SmithyXml.parse_error/2,");
    }
}
