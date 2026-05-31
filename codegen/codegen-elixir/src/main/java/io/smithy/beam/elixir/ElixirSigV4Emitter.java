package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSigV4Metadata;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Emits a thin {@code <service>_sigv4.ex} signing hook for services with
 * {@code @aws.auth#sigv4}. Callers supply credentials in client config; no
 * bundled credential chain is generated.
 */
public final class ElixirSigV4Emitter {

    private ElixirSigV4Emitter() {}

    public static void emit(ElixirContext ctx, ServiceShape service) {
        if (BeamSigV4Metadata.from(service).isEmpty()) {
            return;
        }

        BeamElixirLayout layout = new BeamElixirLayout(
                ctx.settings(), service.getId().getNamespace(), service);
        String sigv4Module = ElixirSymbolProvider.toModuleName(layout.sigv4ModuleName());
        String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());

        ctx.writerDelegator().useFileWriter(layout.sigv4ModuleFile(), writer -> {
            writer.write("defmodule $L do", sigv4Module);
            writer.indent();
            writer.write("@moduledoc false");
            writer.write("alias $L, as: RuntimeTypes", runtimeMod);
            writer.write("");
            writer.write("@spec sign(map(), RuntimeTypes.HttpRequest.t()) :: RuntimeTypes.HttpRequest.t()");
            writer.write("def sign(config, request) do");
            writer.indent();
            writer.write("credentials = Map.fetch!(config, :credentials)");
            writer.write("region = Map.get(config, :region, \"us-east-1\")");
            writer.write("service = Map.fetch!(config, :signing_name)");
            writer.write("AwsSignature.sign(request, credentials, region, service)");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
        });
    }
}
