package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamCredentialProviders;
import io.smithy.beam.core.BeamCredentialProviders.BeamCredentialProviderKind;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamSigV4Metadata;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.stream.Collectors;

/**
 * Emits {@code <service>_credentials.erl} with a default AWS credential resolution chain
 * for services with {@code @aws.auth#sigv4}.
 */
public final class ErlangCredentialProviderEmitter {

    private ErlangCredentialProviderEmitter() {}

    public static void emit(ErlangContext ctx, ServiceShape service) {
        if (BeamSigV4Metadata.from(service).isEmpty()) {
            return;
        }

        BeamErlangLayout layout = new BeamErlangLayout(
                ctx.settings(), service.getId().getNamespace(), service);
        String credentialsModule = layout.credentialsModuleName();

        ctx.writerDelegator().useFileWriter(layout.credentialsModuleFile(), writer -> {
            writer.write("%% Generated AWS credential resolution for $L.", service.getId());
            writer.write("-module($L).", credentialsModule);
            writer.write("-export([resolve/1]).");
            writer.write("");
            writer.write("-type client_config() :: #{binary() => term()}.");
            writer.write("-type aws_credentials() :: #{");
            writer.write("    access_key_id := binary(),");
            writer.write("    secret_access_key := binary(),");
            writer.write("    session_token => binary() | undefined");
            writer.write("}.");
            writer.write("");
            writer.write("-spec resolve(client_config()) -> {ok, aws_credentials()} | {error, term()}.");
            writer.write("resolve(Config) ->");
            writer.indent();
            writer.write("case maps:get(credentials, Config, undefined) of");
            writer.indent();
            writer.write("undefined ->");
            writer.indent();
            writer.write("resolve_chain(Config);");
            writer.dedent();
            writer.write("Creds ->");
            writer.indent();
            writer.write("{ok, Creds}");
            writer.dedent();
            writer.dedent();
            writer.write("end.");
            writer.dedent();
            writer.write("");
            writeResolveChain(writer);
            writeResolveFromEnv(writer);
            writeResolveFromProfile(writer);
            writeResolveFromEcs(writer);
            writeResolveFromEc2(writer);
            writeSharedHelpers(writer);
        });
    }

    private static void writeResolveChain(ErlangWriter writer) {
        writer.write("-spec resolve_chain(client_config()) -> {ok, aws_credentials()} | {error, term()}.");
        writer.write("resolve_chain(Config) ->");
        writer.indent();
        String providers = BeamCredentialProviders.defaultChain().stream()
                .map(ErlangCredentialProviderEmitter::erlangProviderAtom)
                .collect(Collectors.joining(", "));
        writer.write("resolve_chain(Config, [$L]).", providers);
        writer.dedent();
        writer.write("");
        writer.write("resolve_chain(_Config, []) ->");
        writer.indent();
        writer.write("{error, not_found};");
        writer.dedent();
        writer.write("resolve_chain(Config, [Provider | Rest]) ->");
        writer.indent();
        writer.write("case resolve_provider(Provider, Config) of");
        writer.indent();
        writer.write("{ok, Creds} -> {ok, Creds};");
        writer.write("_ -> resolve_chain(Config, Rest)");
        writer.dedent();
        writer.write("end.");
        writer.dedent();
        writer.write("");
        writer.write("-spec resolve_provider(atom(), client_config()) ->");
        writer.write("    {ok, aws_credentials()} | {error, term()}.");
        for (BeamCredentialProviderKind kind : BeamCredentialProviders.defaultChain()) {
            writer.write("resolve_provider($L, Config) ->", erlangProviderAtom(kind));
            writer.indent();
            writer.write("resolve_from_$L(Config);", erlangProviderSuffix(kind));
            writer.dedent();
        }
        writer.write("");
    }

