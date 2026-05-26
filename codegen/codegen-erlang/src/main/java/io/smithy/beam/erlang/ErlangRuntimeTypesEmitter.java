package io.smithy.beam.erlang;

public final class ErlangRuntimeTypesEmitter {

    private ErlangRuntimeTypesEmitter() {}

    public static void writeBody(ErlangWriter writer) {
        writer.write("-ifndef($L).", "BEAM_RUNTIME_TYPES_INCLUDED");
        writer.write("-define($L, true).", "BEAM_RUNTIME_TYPES_INCLUDED");
        writer.write("");
        writer.write("%% HTTP carrier types for generated clients. Adjust only via codegen.");
        writer.write("-record(http_request, {");
        writer.write("  method = <<\"GET\">> :: binary(),");
        writer.write("  path = <<\"/\">> :: binary(),");
        writer.write("  query = #{} :: #{binary() => binary()},");
        writer.write("  headers = [] :: [{binary(), binary()}],");
        writer.write("  body = <<>> :: iodata()");
        writer.write("}).");
        writer.write("-type http_request() :: #http_request{}.");
        writer.write("");
        writer.write("-record(http_response, {");
        writer.write("  status = 200 :: non_neg_integer(),");
        writer.write("  headers = [] :: [{binary(), binary()}],");
        writer.write("  body = <<>> :: iodata()");
        writer.write("}).");
        writer.write("-type http_response() :: #http_response{}.");
        writer.write("");
        writer.write("-type beam_middleware() :: fun((http_request()) -> http_request()).");
        writer.write("-type retry_policy() :: #{max_attempts => pos_integer(), base_delay_ms => pos_integer()}.");
        writer.write("-type timeout_ms() :: non_neg_integer() | infinity.");
        writer.write("-record(endpoint_resolution, {");
        writer.write("  base_url :: binary(),");
        writer.write("  host_prefix = <<>> :: binary(),");
        writer.write("  path_prefix = <<>> :: binary()");
        writer.write("}).");
        writer.write("-type endpoint_resolution() :: #endpoint_resolution{}.");
        writer.write("");
        writer.write("-record(auth_context, {");
        writer.write("  scheme = undefined :: undefined | binary(),");
        writer.write("  signer = undefined :: undefined | module()");
        writer.write("}).");
        writer.write("-type auth_context() :: #auth_context{}.");
        writer.write("");
        writer.write("-endif.");
    }
}
