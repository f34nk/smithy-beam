%% Shared smithy-beam Erlang @httpChecksum helpers.
-module(http_checksum).
-export([
    checksum_header_encode/1,
    sha256_hash/1,
    crc32_hash/1,
    md5_hash/1,
    crc32c_hash/1,
    validate_response_checksum/3
]).

%% @doc Base64-encode a checksum digest for an HTTP header value.
-spec checksum_header_encode(binary()) -> binary().
checksum_header_encode(Data) when is_binary(Data) -> base64:encode(Data).

%% @doc Compute an MD5 digest of the request or response body.
-spec md5_hash(binary()) -> binary().
md5_hash(Body) -> crypto:hash(md5, Body).

%% @doc Compute a SHA256 digest of the request or response body.
-spec sha256_hash(binary()) -> binary().
sha256_hash(Body) -> crypto:hash(sha256, Body).

%% @doc Compute a CRC32 digest of the request or response body.
-spec crc32_hash(binary()) -> binary().
crc32_hash(Body) -> <<(erlang:crc32(Body)):32/big-unsigned-integer>>.

%% @doc Compute a CRC32C digest of the request or response body.
-spec crc32c_hash(binary()) -> binary().
crc32c_hash(Body) -> crypto:hash(crc32c, Body).

-spec crc64nvme_hash(binary()) -> no_return().
crc64nvme_hash(_Body) -> error({unsupported_checksum_algorithm, crc64nvme}).

-spec xxhash64_hash(binary()) -> no_return().
xxhash64_hash(_Body) -> error({unsupported_checksum_algorithm, xxhash64}).

-spec xxhash3_hash(binary()) -> no_return().
xxhash3_hash(_Body) -> error({unsupported_checksum_algorithm, xxhash3}).

-spec xxhash128_hash(binary()) -> no_return().
xxhash128_hash(_Body) -> error({unsupported_checksum_algorithm, xxhash128}).

-spec checksum_digest(binary(), binary()) -> binary().
checksum_digest(Body, <<"MD5">>) -> md5_hash(Body);
checksum_digest(Body, <<"SHA256">>) -> sha256_hash(Body);
checksum_digest(Body, <<"CRC32">>) -> crc32_hash(Body);
checksum_digest(Body, <<"CRC32C">>) -> crc32c_hash(Body).

%% @doc Validate response checksum headers against the response body.
-spec validate_response_checksum(binary(), [{binary(), binary()}], [binary()]) ->
    ok | {error, {checksum_mismatch, binary()}}.
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

-spec checksum_algorithm_from_header(binary()) -> binary().
checksum_algorithm_from_header(<<"x-amz-checksum-", Rest/binary>>) ->
    list_to_binary(string:uppercase(binary_to_list(Rest))).
