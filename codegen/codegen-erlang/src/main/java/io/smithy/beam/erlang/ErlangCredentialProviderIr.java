package io.smithy.beam.erlang;

import io.beam.ir.erlang.AtomExpr;
import io.beam.ir.erlang.AtomPattern;
import io.beam.ir.erlang.BinaryExpr;
import io.beam.ir.erlang.BlockExpr;
import io.beam.ir.erlang.CaseExpr;
import io.beam.ir.erlang.Clause;
import io.beam.ir.erlang.Expression;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.FunctionClause;
import io.beam.ir.erlang.ListExpr;
import io.beam.ir.erlang.ListPattern;
import io.beam.ir.erlang.LocalCallExpr;
import io.beam.ir.erlang.MapEntry;
import io.beam.ir.erlang.MapExpr;
import io.beam.ir.erlang.MapPattern;
import io.beam.ir.erlang.MapPatternEntry;
import io.beam.ir.erlang.MatchExpr;
import io.beam.ir.erlang.MatchPattern;
import io.beam.ir.erlang.Module;
import io.beam.ir.erlang.OpaqueExpr;
import io.beam.ir.erlang.RemoteCallExpr;
import io.beam.ir.erlang.Spec;
import io.beam.ir.erlang.TupleExpr;
import io.beam.ir.erlang.TuplePattern;
import io.beam.ir.erlang.TypeAlias;
import io.beam.ir.erlang.Variable;
import io.beam.ir.erlang.VariablePattern;
import io.beam.ir.erlang.WildcardPattern;
import io.smithy.beam.core.BeamCredentialProviders;
import io.smithy.beam.core.BeamCredentialProviders.BeamCredentialProviderKind;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ErlangCredentialProviderIr {
  private static final String CLIENT_CONFIG = "client_config()";
  private static final String AWS_CREDENTIALS = "aws_credentials()";
  private static final String RESOLVE_RESULT = "{ok, " + AWS_CREDENTIALS + "} | {error, term()}";

  private ErlangCredentialProviderIr() {}

  static Module credentialsModule(String credentialsModule, ServiceShape service) {
    return Module.of(
        credentialsModule,
        credentialFunctions(),
        List.of("Generated AWS credential resolution for " + service.getId() + "."),
        null,
        null,
        List.of(
            TypeAlias.of("client_config", "#{binary() => term()}"),
            TypeAlias.of(
                "aws_credentials",
                "#{\n"
                    + "    access_key_id := binary(),\n"
                    + "    secret_access_key := binary(),\n"
                    + "    session_token => binary() | undefined\n"
                    + "}")),
        List.of("resolve/1"));
  }

  static Function resolve() {
    return Function.of(
        "resolve",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Config")),
                CaseExpr.of(
                    RemoteCallExpr.of(
                        "maps",
                        "get",
                        List.of(
                            AtomExpr.of("credentials"),
                            Variable.of("Config"),
                            AtomExpr.of("undefined"))),
                    List.of(
                        Clause.of(
                            AtomPattern.of("undefined"),
                            LocalCallExpr.of("resolve_chain", List.of(Variable.of("Config")))),
                        Clause.of(
                            VariablePattern.of("Creds"),
                            TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("Creds")))))))),
        Spec.of("resolve(" + CLIENT_CONFIG + ") -> " + RESOLVE_RESULT),
        null,
        null);
  }

  static List<Function> credentialFunctions() {
    List<Function> functions = new ArrayList<>();
    functions.add(resolve());
    functions.addAll(resolveChainFunctions());
    functions.addAll(envAndProfileFunctions());
    functions.addAll(ecsAndEc2Functions());
    return functions;
  }

  private static List<Function> resolveChainFunctions() {
    List<Function> functions = new ArrayList<>();
    functions.add(resolveChainArity1());
    functions.addAll(resolveChainArity2AndProvider());
    return functions;
  }

  private static Function resolveChainArity1() {
    List<Expression> providers =
        BeamCredentialProviders.defaultChain().stream()
            .map(kind -> AtomExpr.of(erlangProviderAtom(kind)))
            .collect(Collectors.toList());
    return Function.of(
        "resolve_chain",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Config")),
                LocalCallExpr.of(
                    "resolve_chain",
                    List.of(Variable.of("Config"), ListExpr.of(providers))))),
        Spec.of("resolve_chain(" + CLIENT_CONFIG + ") -> " + RESOLVE_RESULT),
        null,
        null);
  }

  private static List<Function> resolveChainArity2AndProvider() {
    List<Function> functions = new ArrayList<>();
    functions.add(
        Function.of(
            "resolve_chain",
            List.of(
                FunctionClause.of(
                    List.of(VariablePattern.of("_Config"), ListPattern.of(List.of())),
                    TupleExpr.of(List.of(AtomExpr.of("error"), AtomExpr.of("not_found")))),
                FunctionClause.of(
                    List.of(
                        VariablePattern.of("Config"),
                        ListPattern.cons(
                            VariablePattern.of("Provider"), VariablePattern.of("Rest"))),
                    CaseExpr.of(
                        LocalCallExpr.of(
                            "resolve_provider",
                            List.of(Variable.of("Provider"), Variable.of("Config"))),
                        List.of(
                            Clause.of(
                                TuplePattern.of(
                                    List.of(
                                        AtomPattern.of("ok"), VariablePattern.of("Creds"))),
                                TupleExpr.of(
                                    List.of(AtomExpr.of("ok"), Variable.of("Creds")))),
                            Clause.of(
                                WildcardPattern.of(),
                                LocalCallExpr.of(
                                    "resolve_chain",
                                    List.of(Variable.of("Config"), Variable.of("Rest")))))))),
            null,
            null,
            null));
    functions.add(resolveProvider());
    return functions;
  }

  private static Function resolveProvider() {
    List<BeamCredentialProviderKind> chain = BeamCredentialProviders.defaultChain();
    List<FunctionClause> clauses = new ArrayList<>();
    for (BeamCredentialProviderKind kind : chain) {
      clauses.add(
          FunctionClause.of(
              List.of(
                  AtomPattern.of(erlangProviderAtom(kind)), VariablePattern.of("Config")),
              LocalCallExpr.of(
                  "resolve_from_" + erlangProviderSuffix(kind), List.of(Variable.of("Config")))));
    }
    return Function.of(
        "resolve_provider",
        clauses,
        Spec.of("resolve_provider(atom(), " + CLIENT_CONFIG + ") -> " + RESOLVE_RESULT),
        null,
        null);
  }

  private static List<Function> envAndProfileFunctions() {
    return List.of(
        resolveFromEnv(),
        resolveFromProfile(),
        profileName(),
        profileCredentialsPath(),
        envSessionToken(),
        parseProfileCredentials(),
        findProfileSection(),
        readProfileEntries(),
        mapsToCredentials(),
        trimCredential(),
        optionalCredential());
  }

  private static Function resolveFromEnv() {
    return Function.of(
        "resolve_from_env",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("_Config")),
                OpaqueExpr.of(
                    """
                    case {os:getenv("AWS_ACCESS_KEY_ID"), os:getenv("AWS_SECRET_ACCESS_KEY")} of
                        {Id, Secret} when Id =/= false, Secret =/= false ->
                            Token = os:getenv("AWS_SESSION_TOKEN"),
                            {ok, #{access_key_id => list_to_binary(Id),
                                  secret_access_key => list_to_binary(Secret),
                                  session_token => env_session_token(Token)}};
                        _ ->
                            {error, not_found}
                    end"""
                        .strip()))),
        Spec.of("resolve_from_env(" + CLIENT_CONFIG + ") -> " + RESOLVE_RESULT),
        null,
        null);
  }

  private static Function resolveFromProfile() {
    return Function.of(
        "resolve_from_profile",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Config")),
                OpaqueExpr.of(
                    """
                    Profile = profile_name(Config),
                    Path = profile_credentials_path(Config),
                    case file:read_file(Path) of
                        {ok, Contents} ->
                            parse_profile_credentials(Contents, Profile);
                        {error, _} ->
                            {error, not_found}
                    end"""
                        .strip()))),
        Spec.of("resolve_from_profile(" + CLIENT_CONFIG + ") -> " + RESOLVE_RESULT),
        null,
        null);
  }

  private static Function profileName() {
    return Function.of(
        "profile_name",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Config")),
                OpaqueExpr.of(
                    """
                    case maps:get(profile, Config, undefined) of
                        undefined ->
                            case os:getenv("AWS_PROFILE") of
                                false -> <<\"default\">>;
                                Name -> list_to_binary(Name)
                            end;
                        Name -> Name
                    end"""
                        .strip()))),
        null,
        null,
        null);
  }

  private static Function profileCredentialsPath() {
    return Function.of(
        "profile_credentials_path",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Config")),
                OpaqueExpr.of(
                    """
                    case maps:get(credentials_path, Config, undefined) of
                        undefined ->
                            case os:getenv("AWS_SHARED_CREDENTIALS_FILE") of
                                false ->
                                    filename:join([os:getenv("HOME"), <<\".aws/credentials\">>]);
                                Path -> list_to_binary(Path)
                            end;
                        Path -> Path
                    end"""
                        .strip()))),
        null,
        null,
        null);
  }

  private static List<Function> ecsAndEc2Functions() {
    return List.of(
        resolveFromEcs(),
        resolveFromEc2(),
        fetchJsonCredentials(),
        decodeJsonCredentials(),
        ec2MetadataRequest(),
        httpGet());
  }

  private static Function resolveFromEcs() {
    return Function.of(
        "resolve_from_ecs",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("_Config")),
                OpaqueExpr.of(
                    """
                    case os:getenv("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI") of
                        false ->
                            case os:getenv("AWS_CONTAINER_CREDENTIALS_FULL_URI") of
                                false -> {error, not_found};
                                Uri -> fetch_json_credentials(list_to_binary(Uri))
                            end;
                        Rel ->
                            fetch_json_credentials(<<"http://169.254.170.2", Rel/binary>>)
                    end"""
                        .strip()))),
        Spec.of("resolve_from_ecs(" + CLIENT_CONFIG + ") -> " + RESOLVE_RESULT),
        null,
        null);
  }

  private static Function resolveFromEc2() {
    return Function.of(
        "resolve_from_ec2",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("_Config")),
                OpaqueExpr.of(
                    """
                    case ec2_metadata_request(<<"/latest/meta-data/iam/security-credentials/">>) of
                        {ok, RoleBin} ->
                            Role = string:trim(binary_to_list(RoleBin)),
                            Path = "/latest/meta-data/iam/security-credentials/" ++ Role,
                            case ec2_metadata_request(list_to_binary(Path)) of
                                {ok, JsonBin} -> decode_json_credentials(JsonBin);
                                {error, Reason} -> {error, Reason}
                            end;
                        {error, Reason} ->
                            {error, Reason}
                    end"""
                        .strip()))),
        Spec.of("resolve_from_ec2(" + CLIENT_CONFIG + ") -> " + RESOLVE_RESULT),
        null,
        null);
  }

  private static Function envSessionToken() {
    return Function.of(
        "env_session_token",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("false")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("Token")),
                LocalCallExpr.of("list_to_binary", List.of(Variable.of("Token"))))),
        null,
        null,
        null);
  }

  private static Function parseProfileCredentials() {
    return Function.of(
        "parse_profile_credentials",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Contents"), VariablePattern.of("Profile")),
                OpaqueExpr.of(
                    """
                    Lines = binary:split(Contents, <<\"\\n\">>, [global]),
                    case find_profile_section(Lines, Profile, #{}) of
                        {ok, Creds} -> {ok, Creds};
                        error -> {error, not_found}
                    end"""
                        .strip()))),
        null,
        null,
        null);
  }

  private static Function findProfileSection() {
    return Function.of(
        "find_profile_section",
        List.of(
            FunctionClause.of(
                List.of(
                    ListPattern.of(List.of()),
                    VariablePattern.of("_Profile"),
                    VariablePattern.of("Acc")),
                LocalCallExpr.of("maps_to_credentials", List.of(Variable.of("Acc")))),
            FunctionClause.of(
                List.of(
                    ListPattern.cons(VariablePattern.of("Line"), VariablePattern.of("Rest")),
                    VariablePattern.of("Profile"),
                    VariablePattern.of("Acc")),
                OpaqueExpr.of(
                    """
                    ExpectedHeader = "[" ++ binary_to_list(Profile) ++ "]",
                    Trimmed = string:trim(binary_to_list(Line)),
                    case Trimmed of
                        ExpectedHeader ->
                            read_profile_entries(Rest, Acc);
                        [$[ | _] ->
                            find_profile_section(Rest, Profile, #{});
                        _ ->
                            if map_size(Acc) > 0 ->
                                maps_to_credentials(Acc);
                            true ->
                                find_profile_section(Rest, Profile, Acc)
                            end
                    end"""
                        .strip()))),
        null,
        null,
        null);
  }

  private static Function readProfileEntries() {
    return Function.of(
        "read_profile_entries",
        List.of(
            FunctionClause.of(
                List.of(ListPattern.of(List.of()), VariablePattern.of("Acc")),
                LocalCallExpr.of("maps_to_credentials", List.of(Variable.of("Acc")))),
            FunctionClause.of(
                List.of(
                    ListPattern.cons(VariablePattern.of("Line"), VariablePattern.of("Rest")),
                    VariablePattern.of("Acc")),
                OpaqueExpr.of(
                    """
                    Trimmed = string:trim(binary_to_list(Line)),
                    case Trimmed of
                        [$[ | _] -> maps_to_credentials(Acc);
                        "" -> read_profile_entries(Rest, Acc);
                        Entry ->
                            case string:split(Entry, "=", leading) of
                                [Key, Value] ->
                                    read_profile_entries(Rest, Acc#{list_to_binary(Key) => list_to_binary(Value)});
                                _ -> read_profile_entries(Rest, Acc)
                            end
                    end"""
                        .strip()))),
        null,
        null,
        null);
  }

  private static Function mapsToCredentials() {
    Expression credentialsBody =
        BlockExpr.commaSeparated(
            List.of(
                MatchExpr.bindValue(
                    "Token",
                    RemoteCallExpr.of(
                        "maps",
                        "get",
                        List.of(
                            BinaryExpr.of("aws_session_token"),
                            Variable.of("Fields"),
                            AtomExpr.of("undefined")))),
                TupleExpr.of(
                    List.of(
                        AtomExpr.of("ok"),
                        MapExpr.of(
                            List.of(
                                MapEntry.of(
                                    AtomExpr.of("access_key_id"),
                                    LocalCallExpr.of(
                                        "trim_credential", List.of(Variable.of("Id")))),
                                MapEntry.of(
                                    AtomExpr.of("secret_access_key"),
                                    LocalCallExpr.of(
                                        "trim_credential", List.of(Variable.of("Secret")))),
                                MapEntry.of(
                                    AtomExpr.of("session_token"),
                                    LocalCallExpr.of(
                                        "optional_credential",
                                        List.of(Variable.of("Token"))))))))),
            false);
    return Function.of(
        "maps_to_credentials",
        List.of(
            FunctionClause.of(
                List.of(
                    MatchPattern.of(
                        MapPattern.of(
                            List.of(
                                MapPatternEntry.of(
                                    BinaryExpr.of("aws_access_key_id"), VariablePattern.of("Id"), true),
                                MapPatternEntry.of(
                                    BinaryExpr.of("aws_secret_access_key"),
                                    VariablePattern.of("Secret"),
                                    true))),
                        VariablePattern.of("Fields"))),
                credentialsBody),
            FunctionClause.of(
                List.of(WildcardPattern.of()),
                TupleExpr.of(List.of(AtomExpr.of("error"), AtomExpr.of("not_found"))))),
        null,
        null,
        null);
  }

  private static Function trimCredential() {
    return Function.of(
        "trim_credential",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Value")),
                LocalCallExpr.of(
                    "list_to_binary",
                    List.of(
                        RemoteCallExpr.of(
                            "string",
                            "trim",
                            List.of(
                                LocalCallExpr.of(
                                    "binary_to_list", List.of(Variable.of("Value"))))))))),
        null,
        null,
        null);
  }

  private static Function optionalCredential() {
    return Function.of(
        "optional_credential",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("Value")),
                LocalCallExpr.of("trim_credential", List.of(Variable.of("Value"))))),
        null,
        null,
        null);
  }

  private static Function fetchJsonCredentials() {
    return Function.of(
        "fetch_json_credentials",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Url")),
                CaseExpr.of(
                    LocalCallExpr.of("http_get", List.of(Variable.of("Url"), ListExpr.of(List.of()))),
                    List.of(
                        Clause.of(
                            TuplePattern.of(
                                List.of(AtomPattern.of("ok"), VariablePattern.of("Body"))),
                            LocalCallExpr.of(
                                "decode_json_credentials", List.of(Variable.of("Body")))),
                        Clause.of(
                            TuplePattern.of(
                                List.of(AtomPattern.of("error"), VariablePattern.of("Reason"))),
                            TupleExpr.of(
                                List.of(AtomExpr.of("error"), Variable.of("Reason")))))))),
        null,
        null,
        null);
  }

  private static Function decodeJsonCredentials() {
    return Function.of(
        "decode_json_credentials",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Body")),
                OpaqueExpr.of(
                    """
                    case jsone:try_decode(Body) of
                        {ok, #{<<"AccessKeyId">> := Id, <<"SecretAccessKey">> := Secret} = Doc, _} ->
                            Token = maps:get(<<"Token">>, Doc, undefined),
                            {ok, #{access_key_id => Id,
                                  secret_access_key => Secret,
                                  session_token => Token}};
                        _ ->
                            {error, invalid_credentials}
                    end"""
                        .strip()))),
        null,
        null,
        null);
  }

  private static Function ec2MetadataRequest() {
    return Function.of(
        "ec2_metadata_request",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Path")),
                OpaqueExpr.of(
                    """
                    TokenReq = {
                        "http://169.254.169.254/latest/api/token",
                        [{"X-aws-ec2-metadata-token-ttl-seconds", "60"}],
                        put,
                        <<>>
                    },
                    Headers =
                        case httpc:request(put, TokenReq, [{ssl, [{verify, verify_none}]}], []) of
                            {ok, {{_, 200, _}, RespHeaders, _}} ->
                                case proplists:get_value("x-aws-ec2-metadata-token", RespHeaders) of
                                    undefined -> [];
                                    Token -> [{"X-aws-ec2-metadata-token", Token}]
                                end;
                            _ ->
                                []
                        end,
                    Url = "http://169.254.169.254" ++ binary_to_list(Path),
                    http_get(Url, Headers)"""
                        .strip()))),
        null,
        null,
        null);
  }

  private static Function httpGet() {
    return Function.of(
        "http_get",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Url"), VariablePattern.of("ExtraHeaders")),
                OpaqueExpr.of(
                    """
                    Req = {Url, ExtraHeaders, get, <<>>},
                    case httpc:request(get, Req, [{ssl, [{verify, verify_none}]}], [{body_format, binary}]) of
                        {ok, {{_, 200, _}, _, Body}} -> {ok, Body};
                        {ok, {{_, _, _}, _, _}} -> {error, http_error};
                        {error, Reason} -> {error, Reason}
                    end"""
                        .strip()))),
        null,
        null,
        null);
  }

  private static String erlangProviderAtom(BeamCredentialProviderKind kind) {
    return switch (kind) {
      case ENV -> "env";
      case PROFILE -> "profile";
      case ECS -> "ecs";
      case EC2 -> "ec2";
    };
  }

  private static String erlangProviderSuffix(BeamCredentialProviderKind kind) {
    return erlangProviderAtom(kind);
  }
}
