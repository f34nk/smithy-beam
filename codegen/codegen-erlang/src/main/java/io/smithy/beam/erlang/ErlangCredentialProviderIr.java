package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamCredentialProviders;
import io.smithy.beam.core.BeamCredentialProviders.BeamCredentialProviderKind;
import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAtomPattern;
import io.smithy.beam.ir.erlang.ErlBinary;
import io.smithy.beam.ir.erlang.ErlCall;
import io.smithy.beam.ir.erlang.ErlCallLocal;
import io.smithy.beam.ir.erlang.ErlCapturedBlock;
import io.smithy.beam.ir.erlang.ErlCase;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlComment;
import io.smithy.beam.ir.erlang.ErlConsPattern;
import io.smithy.beam.ir.erlang.ErlExpr;
import io.smithy.beam.ir.erlang.ErlNilPattern;
import io.smithy.beam.ir.erlang.ErlExportAttribute;
import io.smithy.beam.ir.erlang.ErlExprBlock;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlGuard;
import io.smithy.beam.ir.erlang.ErlList;
import io.smithy.beam.ir.erlang.ErlMap;
import io.smithy.beam.ir.erlang.ErlMapEntry;
import io.smithy.beam.ir.erlang.ErlMatch;
import io.smithy.beam.ir.erlang.ErlMatchPattern;
import io.smithy.beam.ir.erlang.ErlModule;
import io.smithy.beam.ir.erlang.ErlBinaryExpr;
import io.smithy.beam.ir.erlang.ErlBinaryTemplate;
import io.smithy.beam.ir.erlang.ErlBinaryText;
import io.smithy.beam.ir.erlang.ErlMapFieldPattern;
import io.smithy.beam.ir.erlang.ErlMapPattern;
import io.smithy.beam.ir.erlang.ErlOp;
import io.smithy.beam.ir.erlang.ErlRemoteCall;
import io.smithy.beam.ir.erlang.ErlTuple;
import io.smithy.beam.ir.erlang.ErlTuplePattern;
import io.smithy.beam.ir.erlang.ErlTypeDef;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

final class ErlangCredentialProviderIr {
    private static final String CLIENT_CONFIG = "client_config()";
    private static final String AWS_CREDENTIALS = "aws_credentials()";
    private static final String RESOLVE_RESULT = "{ok, " + AWS_CREDENTIALS + "} | {error, term()}";

    private ErlangCredentialProviderIr() {}

    static ErlModule credentialsModule(String credentialsModule, ServiceShape service) {
        return new ErlModule(
                credentialsModule,
                List.of(ErlComment.comment("Generated AWS credential resolution for " + service.getId() + ".")),
                List.of(
                        ErlExportAttribute.export(List.of("resolve/1")),
                        clientConfigType(),
                        awsCredentialsType()),
                credentialFunctions());
    }

    static ErlFunction resolve() {
        return ErlFunction.functionWithSpec(
                "resolve",
                1,
                CLIENT_CONFIG,
                RESOLVE_RESULT,
                List.of(ErlClause.blockClause(
                        List.of(ErlVarPattern.varPattern("Config")),
                        ErlCase.caseExpr(
                                ErlCall.call(
                                        "maps",
                                        "get",
                                        ErlAtom.atom("credentials"),
                                        ErlVar.var("Config"),
                                        ErlAtom.atom("undefined")),
                                ErlClause.clause(
                                        List.of(ErlAtomPattern.atomPattern("undefined")),
                                        ErlCallLocal.callLocal("resolve_chain", ErlVar.var("Config"))),
                                ErlClause.clause(
                                        List.of(ErlVarPattern.varPattern("Creds")),
                                        ErlTuple.tuple(ErlAtom.atom("ok"), ErlVar.var("Creds")))))));
    }

    static List<ErlFunction> credentialFunctions() {
        List<ErlFunction> functions = new ArrayList<>();
        functions.add(resolve());
        functions.addAll(resolveChainFunctions());
        functions.addAll(envAndProfileFunctions());
        functions.addAll(ecsAndEc2Functions());
        return functions;
    }

