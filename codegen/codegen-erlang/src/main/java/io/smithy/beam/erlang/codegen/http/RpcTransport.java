package io.smithy.beam.erlang.codegen.http;

import io.smithy.beam.erlang.codegen.ErlangContext;
import io.smithy.beam.erlang.codegen.ErlangDependency;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import io.smithy.beam.erlang.codegen.codec.ErlangTransport;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * {@link ErlangTransport} for AWS-style RPC protocols ({@code awsJson1_0},
 * {@code awsJson1_1}, {@code awsQuery}, {@code ec2Query}). Always
 * {@code POST}s to {@code "/"} and adds the {@code X-Amz-Target} header
 * derived from the service name and operation name.
 *
 * <p>Stateless; one instance is reused across every operation of a service.
 */
public final class RpcTransport implements ErlangTransport {

    /**
     * Emits the {@code Method = <<"POST">>}, {@code Url = …}, and
     * {@code Headers = …} bindings for an RPC-style request. The
     * {@code X-Amz-Target} header value is constructed as
     * {@code "<ServiceName>.<OperationName>"}.
     */
    @Override
    public void writeRequest(ErlangWriter w, ErlangContext ctx, OperationShape op) {
        w.addDependency(ErlangDependency.SMITHY_HTTP_CLIENT);

        ServiceShape service = ctx.service();
        String target = service.getId().getName() + "." + op.getId().getName();

        w.write("Method = <<\"POST\">>,");
        w.write("QueryString = <<>>,");
        w.write("Endpoint = maps:get(endpoint, Client),");
        w.write("Uri = <<\"/\">>,");
        w.write("Url = <<Endpoint/binary, Uri/binary, QueryString/binary>>,");
        w.openBlock("Headers = [");
        w.write("{<<\"Content-Type\">>, <<\"application/x-amz-json-1.0\">>},");
        w.write("{<<\"X-Amz-Target\">>, <<$S>>}", target);
        w.closeBlock("],");
    }

    /**
     * Emits the {@code case smithy_sigv4:sign_request(…) of} dispatch envelope
     * and the inner {@code httpc:request/4} call. Identical to the REST
     * transport's response handling — RPC vs. REST only differs on the
     * request side.
     */
    @Override
    public void writeResponse(ErlangWriter w, ErlangContext ctx, OperationShape op, Runnable decodeSuccessBody) {
        w.addDependency(ErlangDependency.SMITHY_HTTP_CLIENT);

        w.openBlock("case smithy_sigv4:sign_request(Method, Url, Headers, Body, Client) of");
        w.openBlock("{ok, SignedHeaders} ->");
        w.write("StringUrl = binary_to_list(Url),");
        w.write("StringHeaders = [{binary_to_list(K), binary_to_list(V)} || {K, V} <- SignedHeaders],");
        w.write("ContentType = binary_to_list(proplists:get_value("
                + "<<\"Content-Type\">>, Headers, <<\"application/x-amz-json-1.0\">>)),");
        w.write("Request = {StringUrl, StringHeaders, ContentType, Body},");
        w.openBlock("case httpc:request(post, Request, [], [{body_format, binary}]) of");
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
}
