package io.smithy.beam.elixir.codegen.codec;

import io.smithy.beam.elixir.codegen.ElixirContext;
import io.smithy.beam.elixir.codegen.ElixirDependency;
import io.smithy.beam.elixir.codegen.ElixirWriter;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * {@link ElixirCodec} for the {@code ec2Query} protocol.
 *
 * <p>Identical to {@link QueryCodec} except that the {@code encoding:} atom
 * is {@code :ec2_query}, instructing the runtime {@code SmithyQuery} module
 * to apply EC2-specific member name capitalisation when building the
 * URL-form-encoded request body.
 *
 * <p>Stateless; one instance may be shared across all operations of a service.
 */
public final class Ec2QueryCodec implements ElixirCodec {

    @Override
    public String contentType() {
        return "application/x-www-form-urlencoded";
    }

    /**
     * Emits the {@code content_type:} and {@code encoding: :ec2_query} fields
     * for the {@code %SmithyClient.Operation{}} struct.
     */
    @Override
    public void writeRequestEncode(ElixirWriter w, ElixirContext ctx, OperationShape op) {
        w.addDependency(ElixirDependency.SMITHY_QUERY);

        w.write("content_type: $S,", contentType());
        w.write("encoding: :ec2_query,");
        w.write("action: $S,", op.getId().getName());
    }

    /**
     * Emits the {@code decoding: :xml} field for the
     * {@code %SmithyClient.Operation{}} struct. EC2-Query protocol responses
     * are XML documents.
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
