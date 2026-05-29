package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Emits {@code runtime_http.erl} with an httpc-based HTTP dispatcher for generated clients.
 * The wrapper is thin: it converts http_request() to httpc args and wraps the response.
 */
public final class ErlangHttpDispatchEmitter {

    private ErlangHttpDispatchEmitter() {}

    public static void emit(ErlangContext ctx, ServiceShape service) {
        BeamErlangLayout layout = new BeamErlangLayout(ctx.settings(),
                service.getId().getNamespace());
        String httpModule = layout.runtimeHttpModuleName();

        ctx.writerDelegator().useFileWriter(layout.runtimeHttpModuleFile(), writer -> {
            writer.write("%% Generated HTTP dispatcher for $L.", service.getId());
            writer.write("%% Uses httpc from OTP. Replace via adapter for testing.");
            writer.write("-module($L).", httpModule);
            writer.write("-include(\"$L\").", layout.runtimeTypesHeaderFile());
            writer.write("-export([dispatch/2, dispatch/3]).");
            writer.write("");
            writer.write("%% @doc Sends an http_request() and returns http_response().");
            writer.write("%% Config may contain `{base_url, ...}` and `{http_client, Module}` for tests.");
            writer.write("%% Uses httpc by default; pass another module for tests.");
            writer.write("dispatch(Config, Request) ->");
            writer.write("    HttpClient = maps:get(http_client, Config, httpc),");
            writer.write("    dispatch(HttpClient, Config, Request).");
            writer.write("");
            writer.write("dispatch(HttpClient, Config, #http_request{");
            writer.write("        method = Method, path = Path,");
            writer.write("        query = Query, headers = Headers, body = Body}) ->");
            writer.indent();
            writer.write("BaseUrl = maps:get(base_url, Config, <<\"\">>),");
            writer.write("QueryStr = case maps:to_list(Query) of");
            writer.indent();
            writer.write("[] -> <<>>;");
            writer.write("Pairs ->");
            writer.indent();
            writer.write("Encoded = uri_string:compose_query(");
            writer.write("    [{K, V} || {K, V} <- Pairs]),");
            writer.write("<<\"?\", Encoded/binary>>");
            writer.dedent();
            writer.dedent();
            writer.write("end,");
            writer.write("Url = <<BaseUrl/binary, Path/binary, QueryStr/binary>>,");
            writer.write("HttpcHeaders = [{binary_to_list(K), binary_to_list(V)}");
            writer.write("    || {K, V} <- Headers],");
            writer.write("Req = {binary_to_list(Url), HttpcHeaders, mime(Headers), Body},");
            writer.write("case HttpClient:request(binary_to_atom(string:lowercase(Method), utf8),");
            writer.write("        Req, [], [{body_format, binary}]) of");
            writer.indent();
            writer.write("{ok, {{_, Status, _}, RespHeaders, RespBody}} ->");
            writer.indent();
            writer.write("BinHeaders = [{list_to_binary(K), list_to_binary(V)}");
            writer.write("    || {K, V} <- RespHeaders],");
            writer.write("{ok, #http_response{");
            writer.write("    status = Status,");
            writer.write("    headers = BinHeaders,");
            writer.write("    body = RespBody}};");
            writer.dedent();
            writer.write("{error, Reason} ->");
            writer.indent();
            writer.write("{error, Reason}");
            writer.dedent();
            writer.dedent();
            writer.write("end.");
            writer.dedent();
            writer.write("");
            writer.write("mime(Headers) ->");
            writer.indent();
            writer.write("case proplists:get_value(<<\"Content-Type\">>, Headers) of");
            writer.indent();
            writer.write("undefined -> \"application/octet-stream\";");
            writer.write("CT -> binary_to_list(CT)");
            writer.dedent();
            writer.write("end.");
            writer.dedent();
        });
    }
}
