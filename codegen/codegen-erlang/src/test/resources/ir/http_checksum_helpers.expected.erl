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
                checksum_header_encode(
                    checksum_digest(Body, checksum_algorithm_from_header(HeaderName))
                ) =:= Expected
            of
                true -> ok;
                false -> {error, {checksum_mismatch, HeaderName}}
            end
    end.

checksum_algorithm_from_header(<<"x-amz-checksum-", Rest/binary>>) ->
    list_to_binary(string:uppercase(binary_to_list(Rest))).
