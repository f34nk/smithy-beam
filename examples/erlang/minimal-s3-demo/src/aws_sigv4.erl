-module(aws_sigv4).

-export([sign/5, presign/5]).

-record(http_request, {
    method = <<"GET">>,
    path = <<"/">>,
    query = #{},
    headers = [],
    body = <<>>,
    host = undefined,
    stream = undefined
}).

-spec sign(term(), map(), binary(), binary(), map()) -> term().
sign(Request, Credentials, Region, Service, Opts) ->
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

-spec presign(term(), map(), binary(), binary(), map()) ->
    {ok, binary()} | {error, term()}.
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
        ]
        ++ body_digest_option(Opts)
        ++ session_token_option(maps:get(session_token, Credentials, undefined)),
    try
        {ok, aws_signature:sign_v4_query_params(
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

resolve_host(#http_request{host = Host, headers = Headers}, Opts) ->
    coalesce([
        Host,
        maps:get(host, Opts, undefined),
        maps:get(endpoint_host, Opts, undefined),
        header_host(Headers)
    ]).

coalesce([H | Rest]) ->
    case H of
        undefined -> coalesce(Rest);
        <<>> -> coalesce(Rest);
        Value -> Value
    end;
coalesce([]) ->
    <<"localhost">>.

build_url(Host, Path, Query) ->
    <<"https://", Host/binary, Path/binary, (query_suffix(Query))/binary>>.

query_suffix(Query) when map_size(Query) =:= 0 ->
    <<>>;
query_suffix(Query) ->
    Params = uri_string:compose_query([{K, V} || {K, V} <- maps:to_list(Query)]),
    <<"?", Params/binary>>.

ensure_host_header(Headers, Host) ->
    case header_host(Headers) of
        undefined -> [{<<"host">>, Host} | Headers];
        _ -> Headers
    end.

header_host(Headers) ->
    proplists:get_value(<<"host">>, Headers, proplists:get_value(<<"Host">>, Headers)).

maybe_add_session_token(Headers, undefined) ->
    Headers;
maybe_add_session_token(Headers, Token) ->
    case proplists:get_value(<<"x-amz-security-token">>, Headers) of
        undefined -> [{<<"x-amz-security-token">>, Token} | Headers];
        _ -> Headers
    end.

sign_options(Service, Opts) ->
    [{uri_encode_path, Service =/= <<"s3">>}] ++ body_digest_option(Opts).

body_digest_option(Opts) ->
    case maps:get(unsigned_payload, Opts, false) of
        true -> [{body_digest, <<"UNSIGNED-PAYLOAD">>}];
        false -> []
    end.

session_token_option(undefined) ->
    [];
session_token_option(Token) ->
    [{session_token, Token}].

