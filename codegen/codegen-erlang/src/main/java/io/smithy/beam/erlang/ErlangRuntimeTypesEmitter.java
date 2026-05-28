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
        writer.write("-endif.");
    }
}
