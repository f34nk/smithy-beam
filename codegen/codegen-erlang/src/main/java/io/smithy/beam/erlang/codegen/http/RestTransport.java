package io.smithy.beam.erlang.codegen.http;

import io.smithy.beam.core.binding.BindingHelper;
import io.smithy.beam.erlang.codegen.ErlangContext;
import io.smithy.beam.erlang.codegen.ErlangDependency;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import io.smithy.beam.erlang.codegen.codec.ErlangTransport;
import java.util.List;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.utils.CaseUtils;

/**
 * {@link ErlangTransport} that derives the HTTP method, URI, query string and
 * headers from {@code @http}, {@code @httpLabel}, {@code @httpQuery}, and
 * {@code @httpHeader} traits via {@link BindingHelper}.
 *
 * <p>Used by the four REST-style Smithy protocols ({@code restJson1},
 * {@code restXml}, …). Stateless; one instance is reused across every
 * operation of a service.
 */
public final class RestTransport implements ErlangTransport {

    /**
     * Emits the {@code Method = …}, {@code Uri = …}, {@code QueryString = …},
     * {@code Url = …} and {@code Headers = …} bindings for the request, derived
     * from the operation's {@code @http} trait and member-level
     * {@code @httpLabel} / {@code @httpQuery} / {@code @httpHeader} bindings.
     *
     * <p>The codec-supplied {@code Body = …} binding is emitted separately by
     * the integration after this method returns.
     */
    @Override
    public void writeRequest(ErlangWriter w, ErlangContext ctx, OperationShape op) {
        w.addDependency(ErlangDependency.SMITHY_HTTP_CLIENT);

        String method = BindingHelper.method(op);
        String uriTemplate = BindingHelper.uriPattern(op).orElse("/");
        String inputRecord = inputRecordName(ctx, op);

        w.write("Method = <<$S>>,", method);

        writeQueryString(w, ctx, op, inputRecord);
        w.write("Endpoint = maps:get(endpoint, Client),");

        writeUri(w, ctx, op, uriTemplate, inputRecord);

        w.write("Url = <<Endpoint/binary, Uri/binary, QueryString/binary>>,");

        writeHeaders(w, ctx, op, inputRecord);
    }

