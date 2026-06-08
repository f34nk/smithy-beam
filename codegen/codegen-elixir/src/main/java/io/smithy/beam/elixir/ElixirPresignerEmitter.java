package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSigV4Metadata;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Emits {@code <service>_presigner.ex} with presigned URL helpers for SigV4 services.
 */
public final class ElixirPresignerEmitter {

    private ElixirPresignerEmitter() {}

    public static void emit(ElixirContext ctx, ServiceShape service) {
        if (BeamSigV4Metadata.from(service).isEmpty()) {
            return;
        }

        BeamElixirLayout layout = new BeamElixirLayout(
                ctx.settings(), service.getId().getNamespace(), service);
        String presignerModule = ElixirSymbolProvider.toModuleName(layout.presignerModuleName());
        String sigv4Module = ElixirSymbolProvider.toModuleName(layout.sigv4ModuleName());
        String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());

        ctx.writerDelegator().useFileWriter(layout.presignerModuleFile(), writer -> {
            writer.write("defmodule $L do", presignerModule);
            writer.indent();
            writer.write("@moduledoc false");
            writer.write("alias $L, as: RuntimeTypes", runtimeMod);
            writer.write("alias $L, as: ServiceSigv4", sigv4Module);
            writer.write("");
            ElixirFormat.writeSpec(
                    writer,
                    "@spec",
                    "presign_url",
                    "map(), atom(), RuntimeTypes.HttpRequest.t()",
                    "{:ok, String.t()} | {:error, term()}");
            writer.write("def presign_url(config, operation, request) do");
            writer.indent();
            writer.write("credentials = Map.fetch!(config, :credentials)");
            writer.write("region = Map.get(config, :region, \"us-east-1\")");
            writer.write("service = Map.fetch!(config, :signing_name)");
            writer.write("expires = Map.get(config, :presign_expires, 900)");
            writer.write("unsigned = Map.get(config, {:unsigned_payload, operation}, false)");
            writer.write("opts = %{");
            writer.write("  expires: expires,");
            writer.write("  unsigned_payload: unsigned,");
            writer.write("  endpoint_host: ServiceSigv4.endpoint_host_from_config(config)");
            writer.write("}");
            writer.write("ServiceSigv4.presign(request, credentials, region, service, opts)");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
        });
    }
}
