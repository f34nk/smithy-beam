package io.smithy.beam.elixir.codegen.http;

import io.smithy.beam.core.binding.BindingHelper;
import io.smithy.beam.elixir.codegen.ElixirContext;
import io.smithy.beam.elixir.codegen.ElixirDependency;
import io.smithy.beam.elixir.codegen.ElixirWriter;
import io.smithy.beam.elixir.codegen.codec.ElixirTransport;
import java.util.List;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.traits.HttpHeaderTrait;

/**
 * {@link ElixirTransport} that derives the HTTP method, URI, and static
 * headers from {@code @http}, {@code @httpLabel}, and {@code @httpHeader}
 * traits via {@link BindingHelper}.
 *
 * <p>Used by the REST-style Smithy protocols ({@code restJson1},
 * {@code restXml}). Stateless; one instance is reused across every operation
 * of a service.
 */
public final class RestTransport implements ElixirTransport {

    /**
     * Emits the URI prep statement (if any path labels are present) followed
     * by the transport-side fields of the {@code %SmithyClient.Operation{}}
     * struct: {@code http:} (with method and URI) and {@code static_headers:}.
     *
     * <p>Assumed to be called inside the body of a {@code defp <op>_op(input)}
     * helper, with the {@code %SmithyClient.Operation{} } struct already open.
     */
    @Override
    public void writeRequest(ElixirWriter w, ElixirContext ctx, OperationShape op) {
        w.addDependency(ElixirDependency.SMITHY_HTTP_CLIENT);

        String method = BindingHelper.method(op);
        String uriExpr = renderUri(ctx, op);

        w.write("http: %{method: $S, uri: $L},", method, uriExpr);
        writeStaticHeaders(w, ctx, op);
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

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns either a literal {@code "/path"} string or a URI template
     * interpolation (e.g. {@code "/weather/#{URI.encode_www_form(city || "")}"})
     * based on whether the operation has any {@code @httpLabel} bindings.
     */
    private static String renderUri(ElixirContext ctx, OperationShape op) {
        String uriTemplate = BindingHelper.uriPattern(op).orElse("/");
        List<HttpBinding> labels = BindingHelper.labels(ctx.model(), op);
        if (labels.isEmpty()) {
            return "\"" + uriTemplate + "\"";
        }
        String rendered = uriTemplate;
        for (HttpBinding label : labels) {
            String memberName = label.getMemberName();
            String fieldName = ctx.symbolProvider().toMemberName(label.getMember());
            rendered = rendered.replace(
                    "{" + memberName + "}",
                    "#{URI.encode_www_form(" + fieldName + " || \"\")}");
        }
        return "\"" + rendered + "\"";
    }

    private static void writeStaticHeaders(ElixirWriter w, ElixirContext ctx, OperationShape op) {
        List<HttpBinding> headers = BindingHelper.headers(ctx.model(), op);
        if (headers.isEmpty()) {
            w.write("static_headers: [],");
            return;
        }
        w.openBlock("static_headers: [");
        for (int i = 0; i < headers.size(); i++) {
            HttpBinding h = headers.get(i);
            String wireKey = h.getMember().getTrait(HttpHeaderTrait.class)
                    .map(HttpHeaderTrait::getValue)
                    .orElse(h.getMemberName());
            String fieldName = ctx.symbolProvider().toMemberName(h.getMember());
            String suffix = i == headers.size() - 1 ? "" : ",";
            w.write("{$S, input.$L}$L", wireKey, fieldName, suffix);
        }
        w.closeBlock("],");
    }
}
