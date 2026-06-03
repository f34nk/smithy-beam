package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamSigV4Metadata;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Emits {@code <service>_presigner.erl} with presigned URL helpers for SigV4 services.
 */
public final class ErlangPresignerEmitter {

    private ErlangPresignerEmitter() {}

    public static void emit(ErlangContext ctx, ServiceShape service) {
        if (BeamSigV4Metadata.from(service).isEmpty()) {
            return;
        }

        BeamErlangLayout layout = new BeamErlangLayout(
                ctx.settings(), service.getId().getNamespace(), service);
        String presignerModule = layout.presignerModuleName();
        String sigv4Module = layout.sigv4ModuleName();

        ctx.writerDelegator().useFileWriter(layout.presignerModuleFile(), writer -> {
            writer.write("%% Generated presigned URL helper for $L.", service.getId());
            writer.write("-module($L).", presignerModule);
            writer.write("-include(\"$L\").", layout.runtimeTypesHeaderFile());
            writer.write("-export([presign_url/3]).");
            writer.write("");
            writer.write("-type client_config() :: #{binary() => term()}.");
            writer.write("-spec presign_url(client_config(), Operation :: atom(), http_request()) ->");
            writer.write("    {ok, binary()} | {error, term()}.");
            writer.write("presign_url(Config, Operation, Request) ->");
            writer.indent();
            writer.write("Credentials = maps:get(credentials, Config),");
            writer.write("Region = maps:get(region, Config, <<\"us-east-1\">>),");
            writer.write("Service = maps:get(signing_name, Config),");
            writer.write("Expires = maps:get(presign_expires, Config, 900),");
            writer.write("Unsigned = maps:get({unsigned_payload, Operation}, Config, false),");
            writer.write("Opts = #{");
            writer.write("    expires => Expires,");
            writer.write("    unsigned_payload => Unsigned,");
            writer.write("    endpoint_host => $L:endpoint_host_from_config(Config)", sigv4Module);
            writer.write("},");
            writer.write("aws_sigv4:presign(Request, Credentials, Region, Service, Opts).");
            writer.dedent();
        });
    }
}
