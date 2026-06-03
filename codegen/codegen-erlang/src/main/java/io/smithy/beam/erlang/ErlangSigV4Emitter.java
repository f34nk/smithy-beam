package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamSigV4Metadata;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Emits a thin {@code <service>_sigv4.erl} signing hook for services with
 * {@code @aws.auth#sigv4}. Callers may supply credentials in client config or
 * rely on the generated credential provider module.
 */
public final class ErlangSigV4Emitter {

    private ErlangSigV4Emitter() {}

    public static void emit(ErlangContext ctx, ServiceShape service) {
        if (BeamSigV4Metadata.from(service).isEmpty()) {
            return;
        }

        BeamErlangLayout layout = new BeamErlangLayout(
                ctx.settings(), service.getId().getNamespace(), service);
        String sigv4Module = layout.sigv4ModuleName();

        ctx.writerDelegator().useFileWriter(layout.sigv4ModuleFile(), writer -> {
            writer.write("%% Generated SigV4 signing hook for $L.", service.getId());
            writer.write("-module($L).", sigv4Module);
            writer.write("-include(\"$L\").", layout.runtimeTypesHeaderFile());
            writer.write("-export([sign/3, endpoint_host_from_config/1]).");
            writer.write("");
            writer.write("-type client_config() :: #{binary() => term()}.");
            writer.write("-spec sign(client_config(), Operation :: atom(), http_request()) -> http_request().");
            writer.write("sign(Config, Operation, Request) ->");
            writer.indent();
            writer.write("Credentials = maps:get(credentials, Config),");
            writer.write("Region = maps:get(region, Config, <<\"us-east-1\">>),");
            writer.write("Service = maps:get(signing_name, Config),");
            writer.write("Unsigned = maps:get({unsigned_payload, Operation}, Config, false),");
            writer.write("Opts = #{");
            writer.write("    unsigned_payload => Unsigned,");
            writer.write("    endpoint_host => endpoint_host_from_config(Config)");
            writer.write("},");
            writer.write("aws_sigv4:sign(Request, Credentials, Region, Service, Opts).");
            writer.dedent();
            writer.write("");
            writer.write("endpoint_host_from_config(Config) ->");
            writer.indent();
            writer.write("case maps:get(base_url, Config, undefined) of");
            writer.indent();
            writer.write("undefined ->");
            writer.indent();
            writer.write("case {maps:get(endpoint_prefix, Config, undefined),");
            writer.write("      maps:get(region, Config, <<\"us-east-1\">>)} of");
            writer.indent();
            writer.write("{undefined, _} -> undefined;");
            writer.write("{Prefix, Region} -> <<Prefix/binary, \".\", Region/binary, \".amazonaws.com\">>");
            writer.dedent();
            writer.write("end;");
            writer.dedent();
            writer.write("BaseUrl ->");
            writer.indent();
            writer.write("{_Scheme, Authority} = split_base_url(BaseUrl),");
            writer.write("Authority");
            writer.dedent();
            writer.dedent();
            writer.write("end.");
            writer.dedent();
            writer.write("");
            writer.write("split_base_url(<<>>) ->");
            writer.indent();
            writer.write("{<<>>, <<>>};");
            writer.dedent();
            writer.write("split_base_url(BaseUrl) ->");
            writer.indent();
            writer.write("case uri_string:parse(binary_to_list(BaseUrl)) of");
            writer.indent();
            writer.write("#{scheme := Scheme, host := Host} = Parts ->");
            writer.indent();
            writer.write("PortSuffix = case maps:get(port, Parts, undefined) of");
            writer.indent();
            writer.write("undefined -> <<>>;");
            writer.write("Port -> <<\":\", (integer_to_binary(Port))/binary>>");
            writer.dedent();
            writer.write("end,");
            writer.write("{<< (list_to_binary(Scheme))/binary, \"://\">>,");
            writer.write(" << (list_to_binary(Host))/binary, PortSuffix/binary >>};");
            writer.dedent();
            writer.write("_ ->");
            writer.indent();
            writer.write("{<<>>, BaseUrl}");
            writer.dedent();
            writer.dedent();
            writer.write("end.");
            writer.dedent();
        });
    }
}
