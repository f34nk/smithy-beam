package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamEndpointRuleSetEmitter;
import io.smithy.beam.core.BeamSigV4Metadata;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Emits {@code runtime_http.ex} with a Req-based HTTP dispatcher for generated clients.
 */
public final class ElixirHttpDispatchEmitter {

    private ElixirHttpDispatchEmitter() {}

    public static void emit(ElixirContext ctx, ServiceShape service) {
        BeamElixirLayout layout = new BeamElixirLayout(ctx.settings(),
                service.getId().getNamespace(), service);
        String httpModule = ElixirSymbolProvider.toModuleName(layout.runtimeHttpModuleName());
        String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
        String helpersModule = ElixirSymbolProvider.toModuleName(layout.runtimeHelpersModuleName());
        boolean sigv4 = BeamSigV4Metadata.from(service).isPresent();
        boolean endpointRules = BeamEndpointRuleSetEmitter.hasRuleSet(ctx.model(), service);
        String endpointsModule = ElixirSymbolProvider.toModuleName(layout.endpointsModuleName());
        String credentialsModule = ElixirSymbolProvider.toModuleName(layout.credentialsModuleName());
        String configVar = sigv4 ? "config1" : "config";

        ctx.writerDelegator().useFileWriter(
                layout.runtimeHttpModuleFile(), writer -> {
            writer.write("defmodule $L do", httpModule);
            writer.indent();
            writer.write("@moduledoc \"Generated HTTP dispatcher for Smithy service clients. Uses Req.\"");
            writer.write("alias $L, as: RuntimeTypes", runtimeMod);
            writer.write("alias $L, as: RuntimeHelpers", helpersModule);
            writer.write("");
            ElixirFormat.writeSpec(
                    writer,
                    "@spec",
                    "dispatch",
                    "map(), RuntimeTypes.HttpRequest.t()",
                    "{:ok, RuntimeTypes.HttpResponse.t()} | {:error, term()}");
            writer.write("def dispatch(config, req) do");
            writer.indent();
            writer.write("http_client = Map.get(config, :http_client, __MODULE__.ReqClient)");
            writer.write("dispatch(http_client, config, req)");
            writer.dedent();
            writer.write("end");
            writer.write("");
            ElixirFormat.writeSpec(
                    writer,
                    "@spec",
                    "dispatch",
                    "module(), map(), RuntimeTypes.HttpRequest.t()",
                    "{:ok, RuntimeTypes.HttpResponse.t()} | {:error, term()}");
            writer.write("def dispatch(http_client, config, %RuntimeTypes.HttpRequest{} = req) do");
            writer.indent();
            writer.write("dispatch_signed(http_client, config, req)");
            writer.dedent();
            writer.write("end");
            writer.write("");
            ElixirFormat.writeSpec(
                    writer,
                    "@spec",
                    "dispatch_signed",
                    "module(), map(), RuntimeTypes.HttpRequest.t()",
                    "{:ok, RuntimeTypes.HttpResponse.t()} | {:error, term()}");
            writer.write("defp dispatch_signed(http_client, config, %RuntimeTypes.HttpRequest{} = req) do");
            writer.indent();
            if (sigv4) {
                writer.write("config1 =");
                writer.indent();
                writer.write("case Map.get(config, :credentials) do");
                writer.indent();
                writer.write("nil ->");
                writer.indent();
                writer.write("case $L.resolve(config) do", credentialsModule);
                writer.indent();
                writer.write("{:ok, creds} -> Map.put(config, :credentials, creds)");
                writer.write("_ -> config");
                writer.dedent();
                writer.write("end");
                writer.dedent();
                writer.write("_ -> config");
                writer.dedent();
                writer.write("end");
                writer.dedent();
            }
            writer.write("base_url =");
            writer.indent();
            writer.write("case Map.get($L, :base_url) do", configVar);
            writer.indent();
            writer.write("nil ->");
            writer.indent();
            writer.write("case Map.get($L, :endpoint_prefix) do", configVar);
            writer.indent();
            writer.write("nil -> \"\"");
            if (endpointRules) {
                writer.write("_ ->");
                writer.indent();
                writer.write("case $L.resolve($L, %{}) do", endpointsModule, configVar);
                writer.indent();
                writer.write("{:ok, %{url: url}} -> url");
                writer.write("_ -> RuntimeHelpers.resolve_base_url($L)", configVar);
                writer.dedent();
                writer.write("end");
                writer.dedent();
            } else {
                writer.write("_ -> RuntimeHelpers.resolve_base_url($L)", configVar);
            }
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("url -> url");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("{scheme, default_authority} = split_base_url(base_url)");
            writer.write("");
            writer.write("authority =");
            writer.indent();
            writer.write("case req.host do");
            writer.indent();
            writer.write("nil -> default_authority");
            writer.write("host -> host");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("url = scheme <> authority <> req.path");
            writer.write("req_opts = [");
            writer.write("  method: String.downcase(req.method) |> String.to_atom(),");
            writer.write("  url: url,");
            writer.write("  params: req.query,");
            writer.write("  headers: req.headers,");
            writer.write("  body: req.body");
            writer.write("]");
            writer.write("case http_client.request(req_opts) do");
            writer.indent();
            writer.write("{:ok, %{status: status, headers: headers, body: body}} ->");
            writer.indent();
            writer.write("{:ok,");
            writer.write("%RuntimeTypes.HttpResponse{");
            writer.write("  status: status,");
            writer.write("  headers: Enum.map(headers, fn {k, v} -> {k, v} end),");
            writer.write("  body: body");
            writer.write("}}");
            writer.dedent();
            writer.write("");
            writer.write("{:error, reason} ->");
            writer.indent();
            writer.write("{:error, reason}");
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
            writer.write("case {uri.scheme, uri.port} do");
            writer.indent();
            writer.write("{\"https\", 443} -> \"\"");
            writer.write("{\"http\", 80} -> \"\"");
            writer.write("{_, nil} -> \"\"");
            writer.write("{_, port} -> \":#{port}\"");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("{scheme <> \"://\", host <> port_suffix}");
            writer.dedent();
            writer.write("");
            writer.write("_ ->");
            writer.indent();
            writer.write("{\"\", base_url}");
            writer.dedent();
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("defmodule ReqClient do");
            writer.indent();
            writer.write("@moduledoc false");
            writer.write("");
            ElixirFormat.writeSpec(
                    writer,
                    "@spec",
                    "request",
                    "keyword()",
                    "{:ok, map()} | {:error, term()}");
            writer.write("def request(req_opts) do");
            writer.indent();
            writer.write("case Req.request(req_opts) do");
            writer.indent();
            writer.write("{:ok, %Req.Response{status: status, headers: headers, body: body}} ->");
            writer.indent();
            writer.write("{:ok, %{status: status, headers: headers, body: body}}");
            writer.dedent();
            writer.write("");
            writer.write("{:error, reason} ->");
            writer.indent();
            writer.write("{:error, reason}");
            writer.dedent();
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
        });
    }
}