    private static void writeResolveFromEnv(ErlangWriter writer) {
        writer.write("-spec resolve_from_env(client_config()) -> {ok, aws_credentials()} | {error, term()}.");
        writer.write("resolve_from_env(_Config) ->");
        writer.indent();
        writer.write("case {os:getenv(\"AWS_ACCESS_KEY_ID\"), os:getenv(\"AWS_SECRET_ACCESS_KEY\")} of");
        writer.indent();
        writer.write("{Id, Secret} when Id =/= false, Secret =/= false ->");
        writer.indent();
        writer.write("Token = os:getenv(\"AWS_SESSION_TOKEN\"),");
        writer.write("{ok, #{access_key_id => list_to_binary(Id),");
        writer.write("      secret_access_key => list_to_binary(Secret),");
        writer.write("      session_token => env_session_token(Token)}};");
        writer.dedent();
        writer.write("_ ->");
        writer.indent();
        writer.write("{error, not_found}");
        writer.dedent();
        writer.dedent();
        writer.write("end.");
        writer.dedent();
        writer.write("");
    }

    private static void writeResolveFromProfile(ErlangWriter writer) {
        writer.write("-spec resolve_from_profile(client_config()) -> {ok, aws_credentials()} | {error, term()}.");
        writer.write("resolve_from_profile(Config) ->");
        writer.indent();
        writer.write("Profile = profile_name(Config),");
        writer.write("Path = profile_credentials_path(Config),");
        writer.write("case file:read_file(Path) of");
        writer.indent();
        writer.write("{ok, Contents} ->");
        writer.indent();
        writer.write("parse_profile_credentials(Contents, Profile);");
        writer.dedent();
        writer.write("{error, _} ->");
        writer.indent();
        writer.write("{error, not_found}");
        writer.dedent();
        writer.dedent();
        writer.write("end.");
        writer.dedent();
        writer.write("");
        writer.write("profile_name(Config) ->");
        writer.indent();
        writer.write("case maps:get(profile, Config, undefined) of");
        writer.indent();
        writer.write("undefined ->");
        writer.indent();
        writer.write("case os:getenv(\"AWS_PROFILE\") of");
        writer.indent();
        writer.write("false -> <<\"default\">>;");
        writer.write("Name -> list_to_binary(Name)");
        writer.dedent();
        writer.dedent();
        writer.write("Name -> Name");
        writer.dedent();
        writer.write("end.");
        writer.dedent();
        writer.write("");
        writer.write("profile_credentials_path(Config) ->");
        writer.indent();
        writer.write("case maps:get(credentials_path, Config, undefined) of");
        writer.indent();
        writer.write("undefined ->");
        writer.indent();
        writer.write("case os:getenv(\"AWS_SHARED_CREDENTIALS_FILE\") of");
        writer.indent();
        writer.write("false ->");
        writer.indent();
        writer.write("filename:join([os:getenv(\"HOME\"), <<\".aws/credentials\">>]);");
        writer.dedent();
        writer.write("Path -> list_to_binary(Path)");
        writer.dedent();
        writer.dedent();
        writer.write("Path -> Path");
        writer.dedent();
        writer.write("end.");
        writer.dedent();
        writer.write("");
    }

    private static void writeResolveFromEcs(ErlangWriter writer) {
        writer.write("-spec resolve_from_ecs(client_config()) -> {ok, aws_credentials()} | {error, term()}.");
        writer.write("resolve_from_ecs(_Config) ->");
        writer.indent();
        writer.write("case os:getenv(\"AWS_CONTAINER_CREDENTIALS_RELATIVE_URI\") of");
        writer.indent();
        writer.write("false ->");
        writer.indent();
        writer.write("case os:getenv(\"AWS_CONTAINER_CREDENTIALS_FULL_URI\") of");
        writer.indent();
        writer.write("false -> {error, not_found};");
        writer.write("Uri -> fetch_json_credentials(list_to_binary(Uri))");
        writer.dedent();
        writer.dedent();
        writer.write("Rel ->");
        writer.indent();
        writer.write("fetch_json_credentials(<<\"http://169.254.170.2\", Rel/binary>>)");
        writer.dedent();
        writer.dedent();
        writer.write("end.");
        writer.dedent();
        writer.write("");
    }