    private static ErlTypeDef clientConfigType() {
        return new ErlTypeDef("client_config", "#{binary() => term()}");
    }

    private static ErlTypeDef awsCredentialsType() {
        return new ErlTypeDef(
                "aws_credentials",
                "#{\n"
                        + "    access_key_id := binary(),\n"
                        + "    secret_access_key := binary(),\n"
                        + "    session_token => binary() | undefined\n"
                        + "}");
    }

    private static List<ErlFunction> resolveChainFunctions() {
        List<ErlFunction> functions = new ArrayList<>();
        functions.add(resolveChainArity1());
        functions.addAll(resolveChainArity2AndProvider());
        return functions;
    }

    private static ErlFunction resolveChainArity1() {
        List<ErlExpr> providers = BeamCredentialProviders.defaultChain().stream()
                .map(kind -> ErlAtom.atom(erlangProviderAtom(kind)))
                .collect(Collectors.toList());
        return ErlFunction.functionWithSpec(
                "resolve_chain",
                1,
                CLIENT_CONFIG,
                RESOLVE_RESULT,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Config")),
                        ErlCallLocal.callLocal(
                                "resolve_chain",
                                ErlVar.var("Config"),
                                ErlList.list(providers.toArray(ErlExpr[]::new))))));
    }

    private static List<ErlFunction> resolveChainArity2AndProvider() {
        List<ErlFunction> functions = new ArrayList<>();
        functions.add(ErlFunction.function(
                "resolve_chain",
                2,
                List.of(
                        ErlClause.clause(
                                List.of(
                                        ErlVarPattern.varPattern("_Config"),
                                        ErlNilPattern.nilPattern()),
                                ErlTuple.tuple(ErlAtom.atom("error"), ErlAtom.atom("not_found"))),
                        ErlClause.blockClause(
                                List.of(
                                        ErlVarPattern.varPattern("Config"),
                                        ErlConsPattern.consPattern(
                                                ErlVarPattern.varPattern("Provider"),
                                                ErlVarPattern.varPattern("Rest"))),
                                ErlCase.caseExpr(
                                        ErlCallLocal.callLocal(
                                                "resolve_provider",
                                                ErlVar.var("Provider"),
                                                ErlVar.var("Config")),
                                        ErlClause.clause(
                                                List.of(ErlTuplePattern.tuplePattern(
                                                        ErlAtomPattern.atomPattern("ok"),
                                                        ErlVarPattern.varPattern("Creds"))),
                                                ErlTuple.tuple(ErlAtom.atom("ok"), ErlVar.var("Creds"))),
                                        ErlClause.clause(
                                                List.of(ErlVarPattern.varPattern("_")),
                                                ErlCallLocal.callLocal(
                                                        "resolve_chain",
                                                        ErlVar.var("Config"),
                                                        ErlVar.var("Rest"))))))));
        functions.add(resolveProvider());
        return functions;
    }

    private static ErlFunction resolveProvider() {
        List<BeamCredentialProviderKind> chain = BeamCredentialProviders.defaultChain();
        List<ErlClause> clauses = new ArrayList<>();
        for (BeamCredentialProviderKind kind : chain) {
            clauses.add(ErlClause.clause(
                    List.of(
                            ErlAtomPattern.atomPattern(erlangProviderAtom(kind)),
                            ErlVarPattern.varPattern("Config")),
                    ErlCallLocal.callLocal(
                            "resolve_from_" + erlangProviderSuffix(kind),
                            ErlVar.var("Config"))));
        }
        return ErlFunction.functionWithSpec(
                "resolve_provider",
                2,
                "atom(), " + CLIENT_CONFIG,
                RESOLVE_RESULT,
                clauses);
    }

    private static List<ErlFunction> envAndProfileFunctions() {
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

    private static ErlFunction resolveFromEnv() {
        return ErlFunction.functionWithSpec(
                "resolve_from_env",
                1,
                CLIENT_CONFIG,
                RESOLVE_RESULT,
                List.of(ErlClause.blockClause(
                        List.of(ErlVarPattern.varPattern("_Config")),
                        ErlCapturedBlock.capturedBlock(
                                """
                                case {os:getenv("AWS_ACCESS_KEY_ID"), os:getenv("AWS_SECRET_ACCESS_KEY")} of
                                    {Id, Secret} when Id =/= false, Secret =/= false ->
                                        Token = os:getenv("AWS_SESSION_TOKEN"),
                                        {ok, #{access_key_id => list_to_binary(Id),
                                              secret_access_key => list_to_binary(Secret),
                                              session_token => env_session_token(Token)}};
                                    _ ->
                                        {error, not_found}
                                end"""))));
    }

    private static ErlFunction resolveFromProfile() {
        return ErlFunction.functionWithSpec(
                "resolve_from_profile",
                1,
                CLIENT_CONFIG,
                RESOLVE_RESULT,
                List.of(ErlClause.blockClause(
                        List.of(ErlVarPattern.varPattern("Config")),
                        ErlCapturedBlock.capturedBlock(
                                """
                                Profile = profile_name(Config),
                                Path = profile_credentials_path(Config),
                                case file:read_file(Path) of
                                    {ok, Contents} ->
                                        parse_profile_credentials(Contents, Profile);
                                    {error, _} ->
                                        {error, not_found}
                                end"""))));
    }

    private static ErlFunction profileName() {
        return ErlFunction.function(
                "profile_name",
                1,
                List.of(ErlClause.blockClause(
                        List.of(ErlVarPattern.varPattern("Config")),
                        ErlCapturedBlock.capturedBlock(
                                """
                                case maps:get(profile, Config, undefined) of
                                    undefined ->
                                        case os:getenv("AWS_PROFILE") of
                                            false -> <<\"default\">>;
                                            Name -> list_to_binary(Name)
                                        end;
                                    Name -> Name
                                end"""))));
    }

    private static ErlFunction profileCredentialsPath() {
        return ErlFunction.function(
                "profile_credentials_path",
                1,
                List.of(ErlClause.blockClause(
                        List.of(ErlVarPattern.varPattern("Config")),
                        ErlCapturedBlock.capturedBlock(
                                """
                                case maps:get(credentials_path, Config, undefined) of
                                    undefined ->
                                        case os:getenv("AWS_SHARED_CREDENTIALS_FILE") of
                                            false ->
                                                filename:join([os:getenv("HOME"), <<\".aws/credentials\">>]);
                                            Path -> list_to_binary(Path)
                                        end;
                                    Path -> Path
                                end"""))));
    }

    private static List<ErlFunction> ecsAndEc2Functions() {
        return List.of(
                resolveFromEcs(),
                resolveFromEc2(),
                fetchJsonCredentials(),
                decodeJsonCredentials(),
                ec2MetadataRequest(),
                httpGet());
    }

    private static ErlFunction resolveFromEcs() {
        return ErlFunction.functionWithSpec(
                "resolve_from_ecs",
                1,
                CLIENT_CONFIG,
                RESOLVE_RESULT,
                List.of(ErlClause.blockClause(
                        List.of(ErlVarPattern.varPattern("_Config")),
                        ErlCapturedBlock.capturedBlock(
                                """
                                case os:getenv("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI") of
                                    false ->
                                        case os:getenv("AWS_CONTAINER_CREDENTIALS_FULL_URI") of
                                            false -> {error, not_found};
                                            Uri -> fetch_json_credentials(list_to_binary(Uri))
                                        end;
                                    Rel ->
                                        fetch_json_credentials(<<"http://169.254.170.2", Rel/binary>>)
                                end"""))));
    }

    private static ErlFunction resolveFromEc2() {
        return ErlFunction.functionWithSpec(
                "resolve_from_ec2",
                1,
                CLIENT_CONFIG,
                RESOLVE_RESULT,
                List.of(ErlClause.blockClause(
                        List.of(ErlVarPattern.varPattern("_Config")),
                        ErlCapturedBlock.capturedBlock(
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
                                end"""))));
    }

    private static ErlFunction envSessionToken() {
        return ErlFunction.function(
                "env_session_token",
                1,
                List.of(
                        ErlClause.clause(
                                List.of(ErlAtomPattern.atomPattern("false")),
                                ErlAtom.atom("undefined")),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("Token")),
                                ErlCallLocal.callLocal("list_to_binary", ErlVar.var("Token")))));
    }

    private static ErlFunction parseProfileCredentials() {
        return ErlFunction.function(
                "parse_profile_credentials",
                2,
                List.of(ErlClause.blockClause(
                        List.of(
                                ErlVarPattern.varPattern("Contents"),
                                ErlVarPattern.varPattern("Profile")),
                        ErlCapturedBlock.capturedBlock(
                                """
                                Lines = binary:split(Contents, <<\"\\n\">>, [global]),
                                case find_profile_section(Lines, Profile, #{}) of
                                    {ok, Creds} -> {ok, Creds};
                                    error -> {error, not_found}
                                end"""))));
    }

    private static ErlFunction findProfileSection() {
        return ErlFunction.function(
                "find_profile_section",
                3,
                List.of(
                        ErlClause.clause(
                                List.of(
                                        ErlNilPattern.nilPattern(),
                                        ErlVarPattern.varPattern("_Profile"),
                                        ErlVarPattern.varPattern("Acc")),
                                ErlCallLocal.callLocal("maps_to_credentials", ErlVar.var("Acc"))),
                        ErlClause.blockClause(
                                List.of(
                                        ErlConsPattern.consPattern(
                                                ErlVarPattern.varPattern("Line"),
                                                ErlVarPattern.varPattern("Rest")),
                                        ErlVarPattern.varPattern("Profile"),
                                        ErlVarPattern.varPattern("Acc")),
                                ErlCapturedBlock.capturedBlock(
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
                                        end"""))));
    }

    private static ErlFunction readProfileEntries() {
        return ErlFunction.function(
                "read_profile_entries",
                2,
                List.of(
                        ErlClause.clause(
                                List.of(ErlNilPattern.nilPattern(), ErlVarPattern.varPattern("Acc")),
                                ErlCallLocal.callLocal("maps_to_credentials", ErlVar.var("Acc"))),
                        ErlClause.blockClause(
                                List.of(
                                        ErlConsPattern.consPattern(
                                                ErlVarPattern.varPattern("Line"),
                                                ErlVarPattern.varPattern("Rest")),
                                        ErlVarPattern.varPattern("Acc")),
                                ErlCapturedBlock.capturedBlock(
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
                                        end"""))));
    }

    private static ErlFunction mapsToCredentials() {
        return ErlFunction.function(
                "maps_to_credentials",
                1,
                List.of(
                        ErlClause.blockClause(
                                List.of(ErlMatchPattern.matchPattern(
                                        ErlMapPattern.mapPattern(
                                                ErlMapFieldPattern.fieldPattern(
                                                        "<<\"aws_access_key_id\">>",
                                                        ErlVarPattern.varPattern("Id")),
                                                ErlMapFieldPattern.fieldPattern(
                                                        "<<\"aws_secret_access_key\">>",
                                                        ErlVarPattern.varPattern("Secret"))),
                                        ErlVarPattern.varPattern("Fields"))),
                                ErlExprBlock.block(
                                        ErlMatch.match(
                                                ErlVarPattern.varPattern("Token"),
                                                ErlCall.call(
                                                        "maps",
                                                        "get",
                                                        ErlBinary.binary("aws_session_token"),
                                                        ErlVar.var("Fields"),
                                                        ErlAtom.atom("undefined"))),
                                        ErlTuple.tuple(
                                                ErlAtom.atom("ok"),
                                                ErlMap.map(
                                                        ErlMapEntry.entry(
                                                                ErlAtom.atom("access_key_id"),
                                                                ErlCallLocal.callLocal(
                                                                        "trim_credential",
                                                                        ErlVar.var("Id"))),
                                                        ErlMapEntry.entry(
                                                                ErlAtom.atom("secret_access_key"),
                                                                ErlCallLocal.callLocal(
                                                                        "trim_credential",
                                                                        ErlVar.var("Secret"))),
                                                        ErlMapEntry.entry(
                                                                ErlAtom.atom("session_token"),
                                                                ErlCallLocal.callLocal(
                                                                        "optional_credential",
                                                                        ErlVar.var("Token"))))))),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("_")),
                                ErlTuple.tuple(ErlAtom.atom("error"), ErlAtom.atom("not_found")))));
    }

    private static ErlFunction trimCredential() {
        return ErlFunction.function(
                "trim_credential",
                1,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Value")),
                        ErlCallLocal.callLocal(
                                "list_to_binary",
                                ErlRemoteCall.call(
                                        ErlAtom.atom("string"),
                                        "trim",
                                        ErlCallLocal.callLocal(
                                                "binary_to_list",
                                                ErlVar.var("Value")))))));
    }

    private static ErlFunction optionalCredential() {
        return ErlFunction.function(
                "optional_credential",
                1,
                List.of(
                        ErlClause.clause(
                                List.of(ErlAtomPattern.atomPattern("undefined")),
                                ErlAtom.atom("undefined")),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("Value")),
                                ErlCallLocal.callLocal("trim_credential", ErlVar.var("Value")))));
    }

    private static ErlFunction fetchJsonCredentials() {
        return ErlFunction.function(
                "fetch_json_credentials",
                1,
                List.of(ErlClause.blockClause(
                        List.of(ErlVarPattern.varPattern("Url")),
                        ErlCase.caseExpr(
                                ErlCallLocal.callLocal("http_get", ErlVar.var("Url"), ErlList.list()),
                                ErlClause.clause(
                                        List.of(ErlTuplePattern.tuplePattern(
                                                ErlAtomPattern.atomPattern("ok"),
                                                ErlVarPattern.varPattern("Body"))),
                                        ErlCallLocal.callLocal(
                                                "decode_json_credentials",
                                                ErlVar.var("Body"))),
                                ErlClause.clause(
                                        List.of(ErlTuplePattern.tuplePattern(
                                                ErlAtomPattern.atomPattern("error"),
                                                ErlVarPattern.varPattern("Reason"))),
                                        ErlTuple.tuple(
                                                ErlAtom.atom("error"),
                                                ErlVar.var("Reason")))))));
    }

    private static ErlFunction decodeJsonCredentials() {
        return ErlFunction.function(
                "decode_json_credentials",
                1,
                List.of(ErlClause.blockClause(
                        List.of(ErlVarPattern.varPattern("Body")),
                        ErlCapturedBlock.capturedBlock(
                                """
                                case jsx:decode(Body, [return_maps]) of
                                    #{<<"AccessKeyId">> := Id, <<"SecretAccessKey">> := Secret} = Doc ->
                                        Token = maps:get(<<"Token">>, Doc, undefined),
                                        {ok, #{access_key_id => Id,
                                              secret_access_key => Secret,
                                              session_token => Token}};
                                    _ ->
                                        {error, invalid_credentials}
                                end"""))));
    }

    private static ErlFunction ec2MetadataRequest() {
        return ErlFunction.function(
                "ec2_metadata_request",
                1,
                List.of(ErlClause.blockClause(
                        List.of(ErlVarPattern.varPattern("Path")),
                        ErlCapturedBlock.capturedBlock(
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
                                http_get(Url, Headers)"""))));
    }

    private static ErlFunction httpGet() {
        return ErlFunction.function(
                "http_get",
                2,
                List.of(ErlClause.blockClause(
                        List.of(
                                ErlVarPattern.varPattern("Url"),
                                ErlVarPattern.varPattern("ExtraHeaders")),
                        ErlCapturedBlock.capturedBlock(
                                """
                                Req = {Url, ExtraHeaders, get, <<>>},
                                case httpc:request(get, Req, [{ssl, [{verify, verify_none}]}], [{body_format, binary}]) of
                                    {ok, {{_, 200, _}, _, Body}} -> {ok, Body};
                                    {ok, {{_, _, _}, _, _}} -> {error, http_error};
                                    {error, Reason} -> {error, Reason}
                                end"""))));
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
