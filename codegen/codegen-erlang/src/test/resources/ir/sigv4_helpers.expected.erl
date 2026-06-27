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

query_suffix(Query) when map_size(Query) =:= 0 -> <<>>;
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

session_token_option(undefined) -> [];
session_token_option(Token) -> [{session_token, Token}].

endpoint_host_from_config(Config) ->
    case maps:get(base_url, Config, undefined) of
        undefined ->
            case
                {
                    maps:get(endpoint_prefix, Config, undefined),
                    maps:get(region, Config, <<"us-east-1">>)
                }
            of
                {undefined, _} -> undefined;
                {Prefix, Region} -> <<Prefix/binary, ".", Region/binary, ".amazonaws.com">>
            end;
        BaseUrl ->
            {_Scheme, Authority} = split_base_url(BaseUrl),
            Authority
    end.

split_base_url(<<>>) ->
    {<<>>, <<>>};
split_base_url(BaseUrl) ->
    case uri_string:parse(binary_to_list(BaseUrl)) of
        #{scheme := Scheme, host := Host} = Parts ->
            PortSuffix =
                case maps:get(port, Parts, undefined) of
                    undefined -> <<>>;
                    Port -> <<":", (integer_to_binary(Port))/binary>>
                end,
            {<<(list_to_binary(Scheme))/binary, "://">>, <<
                (list_to_binary(Host))/binary, PortSuffix/binary
            >>};
        _ ->
            {<<>>, BaseUrl}
    end.
