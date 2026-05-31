package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamSigV4Metadata;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Emits a thin {@code <service>_sigv4.erl} signing hook for services with
 * {@code @aws.auth#sigv4}. Callers supply credentials in client config; no
 * bundled credential chain is generated.
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
            writer.write("-export([sign/2]).");
            writer.write("");
            writer.write("-type client_config() :: #{binary() => term()}.");
            writer.write("-spec sign(client_config(), http_request()) -> http_request().");
            writer.write("sign(Config, Request) ->");
            writer.indent();
            writer.write("Credentials = maps:get(credentials, Config),");
            writer.write("Region = maps:get(region, Config, <<\"us-east-1\">>),");
            writer.write("Service = maps:get(signing_name, Config),");
            writer.write("aws_sigv4:sign(Request, Credentials, Region, Service).");
            writer.dedent();
        });
    }
}
