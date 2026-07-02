%% Generated AWS credential resolution for smithy.beam.test.sigv4#Sigv4TestService.
-module(sigv4test_service_credentials).
-export([resolve/1]).
-type client_config() :: #{binary() => term()}.
-type aws_credentials() :: #{
    access_key_id := binary(),
    secret_access_key := binary(),
    session_token => binary() | undefined
}.

-spec resolve(client_config()) -> {ok, aws_credentials()} | {error, term()}.
resolve(Config) ->
    case maps:get(credentials, Config, undefined) of
        undefined -> resolve_chain(Config);
        Creds -> {ok, Creds}
    end.

-spec resolve_chain(client_config()) -> {ok, aws_credentials()} | {error, term()}.
resolve_chain(Config) -> resolve_chain(Config, [env, profile, ecs, ec2]).

resolve_chain(_Config, []) -> {error, not_found};
resolve_chain(Config, [Provider | Rest]) ->
    case resolve_provider(Provider, Config) of
        {ok, Creds} -> {ok, Creds};
        _ -> resolve_chain(Config, Rest)
    end.

-spec resolve_provider(atom(), client_config()) -> {ok, aws_credentials()} | {error, term()}.
resolve_provider(env, Config) -> resolve_from_env(Config);
resolve_provider(profile, Config) -> resolve_from_profile(Config);
resolve_provider(ecs, Config) -> resolve_from_ecs(Config);
resolve_provider(ec2, Config) -> resolve_from_ec2(Config).

-spec resolve_from_env(client_config()) -> {ok, aws_credentials()} | {error, term()}.
resolve_from_env(_Config) ->
    case {os:getenv("AWS_ACCESS_KEY_ID"), os:getenv("AWS_SECRET_ACCESS_KEY")} of
        {Id, Secret} when Id =/= false, Secret =/= false ->
            Token = os:getenv("AWS_SESSION_TOKEN"),
            {ok, #{access_key_id => list_to_binary(Id),
                  secret_access_key => list_to_binary(Secret),
                  session_token => env_session_token(Token)}};
        _ ->
            {error, not_found}
    end.

-spec resolve_from_profile(client_config()) -> {ok, aws_credentials()} | {error, term()}.
resolve_from_profile(Config) ->
    Profile = profile_name(Config),
    Path = profile_credentials_path(Config),
    case file:read_file(Path) of
        {ok, Contents} ->
            parse_profile_credentials(Contents, Profile);
        {error, _} ->
            {error, not_found}
    end.

profile_name(Config) ->
    case maps:get(profile, Config, undefined) of
        undefined ->
            case os:getenv("AWS_PROFILE") of
                false -> <<"default">>;
                Name -> list_to_binary(Name)
            end;
        Name -> Name
    end.

profile_credentials_path(Config) ->
    case maps:get(credentials_path, Config, undefined) of
        undefined ->
            case os:getenv("AWS_SHARED_CREDENTIALS_FILE") of
                false ->
                    filename:join([os:getenv("HOME"), <<".aws/credentials">>]);
                Path -> list_to_binary(Path)
            end;
        Path -> Path
    end.

env_session_token(false) -> undefined;
env_session_token(Token) -> list_to_binary(Token).

parse_profile_credentials(Contents, Profile) ->
    Lines = binary:split(Contents, <<"\n">>, [global]),
    case find_profile_section(Lines, Profile, #{}) of
        {ok, Creds} -> {ok, Creds};
        error -> {error, not_found}
    end.

find_profile_section([], _Profile, Acc) -> maps_to_credentials(Acc);
find_profile_section([Line | Rest], Profile, Acc) ->
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
    end.

read_profile_entries([], Acc) -> maps_to_credentials(Acc);
read_profile_entries([Line | Rest], Acc) ->
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
    end.

maps_to_credentials(#{<<"aws_access_key_id">> := Id, <<"aws_secret_access_key">> := Secret} = Fields) ->
    Token = maps:get(<<"aws_session_token">>, Fields, undefined),
    {ok, #{access_key_id => trim_credential(Id), secret_access_key => trim_credential(Secret), session_token => optional_credential(Token)}};
maps_to_credentials(_) -> {error, not_found}.

trim_credential(Value) -> list_to_binary(string:trim(binary_to_list(Value))).

optional_credential(undefined) -> undefined;
optional_credential(Value) -> trim_credential(Value).

-spec resolve_from_ecs(client_config()) -> {ok, aws_credentials()} | {error, term()}.
resolve_from_ecs(_Config) ->
    case os:getenv("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI") of
        false ->
            case os:getenv("AWS_CONTAINER_CREDENTIALS_FULL_URI") of
                false -> {error, not_found};
                Uri -> fetch_json_credentials(list_to_binary(Uri))
            end;
        Rel ->
            fetch_json_credentials(<<"http://169.254.170.2", Rel/binary>>)
    end.

-spec resolve_from_ec2(client_config()) -> {ok, aws_credentials()} | {error, term()}.
resolve_from_ec2(_Config) ->
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
    end.

fetch_json_credentials(Url) ->
    case http_get(Url, []) of
        {ok, Body} -> decode_json_credentials(Body);
        {error, Reason} -> {error, Reason}
    end.

decode_json_credentials(Body) ->
    case jsone:try_decode(Body) of
        {ok, #{<<"AccessKeyId">> := Id, <<"SecretAccessKey">> := Secret} = Doc, _} ->
            Token = maps:get(<<"Token">>, Doc, undefined),
            {ok, #{access_key_id => Id,
                  secret_access_key => Secret,
                  session_token => Token}};
        _ ->
            {error, invalid_credentials}
    end.

ec2_metadata_request(Path) ->
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
    http_get(Url, Headers).

http_get(Url, ExtraHeaders) ->
    Req = {Url, ExtraHeaders, get, <<>>},
    case httpc:request(get, Req, [{ssl, [{verify, verify_none}]}], [{body_format, binary}]) of
        {ok, {{_, 200, _}, _, Body}} -> {ok, Body};
        {ok, {{_, _, _}, _, _}} -> {error, http_error};
        {error, Reason} -> {error, Reason}
    end.