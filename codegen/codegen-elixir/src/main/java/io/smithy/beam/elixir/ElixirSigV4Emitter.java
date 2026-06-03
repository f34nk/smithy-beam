package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSigV4Metadata;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Emits a thin {@code <service>_sigv4.ex} signing hook for services with
 * {@code @aws.auth#sigv4}. Callers may supply credentials in client config or
 * rely on the generated credential provider module.
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
            writer.write("@spec sign(map(), atom(), RuntimeTypes.HttpRequest.t()) :: RuntimeTypes.HttpRequest.t()");
            writer.write("def sign(config, operation, request) do");
            writer.indent();
            writer.write("credentials = Map.fetch!(config, :credentials)");
            writer.write("region = Map.get(config, :region, \"us-east-1\")");
            writer.write("service = Map.fetch!(config, :signing_name)");
            writer.write("unsigned = Map.get(config, {:unsigned_payload, operation}, false)");
            writer.write("opts = %{");
            writer.write("  unsigned_payload: unsigned,");
            writer.write("  endpoint_host: endpoint_host_from_config(config)");
            writer.write("}");
            writer.write("AwsSignature.sign(request, credentials, region, service, opts)");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("defp endpoint_host_from_config(config) do");
            writer.indent();
            writer.write("case Map.get(config, :base_url) do");
            writer.indent();
            writer.write("nil ->");
            writer.indent();
            writer.write("case {Map.get(config, :endpoint_prefix), Map.get(config, :region, \"us-east-1\")} do");
            writer.indent();
            writer.write("{nil, _} -> nil");
            writer.write("{prefix, region} -> \"#{prefix}.#{region}.amazonaws.com\"");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("base_url ->");
            writer.indent();
            writer.write("{_scheme, authority} = split_base_url(base_url)");
            writer.write("authority");
            writer.dedent();
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("defp split_base_url(\"\"), do: {\"\", \"\"}");
            writer.write("defp split_base_url(base_url) do");
            writer.indent();
            writer.write("case URI.parse(base_url) do");
            writer.indent();
            writer.write("%URI{scheme: scheme, host: host} = uri when is_binary(host) ->");
            writer.indent();
            writer.write("port_suffix =");
            writer.indent();
            writer.write("case uri.port do");
            writer.indent();
            writer.write("nil -> \"\"");
            writer.write("port -> \":#{port}\"");
            writer.dedent();
            writer.write("end");
            writer.write("{\"#{scheme}://\", \"#{host}#{port_suffix}\"}");
            writer.dedent();
            writer.write("_ ->");
            writer.indent();
            writer.write("{\"\", base_url}");
            writer.dedent();
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
        });
    }
}
