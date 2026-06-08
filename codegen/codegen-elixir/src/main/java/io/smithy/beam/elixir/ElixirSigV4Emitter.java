package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSigV4Metadata;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Emits a self-contained {@code <service>_sigv4.ex} signing module for services with
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
            writer.write("alias RuntimeTypes.HttpRequest");
            writer.write("");
            ElixirFormat.writeSpec(
                    writer,
                    "@spec",
                    "sign",
                    "map(), atom(), RuntimeTypes.HttpRequest.t()",
                    "RuntimeTypes.HttpRequest.t()");
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
            writer.write("sign_request(request, credentials, region, service, opts)");
            writer.dedent();
            writer.write("end");
            writer.write("");
            ElixirFormat.writeSpec(
                    writer,
                    "@spec",
                    "presign(HttpRequest.t(), map(), String.t(), String.t(), map()) :: {:ok, String.t()} | {:error, term()}");
            writer.write("def presign(%HttpRequest{} = request, credentials, region, service, opts) do");
            writer.indent();
            writer.write("access_key_id = Map.fetch!(credentials, :access_key_id)");
            writer.write("secret_access_key = Map.fetch!(credentials, :secret_access_key)");
            writer.write("datetime = :calendar.universal_time()");
            writer.write("host = resolve_host(request, opts)");
            writer.write("url = build_url(host, request.path, request.query)");
            writer.write("ttl = Map.get(opts, :expires, 900)");
            writer.write("");
            writer.write("query_opts =");
            writer.write("  [{:ttl, ttl}, {:uri_encode_path, service != \"s3\"}]");
            writer.write("  |> Kernel.++(body_digest_option(opts))");
            writer.write("  |> Kernel.++(session_token_option(Map.get(credentials, :session_token)))");
            writer.write("");
            writer.write("try do");
            writer.indent();
            writer.write("{:ok,");
            writer.write(" :aws_signature.sign_v4_query_params(");
            writer.indent();
            writer.write("to_bin(access_key_id),");
            writer.write("to_bin(secret_access_key),");
            writer.write("to_bin(region),");
            writer.write("to_bin(service),");
            writer.write("datetime,");
            writer.write("to_bin(request.method),");
            writer.write("to_bin(url),");
            writer.write("query_opts");
            writer.dedent();
            writer.write(" )");
            writer.write(" |> to_string()}");
            writer.dedent();
            writer.write("catch");
            writer.indent();
            writer.write("_, reason -> {:error, reason}");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("defp sign_request(%HttpRequest{} = request, credentials, region, service, opts) do");
            writer.indent();
            writer.write("access_key_id = Map.fetch!(credentials, :access_key_id)");
            writer.write("secret_access_key = Map.fetch!(credentials, :secret_access_key)");
            writer.write("datetime = :calendar.universal_time()");
            writer.write("host = resolve_host(request, opts)");
            writer.write("url = build_url(host, request.path, request.query)");
            writer.write("headers0 = ensure_host_header(request.headers, host)");
            writer.write("headers1 = maybe_add_session_token(headers0, Map.get(credentials, :session_token))");
            writer.write("");
            writer.write("sign_opts =");
            writer.write("  [{:uri_encode_path, service != \"s3\"}] ++ body_digest_option(opts)");
            writer.write("");
            writer.write("signed_headers =");
            writer.write("  :aws_signature.sign_v4(");
            writer.indent();
            writer.write("to_bin(access_key_id),");
            writer.write("to_bin(secret_access_key),");
            writer.write("to_bin(region),");
            writer.write("to_bin(service),");
            writer.write("datetime,");
            writer.write("to_bin(request.method),");
            writer.write("to_bin(url),");
            writer.write("to_erl_headers(headers1),");
            writer.write("to_bin(request.body),");
            writer.write("sign_opts");
            writer.dedent();
            writer.write("  )");
            writer.write("");
            writer.write("%{request | headers: from_erl_headers(signed_headers)}");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("def endpoint_host_from_config(config) do");
            writer.indent();
            writer.write("case Map.get(config, :base_url) do");
            writer.indent();
            writer.write("nil ->");
            writer.indent();
            writer.write("case {Map.get(config, :endpoint_prefix), Map.get(config, :region, \"us-east-1\")} do");
            writer.indent();
            writer.write("{nil, _} -> nil");
            writer.write("");
            writer.write("{prefix, region} -> \"#{prefix}.#{region}.amazonaws.com\"");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("");
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
            writer.write("defp resolve_host(%HttpRequest{} = request, opts) do");
            writer.indent();
            writer.write("coalesce([");
            writer.write("  request.host,");
            writer.write("  Map.get(opts, :host),");
            writer.write("  Map.get(opts, :endpoint_host),");
            writer.write("  header_host(request.headers)");
            writer.write("])");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("defp coalesce([nil | rest]), do: coalesce(rest)");
            writer.write("defp coalesce([\"\" | rest]), do: coalesce(rest)");
            writer.write("defp coalesce([value | _]), do: value");
            writer.write("defp coalesce([]), do: \"localhost\"");
            writer.write("");
            writer.write("defp build_url(host, path, query) when map_size(query) == 0 do");
            writer.indent();
            writer.write("\"https://#{host}#{path}\"");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("defp build_url(host, path, query) do");
            writer.indent();
            writer.write("params = URI.encode_query(query)");
            writer.write("\"https://#{host}#{path}?#{params}\"");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("defp ensure_host_header(headers, host) do");
            writer.indent();
            writer.write("case header_host(headers) do");
            writer.indent();
            writer.write("nil -> [{\"host\", host} | headers]");
            writer.write("");
            writer.write("_ -> headers");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("defp header_host(headers) do");
            writer.indent();
            writer.write("Enum.find_value(headers, fn");
            writer.indent();
            writer.write("{\"host\", value} -> value");
            writer.write("");
            writer.write("{\"Host\", value} -> value");
            writer.write("");
            writer.write("_ -> nil");
            writer.dedent();
            writer.write("end)");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("defp maybe_add_session_token(headers, nil), do: headers");
            writer.write("");
            writer.write("defp maybe_add_session_token(headers, token) do");
            writer.indent();
            writer.write("if Enum.any?(headers, fn {k, _} -> String.downcase(k) == \"x-amz-security-token\" end) do");
            writer.indent();
            writer.write("headers");
            writer.dedent();
            writer.write("else");
            writer.indent();
            writer.write("[{\"x-amz-security-token\", token} | headers]");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("defp body_digest_option(opts) do");
            writer.indent();
            writer.write("if Map.get(opts, :unsigned_payload, false) do");
            writer.indent();
            writer.write("[{:body_digest, \"UNSIGNED-PAYLOAD\"}]");
            writer.dedent();
            writer.write("else");
            writer.indent();
            writer.write("[]");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("defp session_token_option(nil), do: []");
            writer.write("defp session_token_option(token), do: [{:session_token, to_bin(token)}]");
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
            writer.write("");
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
            writer.write("");
            writer.write("defp to_bin(value) when is_binary(value), do: value");
            writer.write("defp to_bin(value) when is_atom(value), do: Atom.to_string(value)");
            writer.write("defp to_bin(value), do: to_string(value)");
            writer.write("");
            writer.write("defp to_erl_headers(headers) do");
            writer.indent();
            writer.write("Enum.map(headers, fn {k, v} -> {to_bin(k), to_bin(v)} end)");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.write("defp from_erl_headers(headers) do");
            writer.indent();
            writer.write("Enum.map(headers, fn {k, v} -> {to_string(k), to_string(v)} end)");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end");
        });
    }
}
