%% Shared smithy-beam Erlang @aws.auth#sigv4 helpers
-module(aws_sigv4).
-include("runtime_types.hrl").
-export([
    sign/3,
    presign/5,
    presign_url/3
]).

-type client_config() :: #{binary() => term()}.

%% @doc Sign an HTTP request with AWS Signature Version 4.
-spec sign(client_config(), Operation :: atom(), http_request()) -> http_request().
sign(Config, Operation, Request) ->
    Credentials = maps:get(credentials, Config),
    Region = maps:get(region, Config, <<"us-east-1">>),
    Service = maps:get(signing_name, Config),
    Unsigned = maps:get({unsigned_payload, Operation}, Config, false),
    Opts = #{
        unsigned_payload => Unsigned,
        endpoint_host => utils:endpoint_host_from_config(Config)
    },
    sign_request(Request, Credentials, Region, Service, Opts).

%% @doc Build a presigned URL for an HTTP request from client config.
-spec presign_url(client_config(), Operation :: atom(), http_request()) ->
    {ok, binary()} | {error, term()}.
presign_url(Config, Operation, Request) ->
    Credentials = maps:get(credentials, Config),
    Region = maps:get(region, Config, <<"us-east-1">>),
    Service = maps:get(signing_name, Config),
    Expires = maps:get(presign_expires, Config, 900),
    Unsigned = maps:get({unsigned_payload, Operation}, Config, false),
    Opts = #{
        expires => Expires,
        unsigned_payload => Unsigned,
        endpoint_host => utils:endpoint_host_from_config(Config)
    },
    presign(Request, Credentials, Region, Service, Opts).

%% @doc Build a presigned URL with explicit credentials and signing options.
-spec presign(http_request(), map(), binary(), binary(), map()) -> {ok, binary()} | {error, term()}.
presign(Request, Credentials, Region, Service, Opts) ->
    AccessKeyId = maps:get(access_key_id, Credentials),
    SecretAccessKey = maps:get(secret_access_key, Credentials),
    DateTime = calendar:universal_time(),
    Host = resolve_host(Request, Opts),
    Url = build_url(Host, Request#http_request.path, Request#http_request.query),
    Ttl = maps:get(expires, Opts, 900),
    QueryOpts =
        [
            {ttl, Ttl},
            {uri_encode_path, Service =/= <<"s3">>}
        ] ++
            body_digest_option(Opts) ++
            session_token_option(maps:get(session_token, Credentials, undefined)),
    try
        {ok,
            aws_signature:sign_v4_query_params(
                AccessKeyId,
                SecretAccessKey,
                Region,
                Service,
                DateTime,
                Request#http_request.method,
                Url,
                QueryOpts
            )}
    catch
        _:Reason ->
            {error, Reason}
    end.

-spec sign_request(http_request(), map(), binary(), binary(), map()) -> http_request().
sign_request(Request, Credentials, Region, Service, Opts) ->
    AccessKeyId = maps:get(access_key_id, Credentials),
    SecretAccessKey = maps:get(secret_access_key, Credentials),
    DateTime = calendar:universal_time(),
    Host = resolve_host(Request, Opts),
    Url = build_url(Host, Request#http_request.path, Request#http_request.query),
    Headers0 = ensure_host_header(Request#http_request.headers, Host),
    Headers1 = maybe_add_session_token(Headers0, maps:get(session_token, Credentials, undefined)),
    SignOpts = sign_options(Service, Opts),
    SignedHeaders = aws_signature:sign_v4(
        AccessKeyId,
        SecretAccessKey,
        Region,
        Service,
        DateTime,
        Request#http_request.method,
        Url,
        Headers1,
        Request#http_request.body,
        SignOpts
    ),
    Request#http_request{headers = SignedHeaders}.

-spec resolve_host(http_request(), map()) -> binary().
resolve_host(#http_request{host = Host, headers = Headers}, Opts) ->
    coalesce([
        Host,
        maps:get(host, Opts, undefined),
        maps:get(endpoint_host, Opts, undefined),
        header_host(Headers)
    ]).

-spec coalesce([binary() | undefined]) -> binary().
coalesce([H | Rest]) ->
    case H of
        undefined -> coalesce(Rest);
        <<>> -> coalesce(Rest);
        Value -> Value
    end;
coalesce([]) ->
    <<"localhost">>.

-spec build_url(binary(), binary(), #{binary() => binary()}) -> binary().
build_url(Host, Path, Query) ->
    <<"https://", Host/binary, Path/binary, (query_suffix(Query))/binary>>.

-spec query_suffix(#{binary() => binary()}) -> binary().
query_suffix(Query) when map_size(Query) =:= 0 ->
    <<>>;
query_suffix(Query) ->
    Params = uri_string:compose_query([{K, V} || {K, V} <- maps:to_list(Query)]),
    <<"?", Params/binary>>.

-spec ensure_host_header([{binary(), binary()}], binary()) -> [{binary(), binary()}].
ensure_host_header(Headers, Host) ->
    case header_host(Headers) of
        undefined -> [{<<"host">>, Host} | Headers];
        _ -> Headers
    end.

-spec header_host([{binary(), binary()}]) -> binary() | undefined.
header_host(Headers) ->
    proplists:get_value(<<"host">>, Headers, proplists:get_value(<<"Host">>, Headers)).

-spec maybe_add_session_token([{binary(), binary()}], binary() | undefined) ->
    [{binary(), binary()}].
maybe_add_session_token(Headers, undefined) ->
    Headers;
maybe_add_session_token(Headers, Token) ->
    case proplists:get_value(<<"x-amz-security-token">>, Headers) of
        undefined -> [{<<"x-amz-security-token">>, Token} | Headers];
        _ -> Headers
    end.

-spec sign_options(binary(), map()) -> [{atom(), term()}].
sign_options(Service, Opts) ->
    [{uri_encode_path, Service =/= <<"s3">>}] ++ body_digest_option(Opts).

-spec body_digest_option(map()) -> [{atom(), binary()}].
body_digest_option(Opts) ->
    case maps:get(unsigned_payload, Opts, false) of
        true -> [{body_digest, <<"UNSIGNED-PAYLOAD">>}];
        false -> []
    end.

-spec session_token_option(binary() | undefined) -> [{atom(), binary()}].
session_token_option(undefined) -> [];
session_token_option(Token) -> [{session_token, Token}].
