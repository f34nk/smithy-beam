package io.smithy.beam.elixir.codegen.codec;

import io.smithy.beam.elixir.codegen.ElixirContext;
import io.smithy.beam.elixir.codegen.ElixirDependency;
import io.smithy.beam.elixir.codegen.ElixirWriter;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * {@link ElixirCodec} for the {@code awsQuery} protocol.
 *
 * <p>Emits declarative fields into the {@code %SmithyClient.Operation{}} struct
 * that drive URL-form-encoded request serialisation ({@code :query}) and
 * XML response deserialisation ({@code :xml}) in the runtime modules.
 *
 * <p>Stateless; one instance may be shared across all operations of a service.
 */
public final class QueryCodec implements ElixirCodec {

    @Override
    public String contentType() {
        return "application/x-www-form-urlencoded";
    }

    /**
     * Emits the {@code content_type:} and {@code encoding: :query} fields for
     * the {@code %SmithyClient.Operation{}} struct. The runtime
     * {@code SmithyQuery} module serialises the input struct to
     * URL-form-encoded key-value pairs prefixed with {@code Action=…&Version=…}.
     */
    @Override
    public void writeRequestEncode(ElixirWriter w, ElixirContext ctx, OperationShape op) {
        w.addDependency(ElixirDependency.SMITHY_QUERY);

        w.write("content_type: $S,", contentType());
        w.write("encoding: :query,");
        w.write("action: $S,", op.getId().getName());
    }

    /**
     * Emits the {@code decoding: :xml} field for the
     * {@code %SmithyClient.Operation{}} struct. Query-protocol responses are
     * XML documents that the runtime {@code SmithyXml} module decodes.
     */
    @Override
    public void writeResponseDecode(ElixirWriter w, ElixirContext ctx, OperationShape op) {
        w.addDependency(ElixirDependency.SMITHY_XML);

        w.write("decoding: :xml,");
    }

    /**
     * Emits the {@code parse_error_fn:} field pointing to the XML error
     * parser in the {@code SmithyXml} module (Query error responses are XML).
     */
    @Override
    public void writeErrorDecode(ElixirWriter w, ElixirContext ctx, OperationShape op) {
        w.addDependency(ElixirDependency.SMITHY_XML);

        w.write("parse_error_fn: &SmithyXml.parse_error/2,");
    }
}
