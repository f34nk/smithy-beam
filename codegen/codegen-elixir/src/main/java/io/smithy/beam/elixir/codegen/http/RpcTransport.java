package io.smithy.beam.elixir.codegen.http;

import io.smithy.beam.elixir.codegen.ElixirContext;
import io.smithy.beam.elixir.codegen.ElixirDependency;
import io.smithy.beam.elixir.codegen.ElixirWriter;
import io.smithy.beam.elixir.codegen.codec.ElixirTransport;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * {@link ElixirTransport} for AWS-style RPC protocols ({@code awsJson1_0},
 * {@code awsJson1_1}, {@code awsQuery}, {@code ec2Query}). Always
 * {@code POST}s to {@code "/"} and adds the {@code X-Amz-Target} static
 * header derived from the service and operation names.
 *
 * <p>Stateless; one instance is reused across every operation of a service.
 */
public final class RpcTransport implements ElixirTransport {

    /**
     * Emits the transport-side fields of the {@code %SmithyClient.Operation{}}
     * struct: {@code http: %{method: "POST", uri: "/"}} and
     * {@code static_headers: [{"X-Amz-Target", "<Service>.<Op>"}]}.
     */
    @Override
    public void writeRequest(ElixirWriter w, ElixirContext ctx, OperationShape op) {
        w.addDependency(ElixirDependency.SMITHY_HTTP_CLIENT);

        ServiceShape service = ctx.service();
        String target = service.getId().getName() + "." + op.getId().getName();

        w.write("http: %{method: \"POST\", uri: \"/\"},");
        w.openBlock("static_headers: [");
        w.write("{\"X-Amz-Target\", $S}", target);
        w.closeBlock("],");
    }

    /**
     * Invokes {@code decodeSuccessBody} so the codec can emit its
     * {@code decoding:} field. The Elixir transport contributes nothing else
     * to the response side — runtime dispatch lives in {@code SmithyClient}.
     */
    @Override
    public void writeResponse(ElixirWriter w, ElixirContext ctx, OperationShape op, Runnable decodeSuccessBody) {
        w.addDependency(ElixirDependency.SMITHY_HTTP_CLIENT);
        decodeSuccessBody.run();
    }
}
