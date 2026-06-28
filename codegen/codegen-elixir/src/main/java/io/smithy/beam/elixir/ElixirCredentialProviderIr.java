package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamCredentialProviders;
import io.smithy.beam.core.BeamCredentialProviders.BeamCredentialProviderKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExBlankLine;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExCapturedBlock;
import io.smithy.beam.ir.elixir.ExCase;
import io.smithy.beam.ir.elixir.ExCaseBranch;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExConsPattern;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExList;
import io.smithy.beam.ir.elixir.ExListPattern;
import io.smithy.beam.ir.elixir.ExMap;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExModuleEntry;
import io.smithy.beam.ir.elixir.ExNil;
import io.smithy.beam.ir.elixir.ExNilPattern;
import io.smithy.beam.ir.elixir.ExPipeline;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExTypeDef;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ElixirCredentialProviderIr {
  private static final String CLIENT_CONFIG = "client_config()";
  private static final String RESOLVE_RESULT = "{:ok, aws_credentials()} | {:error, term()}";

  private ElixirCredentialProviderIr() {}

  static ExModule credentialsModule(ElixirContext ctx, ServiceShape service) {
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    String moduleName = ElixirSymbolProvider.toModuleName(layout.credentialsModuleName());

    List<ExModuleEntry> entries = new ArrayList<>();
    entries.add(ExTypeDef.alias("client_config", "map()"));
    entries.add(new ExBlankLine());
    entries.add(
        ExTypeDef.structureType(
            "aws_credentials",
            List.of(
                "required(:access_key_id) => String.t()",
                "required(:secret_access_key) => String.t()",
                "optional(:session_token) => String.t() | nil")));

    return ExModule.module(
        moduleName,
        List.of(ExModuledoc.moduledoc("false")),
        List.of(),
        credentialFunctions(),
        entries);
  }

  static ExFunction resolve() {
    return ExFunction.functionWithSpec(
        "def",
        "resolve",
        ExSpec.functionSpec("resolve", CLIENT_CONFIG, RESOLVE_RESULT),
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("config")),
                ExCase.caseExpr(
                    ExCall.call(
                        "Map", "get", ExVar.var("config"), ExAtom.atom("credentials")),
                    List.of(
                        ExCaseBranch.branch(
                            ExNilPattern.nil(),
                            ExCallLocal.callLocal("resolve_chain", ExVar.var("config"))),
                        ExCaseBranch.branch(
                            ExVarPattern.var("creds"),
                            ExTuple.tuple(ExAtom.atom("ok"), ExVar.var("creds")))),
                    true))));
  }

  static List<ExFunction> credentialFunctions() {
    List<ExFunction> functions = new ArrayList<>();
    functions.add(resolve());
    functions.addAll(resolveChainFunctions());
    functions.addAll(envAndProfileFunctions());
    functions.addAll(ecsAndEc2Functions());
    functions.addAll(sharedHelperFunctions());
    return functions;
  }

  private static List<ExFunction> resolveChainFunctions() {
    List<ExFunction> functions = new ArrayList<>();
    functions.add(resolveChainArity1());
    functions.add(resolveChainEmpty());
    functions.add(resolveChainCons());
    functions.add(resolveProvider());
    return functions;
  }

  private static ExFunction resolveChainArity1() {
    List<io.smithy.beam.ir.elixir.ExExpr> providers =
        BeamCredentialProviders.defaultChain().stream()
            .map(kind -> ExAtom.atom(elixirProviderAtom(kind)))
            .collect(Collectors.toList());
    return ExFunction.functionWithSpec(
        "defp",
        "resolve_chain",
        ExSpec.functionSpec("resolve_chain", CLIENT_CONFIG, RESOLVE_RESULT),
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("config")),
                ExCallLocal.callLocal(
                    "resolve_chain",
                    ExVar.var("config"),
                    ExList.list(providers.toArray(io.smithy.beam.ir.elixir.ExExpr[]::new))))));
  }

  private static ExFunction resolveChainEmpty() {
    return ExFunction.defpFunction(
        "resolve_chain",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("_config"), ExListPattern.list()),
                ExTuple.tuple(ExAtom.atom("error"), ExAtom.atom("not_found")))));
  }

  private static ExFunction resolveChainCons() {
    return ExFunction.defpFunction(
        "resolve_chain",
        List.of(
            ExClause.blockClause(
                List.of(
                    ExVarPattern.var("config"),
                    ExConsPattern.consPattern(
                        ExVarPattern.var("provider"), ExVarPattern.var("rest"))),
                ExCase.caseExpr(
                    ExCallLocal.callLocal(
                        "resolve_provider", ExVar.var("provider"), ExVar.var("config")),
                    List.of(
                        ExCaseBranch.branch(
                            ExTuplePattern.tuple(
                                ExAtomPattern.atom("ok"), ExVarPattern.var("creds")),
                            ExTuple.tuple(ExAtom.atom("ok"), ExVar.var("creds"))),
                        ExCaseBranch.branch(
                            ExVarPattern.var("_"),
                            ExCallLocal.callLocal(
                                "resolve_chain", ExVar.var("config"), ExVar.var("rest")))),
                    true))));
  }

  private static ExFunction resolveProvider() {
    List<ExClause> clauses = new ArrayList<>();
    for (BeamCredentialProviderKind kind : BeamCredentialProviders.defaultChain()) {
      clauses.add(
          ExClause.inlineClause(
              List.of(
                  ExAtomPattern.atom(elixirProviderAtom(kind)),
                  ExVarPattern.var("config")),
              ExCallLocal.callLocal(
                  "resolve_from_" + elixirProviderSuffix(kind), ExVar.var("config"))));
    }
    return ExFunction.defpFunction("resolve_provider", clauses);
  }

  private static List<ExFunction> envAndProfileFunctions() {
    return List.of(
        resolveFromEnv(),
        resolveFromProfile(),
        profileName(),
        profileCredentialsPath(),
        parseProfileCredentials(),
        findProfileSection(),
        readProfileEntries(),
        mapToCredentials(),
        optionalCredential());
  }

  private static ExFunction resolveFromEnv() {
    return ExFunction.functionWithSpec(
        "defp",
        "resolve_from_env",
        ExSpec.functionSpec("resolve_from_env", CLIENT_CONFIG, RESOLVE_RESULT),
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("_config")),
                ExCapturedBlock.capturedBlock(
                    """
                    case {System.get_env("AWS_ACCESS_KEY_ID"), System.get_env("AWS_SECRET_ACCESS_KEY")} do
                      {id, secret} when is_binary(id) and is_binary(secret) ->
                        token = System.get_env("AWS_SESSION_TOKEN")
                        {:ok, %{
                          access_key_id: id,
                          secret_access_key: secret,
                          session_token: env_session_token(token)
                        }}

                      _ ->
                        {:error, :not_found}
                    end"""))));
  }

  private static ExFunction resolveFromProfile() {
    return ExFunction.functionWithSpec(
        "defp",
        "resolve_from_profile",
        ExSpec.functionSpec("resolve_from_profile", CLIENT_CONFIG, RESOLVE_RESULT),
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("config")),
                ExCapturedBlock.capturedBlock(
                    """
                    profile = profile_name(config)
                    path = profile_credentials_path(config)
                    case File.read(path) do
                      {:ok, contents} -> parse_profile_credentials(contents, profile)
                      {:error, _} -> {:error, :not_found}
                    end"""))));
  }

  private static ExFunction profileName() {
    return ExFunction.defpFunction(
        "profile_name",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("config")),
                ExCapturedBlock.capturedBlock(
                    """
                    case Map.get(config, :profile) do
                      nil -> System.get_env("AWS_PROFILE") || "default"
                      name -> name
                    end"""))));
  }

  private static ExFunction profileCredentialsPath() {
    return ExFunction.defpFunction(
        "profile_credentials_path",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("config")),
                ExCapturedBlock.capturedBlock(
                    """
                    case Map.get(config, :credentials_path) do
                      nil ->
                        System.get_env("AWS_SHARED_CREDENTIALS_FILE") ||
                          Path.join(home_directory(), ".aws/credentials")

                      path -> path
                    end"""))));
  }

  private static ExFunction parseProfileCredentials() {
    return ExFunction.defpFunction(
        "parse_profile_credentials",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("contents"), ExVarPattern.var("profile")),
                ExPipeline.pipeChain(
                    ExVar.var("contents"),
                    ExCapturedBlock.capturedBlock("String.split(\"\\n\")"),
                    ExCallLocal.callLocal(
                        "find_profile_section", ExVar.var("profile"), ExMap.map())))));
  }

  private static ExFunction findProfileSection() {
    return ExFunction.defpFunction(
        "find_profile_section",
        List.of(
            ExClause.inlineClause(
                List.of(
                    ExListPattern.list(),
                    ExVarPattern.var("_profile"),
                    ExVarPattern.var("acc")),
                ExCallLocal.callLocal("map_to_credentials", ExVar.var("acc"))),
            ExClause.blockClause(
                List.of(
                    ExConsPattern.consPattern(
                        ExVarPattern.var("line"), ExVarPattern.var("rest")),
                    ExVarPattern.var("profile"),
                    ExVarPattern.var("acc")),
                ExCapturedBlock.capturedBlock(
                    """
                    trimmed = String.trim(line)
                    cond do
                      trimmed == "[" <> profile <> "]" ->
                        read_profile_entries(rest, %{})

                      map_size(acc) > 0 ->
                        map_to_credentials(acc)

                      true ->
                        find_profile_section(rest, profile, acc)
                    end"""))));
  }

  private static ExFunction readProfileEntries() {
    return ExFunction.defpFunction(
        "read_profile_entries",
        List.of(
            ExClause.inlineClause(
                List.of(ExListPattern.list(), ExVarPattern.var("acc")),
                ExCallLocal.callLocal("map_to_credentials", ExVar.var("acc"))),
            ExClause.blockClause(
                List.of(
                    ExConsPattern.consPattern(
                        ExVarPattern.var("line"), ExVarPattern.var("rest")),
                    ExVarPattern.var("acc")),
                ExCapturedBlock.capturedBlock(
                    """
                    trimmed = String.trim(line)
                    cond do
                      String.starts_with?(trimmed, "[") -> map_to_credentials(acc)
                      trimmed == "" -> read_profile_entries(rest, acc)
                      true ->
                        case String.split(trimmed, "=", parts: 2) do
                          [key, value] -> read_profile_entries(rest, Map.put(acc, key, value))
                          _ -> read_profile_entries(rest, acc)
                        end
                    end"""))));
  }

  private static ExFunction mapToCredentials() {
    return ExFunction.defpFunction(
        "map_to_credentials",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("fields")),
                ExCapturedBlock.capturedBlock(
                    """
                    case fields do
                      %{"aws_access_key_id" => id, "aws_secret_access_key" => secret} = fields ->
                        {:ok, %{
                          access_key_id: String.trim(id),
                          secret_access_key: String.trim(secret),
                          session_token: optional_credential(Map.get(fields, "aws_session_token"))
                        }}

                      _ ->
                        {:error, :not_found}
                    end"""))));
  }

  private static ExFunction optionalCredential() {
    return ExFunction.defpFunction(
        "optional_credential",
        List.of(
            ExClause.inlineClause(List.of(ExNilPattern.nil()), ExNil.nil()),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("value")),
                ExCall.call("String", "trim", ExVar.var("value")))));
  }

  private static List<ExFunction> ecsAndEc2Functions() {
    return List.of(
        resolveFromEcs(),
        resolveFromEc2(),
        fetchJsonCredentials(),
        decodeJsonCredentials(),
        ec2MetadataRequest(),
        httpGet());
  }

  private static ExFunction resolveFromEcs() {
    return ExFunction.functionWithSpec(
        "defp",
        "resolve_from_ecs",
        ExSpec.functionSpec("resolve_from_ecs", CLIENT_CONFIG, RESOLVE_RESULT),
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("_config")),
                ExCapturedBlock.capturedBlock(
                    """
                    case System.get_env("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI") do
                      nil ->
                        case System.get_env("AWS_CONTAINER_CREDENTIALS_FULL_URI") do
                          nil -> {:error, :not_found}
                          uri -> fetch_json_credentials(uri)
                        end

                      rel ->
                        fetch_json_credentials("http://169.254.170.2" <> rel)
                    end"""))));
  }

  private static ExFunction resolveFromEc2() {
    return ExFunction.functionWithSpec(
        "defp",
        "resolve_from_ec2",
        ExSpec.functionSpec("resolve_from_ec2", CLIENT_CONFIG, RESOLVE_RESULT),
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("_config")),
                ExCapturedBlock.capturedBlock(
                    """
                    with {:ok, role_bin} <- ec2_metadata_request("/latest/meta-data/iam/security-credentials/"),
                         role = String.trim(role_bin),
                         path = "/latest/meta-data/iam/security-credentials/" <> role,
                         {:ok, json_bin} <- ec2_metadata_request(path) do
                      decode_json_credentials(json_bin)
                    else
                      {:error, reason} -> {:error, reason}
                    end"""))));
  }

  private static ExFunction fetchJsonCredentials() {
    return ExFunction.defpFunction(
        "fetch_json_credentials",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("url")),
                ExCase.caseExpr(
                    ExCallLocal.callLocal("http_get", ExVar.var("url"), ExList.list()),
                    List.of(
                        ExCaseBranch.branch(
                            ExTuplePattern.tuple(
                                ExAtomPattern.atom("ok"), ExVarPattern.var("body")),
                            ExCallLocal.callLocal(
                                "decode_json_credentials", ExVar.var("body"))),
                        ExCaseBranch.branch(
                            ExTuplePattern.tuple(
                                ExAtomPattern.atom("error"), ExVarPattern.var("reason")),
                            ExTuple.tuple(ExAtom.atom("error"), ExVar.var("reason")))),
                    true))));
  }

  private static ExFunction decodeJsonCredentials() {
    return ExFunction.defpFunction(
        "decode_json_credentials",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("body")),
                ExCapturedBlock.capturedBlock(
                    """
                    case Jason.decode(body) do
                      {:ok, %{"AccessKeyId" => id, "SecretAccessKey" => secret} = doc} ->
                        {:ok, %{
                          access_key_id: id,
                          secret_access_key: secret,
                          session_token: Map.get(doc, "Token")
                        }}

                      _ ->
                        {:error, :invalid_credentials}
                    end"""))));
  }

  private static ExFunction ec2MetadataRequest() {
    return ExFunction.defpFunction(
        "ec2_metadata_request",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("path")),
                ExCapturedBlock.capturedBlock(
                    """
                    token_headers =
                      case :httpc.request(
                             :put,
                             {~c"http://169.254.169.254/latest/api/token",
                              [{~c"X-aws-ec2-metadata-token-ttl-seconds", ~c"60"}]},
                             [],
                             ~c""
                           ) do
                        {:ok, {{_, 200, _}, resp_headers, _}} ->
                          case :proplists.get_value(~c"x-aws-ec2-metadata-token", resp_headers) do
                            :undefined -> []

                            token -> [{~c"X-aws-ec2-metadata-token", token}]
                          end

                        _ ->
                          []
                      end

                    url = "http://169.254.169.254" <> path
                    http_get(url, token_headers)"""))));
  }

  private static ExFunction httpGet() {
    return ExFunction.defpFunction(
        "http_get",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("url"), ExVarPattern.var("extra_headers")),
                ExCapturedBlock.capturedBlock(
                    """
                    case :httpc.request(:get, {url, extra_headers}, [], [{:body_format, :binary}]) do
                      {:ok, {{_, 200, _}, _resp_headers, body}} -> {:ok, body}

                      {:ok, {{_, _, _}, _, _}} -> {:error, :http_error}

                      {:error, reason} -> {:error, reason}
                    end"""))));
  }

  private static List<ExFunction> sharedHelperFunctions() {
    return List.of(
        envSessionToken(),
        homeDirectory());
  }

  private static ExFunction envSessionToken() {
    return ExFunction.defpFunction(
        "env_session_token",
        List.of(
            ExClause.inlineClause(List.of(ExNilPattern.nil()), ExNil.nil()),
            ExClause.inlineClause(List.of(ExVarPattern.var("token")), ExVar.var("token"))));
  }

  private static ExFunction homeDirectory() {
    return ExFunction.defpFunction(
        "home_directory",
        List.of(
            ExClause.blockClause(
                List.of(),
                ExCapturedBlock.capturedBlock(
                    "System.get_env(\"HOME\") || System.get_env(\"USERPROFILE\") || \"\""))));
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
    return elixirProviderAtom(kind);
  }
}
