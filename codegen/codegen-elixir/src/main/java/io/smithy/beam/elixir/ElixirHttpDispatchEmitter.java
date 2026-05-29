package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Emits {@code runtime_http.ex} with a Req-based HTTP dispatcher for generated clients.
 */
public final class ElixirHttpDispatchEmitter {

    private ElixirHttpDispatchEmitter() {}

    public static void emit(ElixirContext ctx, ServiceShape service) {
        BeamElixirLayout layout = new BeamElixirLayout(ctx.settings(),
                service.getId().getNamespace());
        String httpModule = ElixirSymbolProvider.toModuleName(layout.runtimeHttpModuleName());
        String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());

        ctx.writerDelegator().useFileWriter(
                layout.runtimeHttpModuleFile(), writer -> {
            writer.write("defmodule $L do", httpModule);
            writer.indent();
            writer.write("@moduledoc \"Generated HTTP dispatcher for Smithy service clients. Uses Req.\"");
            writer.write("alias $L, as: RuntimeTypes", runtimeMod);
            writer.write("");
            writer.write("@spec dispatch(map(), RuntimeTypes.HttpRequest.t()) ::");
            writer.write("        {:ok, RuntimeTypes.HttpResponse.t()} | {:error, term()}");
            writer.write("def dispatch(config, req) do");
            writer.indent();
            writer.write("http_client = Map.get(config, :http_client, ReqClient)");
            writer.write("dispatch(http_client, config, req)");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("@spec dispatch(module(), map(), RuntimeTypes.HttpRequest.t()) ::");
            writer.write("        {:ok, RuntimeTypes.HttpResponse.t()} | {:error, term()}");
            writer.write("def dispatch(http_client, config, %RuntimeTypes.HttpRequest{} = req) do");
            writer.indent();
            writer.write("base_url = Map.get(config, :base_url, \"\")");
            writer.write("url = base_url <> req.path");
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
            writer.write("{:ok, %RuntimeTypes.HttpResponse{");
            writer.write("  status: status,");
            writer.write("  headers: Enum.map(headers, fn {k, v} -> {k, v} end),");
            writer.write("  body: body");
            writer.write("}}");
            writer.dedent();
            writer.write("{:error, reason} ->");
            writer.indent();
            writer.write("{:error, reason}");
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
            writer.write("@spec request(keyword()) :: {:ok, map()} | {:error, term()}");
            writer.write("def request(req_opts) do");
            writer.indent();
            writer.write("case Req.new(req_opts) |> Req.run() do");
            writer.indent();
            writer.write("{:ok, %Req.Response{status: status, headers: headers, body: body}} ->");
            writer.indent();
            writer.write("{:ok, %{status: status, headers: headers, body: body}}");
            writer.dedent();
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