    private static void writeResolveFromEc2(ErlangWriter writer) {
        writer.write("-spec resolve_from_ec2(client_config()) -> {ok, aws_credentials()} | {error, term()}.");
        writer.write("resolve_from_ec2(_Config) ->");
        writer.indent();
        writer.write("case ec2_metadata_request(<<\"/latest/meta-data/iam/security-credentials/\">>) of");
        writer.indent();
        writer.write("{ok, RoleBin} ->");
        writer.indent();
        writer.write("Role = string:trim(binary_to_list(RoleBin)),");
        writer.write("Path = \"/latest/meta-data/iam/security-credentials/\" ++ Role,");
        writer.write("case ec2_metadata_request(list_to_binary(Path)) of");
        writer.indent();
        writer.write("{ok, JsonBin} -> decode_json_credentials(JsonBin);");
        writer.write("{error, Reason} -> {error, Reason}");
        writer.dedent();
        writer.dedent();
        writer.write("{error, Reason} ->");
        writer.indent();
        writer.write("{error, Reason}");
        writer.dedent();
        writer.dedent();
        writer.write("end.");
        writer.dedent();
        writer.write("");
    }

    private static void writeSharedHelpers(ErlangWriter writer) {
        writer.write("env_session_token(false) -> undefined;");
        writer.write("env_session_token(Token) -> list_to_binary(Token).");
        writer.write("");
        writer.write("parse_profile_credentials(Contents, Profile) ->");
        writer.indent();
        writer.write("Lines = binary:split(Contents, <<\"\\n\">>, [global]),");
        writer.write("case find_profile_section(Lines, Profile, #{}) of");
        writer.indent();
        writer.write("{ok, Creds} -> {ok, Creds};");
        writer.write("error -> {error, not_found}");
        writer.dedent();
        writer.write("end.");
        writer.dedent();
        writer.write("");
        writer.write("find_profile_section([], _Profile, Acc) ->");
        writer.indent();
        writer.write("maps_to_credentials(Acc);");
        writer.dedent();
        writer.write("find_profile_section([Line | Rest], Profile, Acc) ->");
        writer.indent();
        writer.write("Trimmed = string:trim(binary_to_list(Line)),");
        writer.write("case Trimmed of");
        writer.indent();
        writer.write("\"[\" ++ ProfileName ++ \"]\" ->");
        writer.indent();
        writer.write("Expected = binary_to_list(Profile),");
        writer.write("case ProfileName of");
        writer.indent();
        writer.write("Expected -> read_profile_entries(Rest, Acc);");
        writer.write("_ -> find_profile_section(Rest, Profile, #{})");
        writer.dedent();
        writer.dedent();
        writer.write("_ when map_size(Acc) > 0 ->");
        writer.indent();
        writer.write("maps_to_credentials(Acc);");
        writer.dedent();
        writer.write("_ ->");
        writer.indent();
        writer.write("find_profile_section(Rest, Profile, Acc)");
        writer.dedent();
        writer.dedent();
        writer.write("end.");
        writer.dedent();
        writer.write("");
        writer.write("read_profile_entries([], Acc) -> maps_to_credentials(Acc);");
        writer.write("read_profile_entries([Line | Rest], Acc) ->");
        writer.indent();
        writer.write("Trimmed = string:trim(binary_to_list(Line)),");
        writer.write("case Trimmed of");
        writer.indent();
        writer.write("\"[\" ++ _ -> maps_to_credentials(Acc);");
        writer.write("\"\" -> read_profile_entries(Rest, Acc);");
        writer.write("Entry ->");
        writer.indent();
        writer.write("case string:split(Entry, \"=\", leading) of");
        writer.indent();
        writer.write("[Key, Value] ->");
        writer.indent();
        writer.write("read_profile_entries(Rest, Acc#{list_to_binary(Key) => list_to_binary(Value)});");
        writer.dedent();
        writer.write("_ -> read_profile_entries(Rest, Acc)");
        writer.dedent();
        writer.dedent();
        writer.dedent();
        writer.write("end.");
        writer.dedent();
        writer.write("");
        writer.write("maps_to_credentials(#{<<\"aws_access_key_id\">> := Id,");
        writer.write("                      <<\"aws_secret_access_key\">> := Secret} = Fields) ->");
        writer.indent();
        writer.write("Token = maps:get(<<\"aws_session_token\">>, Fields, undefined),");
        writer.write("{ok, #{access_key_id => trim_credential(Id),");
        writer.write("      secret_access_key => trim_credential(Secret),");
        writer.write("      session_token => optional_credential(Token)}};");
        writer.dedent();
        writer.write("maps_to_credentials(_) ->");
        writer.indent();
        writer.write("{error, not_found}.");
        writer.dedent();
        writer.write("");
        writer.write("trim_credential(Value) ->");
        writer.indent();
        writer.write("list_to_binary(string:trim(binary_to_list(Value))).");
        writer.dedent();
        writer.write("");
        writer.write("optional_credential(undefined) -> undefined;");
        writer.write("optional_credential(Value) -> trim_credential(Value).");
        writer.write("");
        writer.write("fetch_json_credentials(Url) ->");
        writer.indent();
        writer.write("case http_get(Url, []) of");
        writer.indent();
        writer.write("{ok, Body} -> decode_json_credentials(Body);");
        writer.write("{error, Reason} -> {error, Reason}");
        writer.dedent();
        writer.write("end.");
        writer.dedent();
        writer.write("");
        writer.write("decode_json_credentials(Body) ->");
        writer.indent();
        writer.write("case jsx:decode(Body, [return_maps]) of");
        writer.indent();
        writer.write("#{<<\"AccessKeyId\">> := Id, <<\"SecretAccessKey\">> := Secret} = Doc ->");
        writer.indent();
        writer.write("Token = maps:get(<<\"Token\">>, Doc, undefined),");
        writer.write("{ok, #{access_key_id => Id,");
        writer.write("      secret_access_key => Secret,");
        writer.write("      session_token => Token}};");
        writer.dedent();
        writer.write("_ ->");
        writer.indent();
        writer.write("{error, invalid_credentials}");
        writer.dedent();
        writer.dedent();
        writer.write("end.");
        writer.dedent();
        writer.write("");
        writer.write("ec2_metadata_request(Path) ->");
        writer.indent();
        writer.write("TokenReq = {");
        writer.write("    \"http://169.254.169.254/latest/api/token\",");
        writer.write("    [{\"X-aws-ec2-metadata-token-ttl-seconds\", \"60\"}],");
        writer.write("    put,");
        writer.write("    <<>>");
        writer.write("},");
        writer.write("Headers =");
        writer.indent();
        writer.write("case httpc:request(put, TokenReq, [{ssl, [{verify, verify_none}]}], []) of");
        writer.indent();
        writer.write("{ok, {{_, 200, _}, RespHeaders, _}} ->");
        writer.indent();
        writer.write("case proplists:get_value(\"x-aws-ec2-metadata-token\", RespHeaders) of");
        writer.indent();
        writer.write("undefined -> [];");
        writer.write("Token -> [{\"X-aws-ec2-metadata-token\", Token}]");
        writer.dedent();
        writer.dedent();
        writer.write("_ ->");
        writer.indent();
        writer.write("[]");
        writer.dedent();
        writer.dedent();
        writer.write("end,");
        writer.write("Url = \"http://169.254.169.254\" ++ binary_to_list(Path),");
        writer.write("http_get(Url, Headers).");
        writer.dedent();
        writer.write("");
        writer.write("http_get(Url, ExtraHeaders) ->");
        writer.indent();
        writer.write("Req = {Url, ExtraHeaders, get, <<>>},");
        writer.write("case httpc:request(get, Req, [{ssl, [{verify, verify_none}]}], [{body_format, binary}]) of");
        writer.indent();
        writer.write("{ok, {{_, 200, _}, _, Body}} -> {ok, Body};");
        writer.write("{ok, {{_, _, _}, _, _}} -> {error, http_error};");
        writer.write("{error, Reason} -> {error, Reason}");
        writer.dedent();
        writer.write("end.");
        writer.dedent();
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
        return switch (kind) {
            case ENV -> "env";
            case PROFILE -> "profile";
            case ECS -> "ecs";
            case EC2 -> "ec2";
        };
    }
}
