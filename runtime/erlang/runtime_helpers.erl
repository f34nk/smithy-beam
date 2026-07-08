%% Shared smithy-beam Erlang runtime helpers.
-module(runtime_helpers).
-export([
    parse_labels/2,
    headers_set/3,
    checksum_header_encode/1,
    sha256_hash/1,
    crc32_hash/1,
    split_base_url/1,
    resolve_base_url/1
]).
-spec parse_labels(binary(), binary()) -> {ok, map()} | {error, path_mismatch}.
parse_labels(Path, Template) ->
    case match_segments(segments(Path), segments(Template), #{}) of
        {ok, Labels} -> {ok, Labels};
        error -> {error, path_mismatch}
    end.

segments(Path) -> Parts = binary:split(Path, <<"/">>, [global]),
[S || S <- Parts, S =/= <<>>].

match_segments([], [], Acc) ->
    {ok, Acc};
match_segments([Seg | RestPath], [TplSeg | RestTpl], Acc) ->
    case label_name(TplSeg) of
        {ok, Key} ->
            Val = uri_string:unquote(Seg),
            match_segments(RestPath, RestTpl, Acc#{Key => Val});
        error ->
            case (Seg =:= TplSeg) of
                true -> match_segments(RestPath, RestTpl, Acc);
                false -> error
            end
    end;
match_segments(_, _, _) ->
    error.

label_name(<<"{", Rest/binary>>) ->
    case binary:split(Rest, <<"}">>) of
        [Label | [<<>>]] -> {ok, Label};
        _ -> error
    end;
label_name(_) ->
    error.

headers_set(Name, Value, Headers) -> lists:keystore(Name, 1, Headers, {Name, Value}).

checksum_header_encode(Data) when is_binary(Data) -> base64:encode(Data).

md5_hash(Body) -> crypto:hash(md5, Body).

sha256_hash(Body) -> crypto:hash(sha256, Body).

crc32_hash(Body) -> <<(erlang:crc32(Body)):32/big-unsigned-integer>>.

crc32c_hash(Body) -> crypto:hash(crc32c, Body).

crc64nvme_hash(_Body) -> error({unsupported_checksum_algorithm, crc64nvme}).

xxhash64_hash(_Body) -> error({unsupported_checksum_algorithm, xxhash64}).

xxhash3_hash(_Body) -> error({unsupported_checksum_algorithm, xxhash3}).

xxhash128_hash(_Body) -> error({unsupported_checksum_algorithm, xxhash128}).

checksum_digest(Body, <<"MD5">>) -> md5_hash(Body);
checksum_digest(Body, <<"SHA256">>) -> sha256_hash(Body);
checksum_digest(Body, <<"CRC32">>) -> crc32_hash(Body);
checksum_digest(Body, <<"CRC32C">>) -> crc32c_hash(Body).

validate_response_checksum(_Body, _Headers, []) ->
    ok;
validate_response_checksum(Body, Headers, [HeaderName | Rest]) ->
    case proplists:get_value(HeaderName, Headers, undefined) of
        undefined ->
            validate_response_checksum(Body, Headers, Rest);
        Expected ->
            case
                (checksum_header_encode(
                    checksum_digest(Body, checksum_algorithm_from_header(HeaderName))
                ) =:= Expected)
            of
                true -> ok;
                false -> {error, {checksum_mismatch, HeaderName}}
            end
    end.

checksum_algorithm_from_header(<<"x-amz-checksum-", Rest/binary>>) ->
    list_to_binary(string:uppercase(binary_to_list(Rest))).

-spec resolve_base_url(map()) -> binary().
resolve_base_url(Config) ->
    Prefix = maps:get(endpoint_prefix, Config),
    Region = maps:get(region, Config, <<"us-east-1">>),
    <<"https://", Prefix/binary, ".", Region/binary, ".amazonaws.com">>.

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
            {<<(list_to_binary(Scheme))/binary, "://">>, <<(list_to_binary(Host))/binary,
                PortSuffix/binary>>};
        _ ->
            {<<>>, BaseUrl}
    end.