    /**
     * Emits the {@code case smithy_sigv4:sign_request(…) of} dispatch envelope,
     * the inner {@code httpc:request/4} call, and the status-code branch.
     *
     * <p>{@code decodeSuccessBody} is invoked at the success branch where
     * {@code ResponseBody} is bound, allowing the codec to emit its
     * format-specific decode (e.g. {@code jsx:decode/2}).
     */
    @Override
    public void writeResponse(ErlangWriter w, ErlangContext ctx, OperationShape op, Runnable decodeSuccessBody) {
        w.addDependency(ErlangDependency.SMITHY_HTTP_CLIENT);

        w.openBlock("case smithy_sigv4:sign_request(Method, Url, Headers, Body, Client) of");
        w.openBlock("{ok, SignedHeaders} ->");
        w.write("StringUrl = binary_to_list(Url),");
        w.write("StringHeaders = [{binary_to_list(K), binary_to_list(V)} || {K, V} <- SignedHeaders],");
        w.write("Request = {StringUrl, StringHeaders},");
        w.openBlock("case httpc:request(binary_to_atom(string:lowercase(Method), utf8), "
                + "Request, [], [{body_format, binary}]) of");
        w.openBlock("{ok, {{_, StatusCode, _}, _RespHeaders, ResponseBody}} "
                + "when StatusCode >= 200, StatusCode < 300 ->");
        decodeSuccessBody.run();
        w.dedent();
        w.write("{ok, {{_, ErrStatusCode, _}, _RespHeaders, ErrorBody}} ->");
        w.indent();
        w.write("parse_error(ErrStatusCode, ErrorBody);");
        w.dedent();
        w.write("{error, Reason} ->");
        w.indent();
        w.write("{error, {http_error, Reason}}");
        w.dedent();
        w.closeBlock("end;");
        w.dedent();
        w.write("{error, SignError} ->");
        w.indent();
        w.write("{error, {signing_error, SignError}}");
        w.dedent();
        w.closeBlock("end.");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static String inputRecordName(ErlangContext ctx, OperationShape op) {
        StructureShape inputShape =
                ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        return CaseUtils.toSnakeCase(inputShape.getId().getName());
    }

    private static void writeQueryString(ErlangWriter w, ErlangContext ctx, OperationShape op, String inputRecord) {
        List<HttpBinding> queries = BindingHelper.queries(ctx.model(), op);
        if (queries.isEmpty()) {
            w.write("QueryString = <<>>,");
            return;
        }
        w.openBlock("QsParams = [");
        for (int i = 0; i < queries.size(); i++) {
            HttpBinding q = queries.get(i);
            String wireKey = q.getMember().getTrait(HttpQueryTrait.class)
                    .map(HttpQueryTrait::getValue)
                    .orElse(q.getMemberName());
            String fieldName = ctx.symbolProvider().toMemberName(q.getMember());
            String suffix = i == queries.size() - 1 ? "" : ",";
            w.write("{<<$S>>, Input#$L.$L}$L", wireKey, inputRecord, fieldName, suffix);
        }
        w.closeBlock("],");
        w.write("QsFiltered = [{K, ensure_binary(V)} || {K, V} <- QsParams, V =/= undefined],");
        w.openBlock("QueryString = case QsFiltered of");
        w.write("[] -> <<>>;");
        w.write("_ -> <<\"?\", (uri_string:compose_query(QsFiltered))/binary>>");
        w.closeBlock("end,");
    }

    private static void writeUri(ErlangWriter w, ErlangContext ctx, OperationShape op,
                                 String uriTemplate, String inputRecord) {
        List<HttpBinding> labels = BindingHelper.labels(ctx.model(), op);
        if (labels.isEmpty()) {
            w.write("Uri = <<$S>>,", uriTemplate);
            return;
        }
        w.write("Uri0 = <<$S>>,", uriTemplate);
        String prev = "Uri0";
        for (int i = 0; i < labels.size(); i++) {
            HttpBinding label = labels.get(i);
            String memberName = label.getMemberName();
            String fieldName = ctx.symbolProvider().toMemberName(label.getMember());
            String pascal = pascal(memberName);
            String valueVar = pascal + "Value";
            String encodedVar = pascal + "Encoded";
            String next = "Uri" + (i + 1);
            w.write("$L = Input#$L.$L,", valueVar, inputRecord, fieldName);
            w.write("$L = url_encode(ensure_binary($L)),", encodedVar, valueVar);
            w.write("$L = binary:replace($L, <<\"{$L}\">>, $L),",
                    next, prev, memberName, encodedVar);
            prev = next;
        }
        w.write("Uri = $L,", prev);
    }

    private static void writeHeaders(ErlangWriter w, ErlangContext ctx, OperationShape op, String inputRecord) {
        List<HttpBinding> headers = BindingHelper.headers(ctx.model(), op);
        w.write("Headers0 = [{<<\"Content-Type\">>, <<\"application/json\">>}],");
        if (headers.isEmpty()) {
            w.write("Headers = Headers0,");
            return;
        }
        String prev = "Headers0";
        for (int i = 0; i < headers.size(); i++) {
            HttpBinding header = headers.get(i);
            String wireKey = header.getMember().getTrait(HttpHeaderTrait.class)
                    .map(HttpHeaderTrait::getValue)
                    .orElse(header.getMemberName());
            String fieldName = ctx.symbolProvider().toMemberName(header.getMember());
            String next = "Headers" + (i + 1);
            String valVar = "Val" + (i + 1);
            w.openBlock("$L = case Input#$L.$L of", next, inputRecord, fieldName);
            w.write("undefined -> $L;", prev);
            w.write("$L -> [{<<$S>>, ensure_binary($L)} | $L]", valVar, wireKey, valVar, prev);
            w.closeBlock("end,");
            prev = next;
        }
        w.write("Headers = $L,", prev);
    }

    private static String pascal(String memberName) {
        if (memberName.isEmpty()) {
            return memberName;
        }
        return Character.toUpperCase(memberName.charAt(0)) + memberName.substring(1);
    }
}
