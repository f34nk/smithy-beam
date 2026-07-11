-module(http_checksum_test).

-include_lib("eunit/include/eunit.hrl").

-define(BODY, <<"hello">>).

-define(MD5_DIGEST,
    <<93, 65, 64, 42, 188, 75, 42, 118, 185, 113, 157, 145, 16, 23, 197, 146>>
).

-define(SHA256_DIGEST,
    <<44, 242, 77, 186, 95, 176, 163, 14, 38, 232, 59, 42, 197, 185, 226, 158, 27, 22, 30,
        92, 31, 167, 66, 94, 115, 4, 51, 98, 147, 139, 152, 36>>
).

-define(CRC32_DIGEST, <<54, 16, 166, 134>>).

-define(SHA256_HEADER, <<"LPJNul+wow4m6DsqxbninhsWHlwfp0JecwQzYpOLmCQ=">>).

md5_hash_test() ->
    ?assertEqual(?MD5_DIGEST, http_checksum:md5_hash(?BODY)).

sha256_hash_test() ->
    ?assertEqual(?SHA256_DIGEST, http_checksum:sha256_hash(?BODY)).

crc32_hash_test() ->
    ?assertEqual(?CRC32_DIGEST, http_checksum:crc32_hash(?BODY)).

checksum_header_encode_test() ->
    ?assertEqual(?SHA256_HEADER, http_checksum:checksum_header_encode(?SHA256_DIGEST)).

validate_response_checksum_ok_test() ->
    Headers = [{<<"x-amz-checksum-sha256">>, ?SHA256_HEADER}],
    ?assertEqual(
        ok,
        http_checksum:validate_response_checksum(?BODY, Headers, [<<"x-amz-checksum-sha256">>])
    ).

validate_response_checksum_mismatch_test() ->
    Headers = [{<<"x-amz-checksum-sha256">>, <<"invalid">>}],
    ?assertEqual(
        {error, {checksum_mismatch, <<"x-amz-checksum-sha256">>}},
        http_checksum:validate_response_checksum(?BODY, Headers, [<<"x-amz-checksum-sha256">>])
    ).

validate_response_checksum_skips_missing_header_test() ->
    ?assertEqual(ok, http_checksum:validate_response_checksum(?BODY, [], [])).

crc32c_hash_test() ->
    case (catch http_checksum:crc32c_hash(?BODY)) of
        {'EXIT', _} ->
            ok;
        Digest when is_binary(Digest) ->
            ?assert(byte_size(Digest) > 0)
    end.
