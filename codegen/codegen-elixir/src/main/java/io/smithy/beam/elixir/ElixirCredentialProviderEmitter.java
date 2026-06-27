package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamCredentialProviders;
import io.smithy.beam.core.BeamCredentialProviders.BeamCredentialProviderKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSigV4Metadata;
import java.util.stream.Collectors;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Emits {@code <service>_credentials.ex} with a default AWS credential resolution chain for
 * services with {@code @aws.auth#sigv4}.
 */
public final class ElixirCredentialProviderEmitter {

  private ElixirCredentialProviderEmitter() {}

  public static void emit(ElixirContext ctx, ServiceShape service) {
    if (BeamSigV4Metadata.from(service).isEmpty()) {
      return;
    }

    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    String credentialsModule = ElixirSymbolProvider.toModuleName(layout.credentialsModuleName());

    ctx.writerDelegator()
        .useFileWriter(
            layout.credentialsModuleFile(),
            writer -> {
              writer.write("defmodule $L do", credentialsModule);
              writer.indent();
              writer.write("@moduledoc false");
              writer.write("");
              writer.write("@type client_config :: map()");
              writer.write("@type aws_credentials :: %{");
              writer.write("  required(:access_key_id) => String.t(),");
              writer.write("  required(:secret_access_key) => String.t(),");
              writer.write("  optional(:session_token) => String.t() | nil");
              writer.write("}");
              writer.write("");
              ElixirFormat.writeSpec(
                  writer,
                  "@spec",
                  "resolve",
                  "client_config()",
                  "{:ok, aws_credentials()} | {:error, term()}");
              writer.write("def resolve(config) do");
              writer.indent();
              writer.write("case Map.get(config, :credentials) do");
              writer.indent();
              writer.write("nil -> resolve_chain(config)");
              writer.write("");
              writer.write("creds -> {:ok, creds}");
              writer.dedent();
              writer.write("end");
              writer.dedent();
              writer.write("end");
              writer.write("");
              writeResolveChain(writer);
              writeResolveFromEnv(writer);
              writeResolveFromProfile(writer);
              writeResolveFromEcs(writer);
              writeResolveFromEc2(writer);
              writeSharedHelpers(writer);
              writer.dedent();
              writer.write("end");
            });
  }

  private static void writeResolveChain(ElixirWriter writer) {
    String providers =
        BeamCredentialProviders.defaultChain().stream()
            .map(kind -> ":" + elixirProviderAtom(kind))
            .collect(Collectors.joining(", "));
    writer.write("defp resolve_chain(config) do");
    writer.indent();
    writer.write("resolve_chain(config, [$L])", providers);
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp resolve_chain(_config, []), do: {:error, :not_found}");
    writer.write("defp resolve_chain(config, [provider | rest]) do");
    writer.indent();
    writer.write("case resolve_provider(provider, config) do");
    writer.indent();
    writer.write("{:ok, creds} -> {:ok, creds}");
    writer.write("");
    writer.write("_ -> resolve_chain(config, rest)");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
    for (BeamCredentialProviderKind kind : BeamCredentialProviders.defaultChain()) {
      writer.write(
          "defp resolve_provider(:$L, config), do: resolve_from_$L(config)",
          elixirProviderAtom(kind),
          elixirProviderSuffix(kind));
    }
    writer.write("");
  }

  private static void writeResolveFromEnv(ElixirWriter writer) {
    writer.write("defp resolve_from_env(_config) do");
    writer.indent();
    writer.write(
        "case {System.get_env(\"AWS_ACCESS_KEY_ID\"), System.get_env(\"AWS_SECRET_ACCESS_KEY\")} do");
    writer.indent();
    writer.write("{id, secret} when is_binary(id) and is_binary(secret) ->");
    writer.indent();
    writer.write("token = System.get_env(\"AWS_SESSION_TOKEN\")");
    writer.write("{:ok, %{");
    writer.write("  access_key_id: id,");
    writer.write("  secret_access_key: secret,");
    writer.write("  session_token: env_session_token(token)");
    writer.write("}}");
    writer.dedent();
    writer.write("_ ->");
    writer.indent();
    writer.write("{:error, :not_found}");
    writer.dedent();
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
  }

  private static void writeResolveFromProfile(ElixirWriter writer) {
    writer.write("defp resolve_from_profile(config) do");
    writer.indent();
    writer.write("profile = profile_name(config)");
    writer.write("path = profile_credentials_path(config)");
    writer.write("case File.read(path) do");
    writer.indent();
    writer.write("{:ok, contents} -> parse_profile_credentials(contents, profile)");
    writer.write("{:error, _} -> {:error, :not_found}");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp profile_name(config) do");
    writer.indent();
    writer.write("case Map.get(config, :profile) do");
    writer.indent();
    writer.write("nil -> System.get_env(\"AWS_PROFILE\") || \"default\"");
    writer.write("name -> name");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp profile_credentials_path(config) do");
    writer.indent();
    writer.write("case Map.get(config, :credentials_path) do");
    writer.indent();
    writer.write("nil ->");
    writer.indent();
    writer.write("System.get_env(\"AWS_SHARED_CREDENTIALS_FILE\") ||");
    writer.write("  Path.join(home_directory(), \".aws/credentials\")");
    writer.dedent();
    writer.write("path -> path");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
  }

  private static void writeResolveFromEcs(ElixirWriter writer) {
    writer.write("defp resolve_from_ecs(_config) do");
    writer.indent();
    writer.write("case System.get_env(\"AWS_CONTAINER_CREDENTIALS_RELATIVE_URI\") do");
    writer.indent();
    writer.write("nil ->");
    writer.indent();
    writer.write("case System.get_env(\"AWS_CONTAINER_CREDENTIALS_FULL_URI\") do");
    writer.indent();
    writer.write("nil -> {:error, :not_found}");
    writer.write("uri -> fetch_json_credentials(uri)");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("rel ->");
    writer.indent();
    writer.write("fetch_json_credentials(\"http://169.254.170.2\" <> rel)");
    writer.dedent();
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
  }

  private static void writeResolveFromEc2(ElixirWriter writer) {
    writer.write("defp resolve_from_ec2(_config) do");
    writer.indent();
    writer.write(
        "with {:ok, role_bin} <- ec2_metadata_request(\"/latest/meta-data/iam/security-credentials/\"),");
    writer.write("     role = String.trim(role_bin),");
    writer.write("     path = \"/latest/meta-data/iam/security-credentials/\" <> role,");
    writer.write("     {:ok, json_bin} <- ec2_metadata_request(path) do");
    writer.indent();
    writer.write("decode_json_credentials(json_bin)");
    writer.dedent();
    writer.write("else");
    writer.indent();
    writer.write("{:error, reason} -> {:error, reason}");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
  }

  private static void writeSharedHelpers(ElixirWriter writer) {
    writer.write("defp env_session_token(nil), do: nil");
    writer.write("defp env_session_token(token), do: token");
    writer.write("");
    writer.write("defp home_directory do");
    writer.indent();
    writer.write("System.get_env(\"HOME\") || System.get_env(\"USERPROFILE\") || \"\"");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp parse_profile_credentials(contents, profile) do");
    writer.indent();
    writer.write("contents");
    ElixirFormat.writePipelineStep(writer, "String.split(\"\\n\")");
    ElixirFormat.writePipelineStep(writer, "find_profile_section(profile, %{})");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp find_profile_section([], _profile, acc), do: map_to_credentials(acc)");
    writer.write("defp find_profile_section([line | rest], profile, acc) do");
    writer.indent();
    writer.write("trimmed = String.trim(line)");
    writer.write("cond do");
    writer.indent();
    writer.write("trimmed == \"[\" <> profile <> \"]\" ->");
    writer.indent();
    writer.write("read_profile_entries(rest, %{})");
    writer.dedent();
    writer.write("map_size(acc) > 0 ->");
    writer.indent();
    writer.write("map_to_credentials(acc)");
    writer.dedent();
    writer.write("true ->");
    writer.indent();
    writer.write("find_profile_section(rest, profile, acc)");
    writer.dedent();
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp read_profile_entries([], acc), do: map_to_credentials(acc)");
    writer.write("defp read_profile_entries([line | rest], acc) do");
    writer.indent();
    writer.write("trimmed = String.trim(line)");
    writer.write("cond do");
    writer.indent();
    writer.write("String.starts_with?(trimmed, \"[\") -> map_to_credentials(acc)");
    writer.write("trimmed == \"\" -> read_profile_entries(rest, acc)");
    writer.write("true ->");
    writer.indent();
    writer.write("case String.split(trimmed, \"=\", parts: 2) do");
    writer.indent();
    writer.write("[key, value] -> read_profile_entries(rest, Map.put(acc, key, value))");
    writer.write("_ -> read_profile_entries(rest, acc)");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write(
        "defp map_to_credentials(%{\"aws_access_key_id\" => id, \"aws_secret_access_key\" => secret} = fields) do");
    writer.indent();
    writer.write("{:ok, %{");
    writer.write("  access_key_id: String.trim(id),");
    writer.write("  secret_access_key: String.trim(secret),");
    writer.write("  session_token: optional_credential(Map.get(fields, \"aws_session_token\"))");
    writer.write("}}");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp map_to_credentials(_), do: {:error, :not_found}");
    writer.write("");
    writer.write("defp optional_credential(nil), do: nil");
    writer.write("defp optional_credential(value), do: String.trim(value)");
    writer.write("");
    writer.write("defp fetch_json_credentials(url) do");
    writer.indent();
    writer.write("case http_get(url, []) do");
    writer.indent();
    writer.write("{:ok, body} -> decode_json_credentials(body)");
    writer.write("");
    writer.write("{:error, reason} -> {:error, reason}");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp decode_json_credentials(body) do");
    writer.indent();
    writer.write("case Jason.decode(body) do");
    writer.indent();
    writer.write("{:ok, %{\"AccessKeyId\" => id, \"SecretAccessKey\" => secret} = doc} ->");
    writer.indent();
    writer.write("{:ok, %{");
    writer.write("  access_key_id: id,");
    writer.write("  secret_access_key: secret,");
    writer.write("  session_token: Map.get(doc, \"Token\")");
    writer.write("}}");
    writer.dedent();
    writer.write("");
    writer.write("_ ->");
    writer.indent();
    writer.write("{:error, :invalid_credentials}");
    writer.dedent();
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp ec2_metadata_request(path) do");
    writer.indent();
    writer.write("token_headers =");
    writer.indent();
    writer.write("case :httpc.request(");
    writer.write("       :put,");
    writer.write("       {~c\"http://169.254.169.254/latest/api/token\",");
    writer.write("        [{~c\"X-aws-ec2-metadata-token-ttl-seconds\", ~c\"60\"}]},");
    writer.write("       [],");
    writer.write("       ~c\"\"");
    writer.write("     ) do");
    writer.indent();
    writer.write("{:ok, {{_, 200, _}, resp_headers, _}} ->");
    writer.indent();
    writer.write("case :proplists.get_value(~c\"x-aws-ec2-metadata-token\", resp_headers) do");
    writer.indent();
    writer.write(":undefined -> []");
    writer.write("");
    writer.write("token -> [{~c\"X-aws-ec2-metadata-token\", token}]");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("");
    writer.write("_ ->");
    writer.indent();
    writer.write("[]");
    writer.dedent();
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("url = \"http://169.254.169.254\" <> path");
    writer.write("http_get(url, token_headers)");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp http_get(url, extra_headers) do");
    writer.indent();
    writer.write(
        "case :httpc.request(:get, {url, extra_headers}, [], [{:body_format, :binary}]) do");
    writer.indent();
    writer.write("{:ok, {{_, 200, _}, _resp_headers, body}} -> {:ok, body}");
    writer.write("");
    writer.write("{:ok, {{_, _, _}, _, _}} -> {:error, :http_error}");
    writer.write("");
    writer.write("{:error, reason} -> {:error, reason}");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
  }

  private static String elixirProviderAtom(BeamCredentialProviderKind kind) {
    return switch (kind) {
      case ENV -> "env";
      case PROFILE -> "profile";
      case ECS -> "ecs";
      case EC2 -> "ec2";
    };
  }

  private static String elixirProviderSuffix(BeamCredentialProviderKind kind) {
    return switch (kind) {
      case ENV -> "env";
      case PROFILE -> "profile";
      case ECS -> "ecs";
      case EC2 -> "ec2";
    };
  }
}
