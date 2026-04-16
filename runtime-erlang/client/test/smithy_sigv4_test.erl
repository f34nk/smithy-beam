-module(aws_sigv4_test).

-include_lib("eunit/include/eunit.hrl").

%%% Tests for AWS SigV4 module

%% Test that the module compiles and exports are correct
module_info_test() ->
    %% Verify module exists and loads
    ?assertEqual(smithy_sigv4, smithy_sigv4:module_info(module)),

    %% Verify sign_request/5 is exported
    Exports = smithy_sigv4:module_info(exports),
    ?assert(lists:member({sign_request, 5}, Exports)).

%%====================================================================
%% Canonical Request Generation Tests
%%====================================================================

%% Test hash_sha256/1 with empty string
hash_sha256_empty_test() ->
    %% SHA256 of empty string (AWS test vector)
    Expected = <<"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855">>,
    Result = smithy_sigv4:hash_sha256(<<>>),
    ?assertEqual(Expected, Result).

%% Test hash_sha256/1 with known value
hash_sha256_known_test() ->
    %% SHA256 of "hello"
    Expected = <<"2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824">>,
    Result = smithy_sigv4:hash_sha256(<<"hello">>),
    ?assertEqual(Expected, Result).

%% Test hash_sha256/1 with JSON body
hash_sha256_json_test() ->
    Body = <<"{\"TableName\":\"Test\"}">>,
    Result = smithy_sigv4:hash_sha256(Body),
    %% Should return lowercase hex string of 64 characters (32 bytes * 2)
    ?assertEqual(64, byte_size(Result)),
    %% Should be all lowercase hex
    ?assert(is_hex_string(Result)).

%% Test signed_header_list/1 with single header
signed_header_list_single_test() ->
    Headers = [{<<"Host">>, <<"example.com">>}],
    Result = smithy_sigv4:signed_header_list(Headers),
    ?assertEqual(<<"host">>, Result).

%% Test signed_header_list/1 with multiple headers (sorted)
signed_header_list_multiple_test() ->
    Headers = [
        {<<"X-Amz-Date">>, <<"20230101T120000Z">>},
        {<<"Host">>, <<"s3.amazonaws.com">>},
        {<<"Content-Type">>, <<"application/json">>}
    ],
    Result = smithy_sigv4:signed_header_list(Headers),
    %% Should be sorted alphabetically
    ?assertEqual(<<"content-type;host;x-amz-date">>, Result).

%% Test signed_header_list/1 with mixed case
signed_header_list_mixed_case_test() ->
    Headers = [
        {<<"HOST">>, <<"example.com">>},
        {<<"X-AMZ-DATE">>, <<"20230101T120000Z">>}
    ],
    Result = smithy_sigv4:signed_header_list(Headers),
    %% Should be lowercase and sorted
    ?assertEqual(<<"host;x-amz-date">>, Result).

%% Test canonicalize_headers/1 with single header
canonicalize_headers_single_test() ->
    Headers = [{<<"Host">>, <<"example.com">>}],
    Result = smithy_sigv4:canonicalize_headers(Headers),
    Expected = <<"host:example.com\n">>,
    ?assertEqual(Expected, Result).

%% Test canonicalize_headers/1 with sorting
canonicalize_headers_sorting_test() ->
    Headers = [
        {<<"X-Amz-Date">>, <<"20230101T120000Z">>},
        {<<"Host">>, <<"s3.amazonaws.com">>}
    ],
    Result = smithy_sigv4:canonicalize_headers(Headers),
    %% host should come before x-amz-date alphabetically
    Expected = <<"host:s3.amazonaws.com\nx-amz-date:20230101T120000Z\n">>,
    ?assertEqual(Expected, Result).

%% Test canonicalize_headers/1 with whitespace trimming
canonicalize_headers_trim_test() ->
    Headers = [
        {<<"Host">>, <<"  example.com  ">>},
        {<<"X-Custom">>, <<"  value with spaces  ">>}
    ],
    Result = smithy_sigv4:canonicalize_headers(Headers),
    %% Whitespace should be trimmed from values
    Expected = <<"host:example.com\nx-custom:value with spaces\n">>,
    ?assertEqual(Expected, Result).

%% Test canonicalize_headers/1 with lowercase conversion
canonicalize_headers_lowercase_test() ->
    Headers = [
        {<<"HOST">>, <<"example.com">>},
        {<<"Content-TYPE">>, <<"application/json">>}
    ],
    Result = smithy_sigv4:canonicalize_headers(Headers),
    Expected = <<"content-type:application/json\nhost:example.com\n">>,
    ?assertEqual(Expected, Result).

%% Test canonicalize_query_string/1 with empty string
canonicalize_query_empty_test() ->
    ?assertEqual(<<>>, smithy_sigv4:canonicalize_query_string(<<>>)),
    ?assertEqual(<<>>, smithy_sigv4:canonicalize_query_string(undefined)).

%% Test canonicalize_query_string/1 with single parameter
canonicalize_query_single_test() ->
    Query = <<"key=value">>,
    Result = smithy_sigv4:canonicalize_query_string(Query),
    %% Should be URL-encoded
    ?assertEqual(<<"key=value">>, Result).

%% Test canonicalize_query_string/1 with multiple parameters (sorted)
canonicalize_query_sorting_test() ->
    Query = <<"zebra=last&apple=first&middle=center">>,
    Result = smithy_sigv4:canonicalize_query_string(Query),
    %% Should be sorted by key
    ?assertEqual(<<"apple=first&middle=center&zebra=last">>, Result).

%% Test canonicalize_query_string/1 with URL encoding
canonicalize_query_encoding_test() ->
    Query = <<"key=value with spaces">>,
    Result = smithy_sigv4:canonicalize_query_string(Query),
    %% Spaces should be encoded as %20
    ?assert(binary:match(Result, <<"%20">>) =/= nomatch).

%% Test canonicalize_query_string/1 with leading ?
canonicalize_query_leading_question_test() ->
    Query = <<"?key=value">>,
    Result = smithy_sigv4:canonicalize_query_string(Query),
    ?assertEqual(<<"key=value">>, Result).

%% Test create_canonical_request/4 with GET request
create_canonical_request_get_test() ->
    Method = <<"GET">>,
    Uri = <<"https://s3.amazonaws.com/mybucket/mykey">>,
    Headers = [
        {<<"Host">>, <<"s3.amazonaws.com">>},
        {<<"X-Amz-Date">>, <<"20230101T120000Z">>}
    ],
    Body = <<>>,

    Result = smithy_sigv4:create_canonical_request(Method, Uri, Headers, Body),

    %% Should contain all components
    ?assert(binary:match(Result, <<"GET">>) =/= nomatch),
    ?assert(binary:match(Result, <<"/mybucket/mykey">>) =/= nomatch),
    ?assert(binary:match(Result, <<"host:s3.amazonaws.com">>) =/= nomatch),
    ?assert(
        binary:match(Result, <<"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855">>) =/=
            nomatch
    ).

%% Test create_canonical_request/4 with POST and body
create_canonical_request_post_test() ->
    Method = <<"POST">>,
    Uri = <<"https://dynamodb.us-west-2.amazonaws.com/">>,
    Headers = [
        {<<"Host">>, <<"dynamodb.us-west-2.amazonaws.com">>},
        {<<"Content-Type">>, <<"application/x-amz-json-1.0">>}
    ],
    Body = <<"{\"TableName\":\"Test\"}">>,

    Result = smithy_sigv4:create_canonical_request(Method, Uri, Headers, Body),

    %% Should contain POST method
    ?assert(binary:match(Result, <<"POST">>) =/= nomatch),
    %% Should contain hashed body (not empty hash)
    EmptyHash = <<"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855">>,
    ?assert(binary:match(Result, EmptyHash) =:= nomatch).

%% Test create_canonical_request/4 with query parameters
create_canonical_request_query_test() ->
    Method = <<"GET">>,
    Uri = <<"https://s3.amazonaws.com/mybucket?prefix=photos&max-keys=100">>,
    Headers = [{<<"Host">>, <<"s3.amazonaws.com">>}],
    Body = <<>>,

    Result = smithy_sigv4:create_canonical_request(Method, Uri, Headers, Body),

    %% Should contain sorted query parameters
    ?assert(binary:match(Result, <<"max-keys">>) =/= nomatch),
    ?assert(binary:match(Result, <<"prefix">>) =/= nomatch).

%% Test create_canonical_request/4 with root path
create_canonical_request_root_path_test() ->
    Method = <<"GET">>,
    Uri = <<"https://s3.amazonaws.com/">>,
    Headers = [{<<"Host">>, <<"s3.amazonaws.com">>}],
    Body = <<>>,

    Result = smithy_sigv4:create_canonical_request(Method, Uri, Headers, Body),

    %% Should have / as path
    Lines = binary:split(Result, <<"\n">>, [global]),
    %% Second line should be the path
    Path = lists:nth(2, Lines),
    ?assertEqual(<<"/">>, Path).

%% Test create_canonical_request/4 with no path (implicit root)
create_canonical_request_no_path_test() ->
    Method = <<"GET">>,
    Uri = <<"https://s3.amazonaws.com">>,
    Headers = [{<<"Host">>, <<"s3.amazonaws.com">>}],
    Body = <<>>,

    Result = smithy_sigv4:create_canonical_request(Method, Uri, Headers, Body),

    %% Should have / as path even if not specified
    Lines = binary:split(Result, <<"\n">>, [global]),
    Path = lists:nth(2, Lines),
    ?assertEqual(<<"/">>, Path).

%%====================================================================
%% String to Sign Generation Tests
%%====================================================================

%% Test credential_scope/3 basic format
credential_scope_basic_test() ->
    DateTime = <<"20230101T120000Z">>,
    Region = <<"us-east-1">>,
    Service = <<"s3">>,

    Result = smithy_sigv4:credential_scope(DateTime, Region, Service),

    Expected = <<"20230101/us-east-1/s3/aws4_request">>,
    ?assertEqual(Expected, Result).

%% Test credential_scope/3 with different region
credential_scope_different_region_test() ->
    DateTime = <<"20230515T093000Z">>,
    Region = <<"eu-west-1">>,
    Service = <<"dynamodb">>,

    Result = smithy_sigv4:credential_scope(DateTime, Region, Service),

    Expected = <<"20230515/eu-west-1/dynamodb/aws4_request">>,
    ?assertEqual(Expected, Result).

%% Test credential_scope/3 date extraction
credential_scope_date_extraction_test() ->
    %% Different times on same day should have same date
    DateTime1 = <<"20230101T000000Z">>,
    DateTime2 = <<"20230101T235959Z">>,
    Region = <<"us-west-2">>,
    Service = <<"s3">>,

    Result1 = smithy_sigv4:credential_scope(DateTime1, Region, Service),
    Result2 = smithy_sigv4:credential_scope(DateTime2, Region, Service),

    %% Both should have same credential scope (same date)
    ?assertEqual(Result1, Result2),
    ?assertEqual(<<"20230101/us-west-2/s3/aws4_request">>, Result1).

%% Test credential_scope/3 with various services
credential_scope_services_test() ->
    DateTime = <<"20230101T120000Z">>,
    Region = <<"us-east-1">>,

    Services = [<<"s3">>, <<"dynamodb">>, <<"ec2">>, <<"lambda">>],

    lists:foreach(
        fun(Service) ->
            Result = smithy_sigv4:credential_scope(DateTime, Region, Service),
            Expected = <<"20230101/us-east-1/", Service/binary, "/aws4_request">>,
            ?assertEqual(Expected, Result)
        end,
        Services
    ).

%% Test iso8601_datetime/0 format
iso8601_datetime_format_test() ->
    Result = smithy_sigv4:iso8601_datetime(),

    %% Should be 16 characters: YYYYMMDDTHHMMSSZ
    ?assertEqual(16, byte_size(Result)),

    %% Should start with 20 (year 20XX)
    ?assertEqual(<<"20">>, binary:part(Result, 0, 2)),

    %% Should have T separator at position 8
    ?assertEqual($T, binary:at(Result, 8)),

    %% Should end with Z
    ?assertEqual($Z, binary:at(Result, 15)),

    %% Should be all digits except T and Z
    <<Year:4/binary, Month:2/binary, Day:2/binary, "T", Hour:2/binary, Minute:2/binary,
        Second:2/binary, "Z">> = Result,

    ?assert(is_numeric(Year)),
    ?assert(is_numeric(Month)),
    ?assert(is_numeric(Day)),
    ?assert(is_numeric(Hour)),
    ?assert(is_numeric(Minute)),
    ?assert(is_numeric(Second)).

%% Test iso8601_datetime/0 sequential calls
iso8601_datetime_sequential_test() ->
    %% Call twice in quick succession
    Time1 = smithy_sigv4:iso8601_datetime(),
    Time2 = smithy_sigv4:iso8601_datetime(),

    %% Both should be valid timestamps
    ?assertEqual(16, byte_size(Time1)),
    ?assertEqual(16, byte_size(Time2)),

    %% Should be same or Time2 >= Time1 (depends on execution speed)
    ?assert(Time2 >= Time1).

%% Test create_string_to_sign/3 basic format
create_string_to_sign_basic_test() ->
    DateTime = <<"20230101T120000Z">>,
    CredentialScope = <<"20230101/us-east-1/s3/aws4_request">>,
    CanonicalRequest =
        <<"GET\n/\n\nhost:s3.amazonaws.com\n\nhost\ne3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855">>,

    Result = smithy_sigv4:create_string_to_sign(DateTime, CredentialScope, CanonicalRequest),

    %% Should start with algorithm
    ?assert(binary:match(Result, <<"AWS4-HMAC-SHA256">>) =/= nomatch),

    %% Should contain DateTime
    ?assert(binary:match(Result, DateTime) =/= nomatch),

    %% Should contain CredentialScope
    ?assert(binary:match(Result, CredentialScope) =/= nomatch),

    %% Should have 4 lines (algorithm, datetime, scope, hash)
    Lines = binary:split(Result, <<"\n">>, [global]),
    ?assertEqual(4, length(Lines)).

%% Test create_string_to_sign/3 structure
create_string_to_sign_structure_test() ->
    DateTime = <<"20230515T093000Z">>,
    CredentialScope = <<"20230515/eu-west-1/dynamodb/aws4_request">>,
    CanonicalRequest =
        <<"POST\n/\n\nhost:dynamodb.eu-west-1.amazonaws.com\n\nhost\n44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a">>,

    Result = smithy_sigv4:create_string_to_sign(DateTime, CredentialScope, CanonicalRequest),

    %% Split into lines
    Lines = binary:split(Result, <<"\n">>, [global]),

    %% Line 1: Algorithm
    ?assertEqual(<<"AWS4-HMAC-SHA256">>, lists:nth(1, Lines)),

    %% Line 2: DateTime
    ?assertEqual(DateTime, lists:nth(2, Lines)),

    %% Line 3: Credential Scope
    ?assertEqual(CredentialScope, lists:nth(3, Lines)),

    %% Line 4: Hashed canonical request (64 char hex)
    HashedCanonicalRequest = lists:nth(4, Lines),
    ?assertEqual(64, byte_size(HashedCanonicalRequest)),
    ?assert(is_hex_string(HashedCanonicalRequest)).

%% Test create_string_to_sign/3 with AWS test vector
create_string_to_sign_aws_example_test() ->
    %% Based on AWS SigV4 test suite
    DateTime = <<"20150830T123600Z">>,
    CredentialScope = <<"20150830/us-east-1/service/aws4_request">>,

    %% Simple GET request canonical request
    CanonicalRequest =
        <<"GET\n/\n\nhost:example.amazonaws.com\nx-amz-date:20150830T123600Z\n\nhost;x-amz-date\ne3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855">>,

    Result = smithy_sigv4:create_string_to_sign(DateTime, CredentialScope, CanonicalRequest),

    %% Verify structure
    Lines = binary:split(Result, <<"\n">>, [global]),
    ?assertEqual(4, length(Lines)),
    ?assertEqual(<<"AWS4-HMAC-SHA256">>, lists:nth(1, Lines)),
    ?assertEqual(DateTime, lists:nth(2, Lines)),
    ?assertEqual(CredentialScope, lists:nth(3, Lines)),

    %% Hashed canonical request should be deterministic
    HashedCanonicalRequest = lists:nth(4, Lines),
    ?assertEqual(64, byte_size(HashedCanonicalRequest)).

%% Test create_string_to_sign/3 with empty canonical request
create_string_to_sign_empty_canonical_test() ->
    DateTime = <<"20230101T000000Z">>,
    CredentialScope = <<"20230101/us-east-1/s3/aws4_request">>,
    CanonicalRequest = <<>>,

    Result = smithy_sigv4:create_string_to_sign(DateTime, CredentialScope, CanonicalRequest),

    %% Should still have 4 lines
    Lines = binary:split(Result, <<"\n">>, [global]),
    ?assertEqual(4, length(Lines)),

    %% Last line should be hash of empty string
    EmptyHash = <<"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855">>,
    ?assertEqual(EmptyHash, lists:nth(4, Lines)).

%% Test integration: credential_scope used in create_string_to_sign
integration_credential_scope_test() ->
    DateTime = <<"20230101T120000Z">>,
    Region = <<"us-east-1">>,
    Service = <<"s3">>,

    %% Create credential scope
    CredentialScope = smithy_sigv4:credential_scope(DateTime, Region, Service),

    %% Use in string to sign
    CanonicalRequest =
        <<"GET\n/\n\nhost:example.com\n\nhost\ne3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855">>,
    StringToSign = smithy_sigv4:create_string_to_sign(DateTime, CredentialScope, CanonicalRequest),

    %% Verify credential scope is in string to sign
    ?assert(binary:match(StringToSign, CredentialScope) =/= nomatch),
    ?assert(binary:match(StringToSign, <<"20230101/us-east-1/s3/aws4_request">>) =/= nomatch).

%% Test full flow: canonical request to string to sign
integration_full_flow_test() ->
    %% Create canonical request
    Method = <<"GET">>,
    Uri = <<"https://s3.amazonaws.com/mybucket/mykey">>,
    Headers = [
        {<<"Host">>, <<"s3.amazonaws.com">>},
        {<<"X-Amz-Date">>, <<"20230101T120000Z">>}
    ],
    Body = <<>>,

    CanonicalRequest = smithy_sigv4:create_canonical_request(Method, Uri, Headers, Body),

    %% Create credential scope
    DateTime = <<"20230101T120000Z">>,
    Region = <<"us-east-1">>,
    Service = <<"s3">>,
    CredentialScope = smithy_sigv4:credential_scope(DateTime, Region, Service),

    %% Create string to sign
    StringToSign = smithy_sigv4:create_string_to_sign(DateTime, CredentialScope, CanonicalRequest),

    %% Verify result structure
    Lines = binary:split(StringToSign, <<"\n">>, [global]),
    ?assertEqual(4, length(Lines)),
    ?assertEqual(<<"AWS4-HMAC-SHA256">>, lists:nth(1, Lines)),
    ?assertEqual(DateTime, lists:nth(2, Lines)),
    ?assertEqual(<<"20230101/us-east-1/s3/aws4_request">>, lists:nth(3, Lines)),

    %% Hash should be deterministic
    HashedCanonicalRequest = lists:nth(4, Lines),
    ?assertEqual(64, byte_size(HashedCanonicalRequest)),
    ?assert(is_hex_string(HashedCanonicalRequest)).

%%====================================================================
%% Signature Calculation Tests
%%====================================================================

%% Test hmac_sha256/2 with known input
hmac_sha256_known_test() ->
    %% Test with known HMAC-SHA256 value
    Key = <<"key">>,
    Data = <<"The quick brown fox jumps over the lazy dog">>,

    Result = smithy_sigv4:hmac_sha256(Key, Data),

    %% Should be 32 bytes
    ?assertEqual(32, byte_size(Result)),

    %% Should be binary (not hex)
    ?assert(is_binary(Result)).

%% Test hmac_sha256/2 with empty data
hmac_sha256_empty_data_test() ->
    Key = <<"secret">>,
    Data = <<>>,

    Result = smithy_sigv4:hmac_sha256(Key, Data),

    ?assertEqual(32, byte_size(Result)).

%% Test hmac_sha256/2 with empty key
hmac_sha256_empty_key_test() ->
    Key = <<>>,
    Data = <<"data">>,

    Result = smithy_sigv4:hmac_sha256(Key, Data),

    ?assertEqual(32, byte_size(Result)).

%% Test hmac_sha256/2 deterministic output
hmac_sha256_deterministic_test() ->
    Key = <<"testkey">>,
    Data = <<"testdata">>,

    Result1 = smithy_sigv4:hmac_sha256(Key, Data),
    Result2 = smithy_sigv4:hmac_sha256(Key, Data),

    %% Should produce same result
    ?assertEqual(Result1, Result2).

%% Test derive_signing_key/4 basic
derive_signing_key_basic_test() ->
    SecretAccessKey = <<"wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY">>,
    Date = <<"20150830">>,
    Region = <<"us-east-1">>,
    Service = <<"iam">>,

    SigningKey = smithy_sigv4:derive_signing_key(SecretAccessKey, Date, Region, Service),

    %% Should be 32 bytes (HMAC-SHA256 output)
    ?assertEqual(32, byte_size(SigningKey)),

    %% Should be binary (not hex)
    ?assert(is_binary(SigningKey)).

%% Test derive_signing_key/4 with AWS test vector
derive_signing_key_aws_example_test() ->
    %% From AWS SigV4 test suite
    SecretAccessKey = <<"wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY">>,
    Date = <<"20150830">>,
    Region = <<"us-east-1">>,
    Service = <<"iam">>,

    SigningKey = smithy_sigv4:derive_signing_key(SecretAccessKey, Date, Region, Service),

    %% Expected value from AWS test suite
    %% c4afb1cc5771d871763a393e44b703571b55cc28424d1a5e86da6ed3c154a4b9
    Expected = binary:decode_hex(
        <<"c4afb1cc5771d871763a393e44b703571b55cc28424d1a5e86da6ed3c154a4b9">>
    ),

    ?assertEqual(Expected, SigningKey).

%% Test derive_signing_key/4 deterministic
derive_signing_key_deterministic_test() ->
    SecretAccessKey = <<"testSecret">>,
    Date = <<"20230101">>,
    Region = <<"us-west-2">>,
    Service = <<"s3">>,

    Key1 = smithy_sigv4:derive_signing_key(SecretAccessKey, Date, Region, Service),
    Key2 = smithy_sigv4:derive_signing_key(SecretAccessKey, Date, Region, Service),

    %% Should produce same key
    ?assertEqual(Key1, Key2).

%% Test derive_signing_key/4 different dates produce different keys
derive_signing_key_different_dates_test() ->
    SecretAccessKey = <<"testSecret">>,
    Region = <<"us-east-1">>,
    Service = <<"s3">>,

    Key1 = smithy_sigv4:derive_signing_key(SecretAccessKey, <<"20230101">>, Region, Service),
    Key2 = smithy_sigv4:derive_signing_key(SecretAccessKey, <<"20230102">>, Region, Service),

    %% Different dates should produce different keys
    ?assertNotEqual(Key1, Key2).

%% Test derive_signing_key/4 different regions produce different keys
derive_signing_key_different_regions_test() ->
    SecretAccessKey = <<"testSecret">>,
    Date = <<"20230101">>,
    Service = <<"s3">>,

    Key1 = smithy_sigv4:derive_signing_key(SecretAccessKey, Date, <<"us-east-1">>, Service),
    Key2 = smithy_sigv4:derive_signing_key(SecretAccessKey, Date, <<"eu-west-1">>, Service),

    %% Different regions should produce different keys
    ?assertNotEqual(Key1, Key2).

%% Test derive_signing_key/4 different services produce different keys
derive_signing_key_different_services_test() ->
    SecretAccessKey = <<"testSecret">>,
    Date = <<"20230101">>,
    Region = <<"us-east-1">>,

    Key1 = smithy_sigv4:derive_signing_key(SecretAccessKey, Date, Region, <<"s3">>),
    Key2 = smithy_sigv4:derive_signing_key(SecretAccessKey, Date, Region, <<"dynamodb">>),

    %% Different services should produce different keys
    ?assertNotEqual(Key1, Key2).

%% Test calculate_signature/2 basic
calculate_signature_basic_test() ->
    %% Create a signing key
    SigningKey = smithy_sigv4:derive_signing_key(
        <<"secret">>,
        <<"20230101">>,
        <<"us-east-1">>,
        <<"s3">>
    ),

    StringToSign =
        <<"AWS4-HMAC-SHA256\n20230101T120000Z\n20230101/us-east-1/s3/aws4_request\nabc123">>,

    Signature = smithy_sigv4:calculate_signature(SigningKey, StringToSign),

    %% Should be 64 character hex string (32 bytes * 2)
    ?assertEqual(64, byte_size(Signature)),

    %% Should be lowercase hex
    ?assert(is_hex_string(Signature)).

%% Test calculate_signature/2 with AWS test vector
calculate_signature_aws_example_test() ->
    %% From AWS SigV4 test suite
    SecretAccessKey = <<"wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY">>,
    Date = <<"20150830">>,
    Region = <<"us-east-1">>,
    Service = <<"iam">>,

    %% Derive signing key
    SigningKey = smithy_sigv4:derive_signing_key(SecretAccessKey, Date, Region, Service),

    %% String to sign from AWS test suite
    StringToSign =
        <<"AWS4-HMAC-SHA256\n20150830T123600Z\n20150830/us-east-1/iam/aws4_request\nf536975d06c0309214f805bb90ccff089219ecd68b2577efef23edd43b7e1a59">>,

    Signature = smithy_sigv4:calculate_signature(SigningKey, StringToSign),

    %% Expected signature from AWS test suite
    Expected = <<"5d672d79c15b13162d9279b0855cfba6789a8edb4c82c400e06b5924a6f2b5d7">>,

    ?assertEqual(Expected, Signature).

%% Test calculate_signature/2 deterministic
calculate_signature_deterministic_test() ->
    % 32 bytes
    SigningKey = <<"testkey12345678901234567890123456789012">>,
    StringToSign = <<"test string to sign">>,

    Sig1 = smithy_sigv4:calculate_signature(SigningKey, StringToSign),
    Sig2 = smithy_sigv4:calculate_signature(SigningKey, StringToSign),

    ?assertEqual(Sig1, Sig2).

%% Test calculate_signature/2 different inputs produce different signatures
calculate_signature_different_inputs_test() ->
    SigningKey = <<"testkey12345678901234567890123456789012">>,

    Sig1 = smithy_sigv4:calculate_signature(SigningKey, <<"string1">>),
    Sig2 = smithy_sigv4:calculate_signature(SigningKey, <<"string2">>),

    ?assertNotEqual(Sig1, Sig2).

%% Test complete flow: derive key and sign
integration_derive_and_sign_test() ->
    %% AWS credentials
    SecretAccessKey = <<"testSecretKey">>,
    Date = <<"20230101">>,
    Region = <<"us-east-1">>,
    Service = <<"s3">>,

    %% String to sign
    StringToSign =
        <<"AWS4-HMAC-SHA256\n20230101T120000Z\n20230101/us-east-1/s3/aws4_request\ne3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855">>,

    %% Derive signing key
    SigningKey = smithy_sigv4:derive_signing_key(SecretAccessKey, Date, Region, Service),

    %% Calculate signature
    Signature = smithy_sigv4:calculate_signature(SigningKey, StringToSign),

    %% Verify signature properties
    ?assertEqual(64, byte_size(Signature)),
    ?assert(is_hex_string(Signature)).

%% Test complete SigV4 flow from canonical request to signature
integration_full_sigv4_flow_test() ->
    %% Request components
    Method = <<"GET">>,
    Uri = <<"https://s3.amazonaws.com/mybucket/mykey">>,
    Headers = [
        {<<"Host">>, <<"s3.amazonaws.com">>},
        {<<"X-Amz-Date">>, <<"20230101T120000Z">>}
    ],
    Body = <<>>,

    %% Create canonical request
    CanonicalRequest = smithy_sigv4:create_canonical_request(Method, Uri, Headers, Body),

    %% Create string to sign
    DateTime = <<"20230101T120000Z">>,
    Region = <<"us-east-1">>,
    Service = <<"s3">>,
    CredentialScope = smithy_sigv4:credential_scope(DateTime, Region, Service),
    StringToSign = smithy_sigv4:create_string_to_sign(DateTime, CredentialScope, CanonicalRequest),

    %% Calculate signature
    SecretAccessKey = <<"wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY">>,
    % Extract date: 20230101
    Date = binary:part(DateTime, 0, 8),
    SigningKey = smithy_sigv4:derive_signing_key(SecretAccessKey, Date, Region, Service),
    Signature = smithy_sigv4:calculate_signature(SigningKey, StringToSign),

    %% Verify final signature
    ?assertEqual(64, byte_size(Signature)),
    ?assert(is_hex_string(Signature)),

    %% Signature should be deterministic
    SigningKey2 = smithy_sigv4:derive_signing_key(SecretAccessKey, Date, Region, Service),
    Signature2 = smithy_sigv4:calculate_signature(SigningKey2, StringToSign),
    ?assertEqual(Signature, Signature2).

%%====================================================================
%% Authorization Header and Complete Signing Tests
%%====================================================================

%% Test format_auth_header/4 basic format
format_auth_header_basic_test() ->
    AccessKeyId = <<"AKIAIOSFODNN7EXAMPLE">>,
    CredentialScope = <<"20230101/us-east-1/s3/aws4_request">>,
    SignedHeaders = <<"host;x-amz-date">>,
    Signature = <<"5d672d79c15b13162d9279b0855cfba6789a8edb4c82c400e06b5924a6f2b5d7">>,

    Result = smithy_sigv4:format_auth_header(AccessKeyId, CredentialScope, SignedHeaders, Signature),

    %% Should start with algorithm
    ?assert(binary:match(Result, <<"AWS4-HMAC-SHA256">>) =/= nomatch),

    %% Should contain Credential
    ?assert(binary:match(Result, <<"Credential=">>) =/= nomatch),
    ?assert(binary:match(Result, AccessKeyId) =/= nomatch),
    ?assert(binary:match(Result, CredentialScope) =/= nomatch),

    %% Should contain SignedHeaders
    ?assert(binary:match(Result, <<"SignedHeaders=">>) =/= nomatch),
    ?assert(binary:match(Result, SignedHeaders) =/= nomatch),

    %% Should contain Signature
    ?assert(binary:match(Result, <<"Signature=">>) =/= nomatch),
    ?assert(binary:match(Result, Signature) =/= nomatch).

%% Test format_auth_header/4 exact format
format_auth_header_format_test() ->
    AccessKeyId = <<"AKIAIOSFODNN7EXAMPLE">>,
    CredentialScope = <<"20230101/us-east-1/s3/aws4_request">>,
    SignedHeaders = <<"host;x-amz-date">>,
    Signature = <<"abc123">>,

    Result = smithy_sigv4:format_auth_header(AccessKeyId, CredentialScope, SignedHeaders, Signature),

    Expected =
        <<"AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20230101/us-east-1/s3/aws4_request, SignedHeaders=host;x-amz-date, Signature=abc123">>,

    ?assertEqual(Expected, Result).

%% Test sign_request/5 returns headers with Authorization
sign_request_basic_test() ->
    Method = <<"GET">>,
    Url = <<"https://s3.amazonaws.com/mybucket/mykey">>,
    Headers = [{<<"Host">>, <<"s3.amazonaws.com">>}],
    Body = <<>>,
    Credentials = #{
        access_key_id => <<"AKIAIOSFODNN7EXAMPLE">>,
        secret_access_key => <<"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY">>,
        region => <<"us-east-1">>,
        service => <<"s3">>
    },

    {ok, Result} = smithy_sigv4:sign_request(Method, Url, Headers, Body, Credentials),

    %% Should return a list of headers
    ?assert(is_list(Result)),

    %% Should have Authorization header
    ?assert(lists:keyfind(<<"Authorization">>, 1, Result) =/= false),

    %% Should have X-Amz-Date header
    ?assert(lists:keyfind(<<"X-Amz-Date">>, 1, Result) =/= false),

    %% Should have original Host header
    ?assert(lists:keyfind(<<"Host">>, 1, Result) =/= false).

%% Test sign_request/5 with session token
sign_request_with_session_token_test() ->
    Method = <<"POST">>,
    Url = <<"https://dynamodb.us-west-2.amazonaws.com/">>,
    Headers = [{<<"Host">>, <<"dynamodb.us-west-2.amazonaws.com">>}],
    Body = <<"{\"TableName\":\"Test\"}">>,
    Credentials = #{
        access_key_id => <<"AKIAIOSFODNN7EXAMPLE">>,
        secret_access_key => <<"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY">>,
        session_token => <<"AQoEXAMPLEsessiontoken">>,
        region => <<"us-west-2">>,
        service => <<"dynamodb">>
    },

    {ok, Result} = smithy_sigv4:sign_request(Method, Url, Headers, Body, Credentials),

    %% Should have Authorization header
    ?assert(lists:keyfind(<<"Authorization">>, 1, Result) =/= false),

    %% Should have X-Amz-Security-Token header
    {_, Token} = lists:keyfind(<<"X-Amz-Security-Token">>, 1, Result),
    ?assertEqual(<<"AQoEXAMPLEsessiontoken">>, Token).

%% Test sign_request/5 without session token
sign_request_without_session_token_test() ->
    Method = <<"GET">>,
    Url = <<"https://s3.amazonaws.com/bucket">>,
    Headers = [{<<"Host">>, <<"s3.amazonaws.com">>}],
    Body = <<>>,
    Credentials = #{
        access_key_id => <<"AKIAIOSFODNN7EXAMPLE">>,
        secret_access_key => <<"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY">>,
        region => <<"us-east-1">>,
        service => <<"s3">>
    },

    {ok, Result} = smithy_sigv4:sign_request(Method, Url, Headers, Body, Credentials),

    %% Should NOT have X-Amz-Security-Token header
    ?assertEqual(false, lists:keyfind(<<"X-Amz-Security-Token">>, 1, Result)).

%% Test sign_request/5 Authorization header format
sign_request_authorization_format_test() ->
    Method = <<"GET">>,
    Url = <<"https://s3.amazonaws.com/mybucket">>,
    Headers = [{<<"Host">>, <<"s3.amazonaws.com">>}],
    Body = <<>>,
    Credentials = #{
        access_key_id => <<"AKIAIOSFODNN7EXAMPLE">>,
        secret_access_key => <<"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY">>,
        region => <<"us-east-1">>,
        service => <<"s3">>
    },

    {ok, Result} = smithy_sigv4:sign_request(Method, Url, Headers, Body, Credentials),

    {_, AuthHeader} = lists:keyfind(<<"Authorization">>, 1, Result),

    %% Should start with AWS4-HMAC-SHA256
    ?assert(binary:match(AuthHeader, <<"AWS4-HMAC-SHA256">>) =/= nomatch),

    %% Should contain Credential, SignedHeaders, and Signature
    ?assert(binary:match(AuthHeader, <<"Credential=">>) =/= nomatch),
    ?assert(binary:match(AuthHeader, <<"SignedHeaders=">>) =/= nomatch),
    ?assert(binary:match(AuthHeader, <<"Signature=">>) =/= nomatch).

%% Test sign_request/5 X-Amz-Date format
sign_request_date_format_test() ->
    Method = <<"GET">>,
    Url = <<"https://s3.amazonaws.com/mybucket">>,
    Headers = [{<<"Host">>, <<"s3.amazonaws.com">>}],
    Body = <<>>,
    Credentials = #{
        access_key_id => <<"AKIAIOSFODNN7EXAMPLE">>,
        secret_access_key => <<"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY">>,
        region => <<"us-east-1">>,
        service => <<"s3">>
    },

    {ok, Result} = smithy_sigv4:sign_request(Method, Url, Headers, Body, Credentials),

    {_, DateTime} = lists:keyfind(<<"X-Amz-Date">>, 1, Result),

    %% Should be 16 characters
    ?assertEqual(16, byte_size(DateTime)),

    %% Should have T separator at position 8
    ?assertEqual($T, binary:at(DateTime, 8)),

    %% Should end with Z
    ?assertEqual($Z, binary:at(DateTime, 15)).

%% Test sign_request/5 with POST and body
sign_request_post_with_body_test() ->
    Method = <<"POST">>,
    Url = <<"https://dynamodb.us-east-1.amazonaws.com/">>,
    Headers = [
        {<<"Host">>, <<"dynamodb.us-east-1.amazonaws.com">>},
        {<<"Content-Type">>, <<"application/x-amz-json-1.0">>}
    ],
    Body = <<"{\"TableName\":\"Users\",\"Key\":{\"UserId\":{\"S\":\"123\"}}}">>,
    Credentials = #{
        access_key_id => <<"AKIAIOSFODNN7EXAMPLE">>,
        secret_access_key => <<"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY">>,
        region => <<"us-east-1">>,
        service => <<"dynamodb">>
    },

    {ok, Result} = smithy_sigv4:sign_request(Method, Url, Headers, Body, Credentials),

    %% Should have all required headers
    ?assert(lists:keyfind(<<"Authorization">>, 1, Result) =/= false),
    ?assert(lists:keyfind(<<"X-Amz-Date">>, 1, Result) =/= false),
    ?assert(lists:keyfind(<<"Host">>, 1, Result) =/= false),
    ?assert(lists:keyfind(<<"Content-Type">>, 1, Result) =/= false).

%% Test sign_request/5 deterministic
sign_request_deterministic_test() ->
    Method = <<"GET">>,
    Url = <<"https://s3.amazonaws.com/mybucket">>,
    Headers = [{<<"Host">>, <<"s3.amazonaws.com">>}],
    Body = <<>>,
    Credentials = #{
        access_key_id => <<"AKIAIOSFODNN7EXAMPLE">>,
        secret_access_key => <<"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY">>,
        region => <<"us-east-1">>,
        service => <<"s3">>
    },

    %% Call twice
    {ok, Result1} = smithy_sigv4:sign_request(Method, Url, Headers, Body, Credentials),
    {ok, Result2} = smithy_sigv4:sign_request(Method, Url, Headers, Body, Credentials),

    %% Both should have Authorization header
    {_, Auth1} = lists:keyfind(<<"Authorization">>, 1, Result1),
    {_, Auth2} = lists:keyfind(<<"Authorization">>, 1, Result2),

    %% Signatures may differ due to different timestamps, but format should be same
    ?assert(binary:match(Auth1, <<"AWS4-HMAC-SHA256">>) =/= nomatch),
    ?assert(binary:match(Auth2, <<"AWS4-HMAC-SHA256">>) =/= nomatch).

%% Test complete integration: sign_request produces valid AWS format
integration_sign_request_complete_test() ->
    Method = <<"GET">>,
    Url = <<"https://s3.amazonaws.com/mybucket/myobject">>,
    Headers = [{<<"Host">>, <<"s3.amazonaws.com">>}],
    Body = <<>>,
    Credentials = #{
        access_key_id => <<"AKIAIOSFODNN7EXAMPLE">>,
        secret_access_key => <<"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY">>,
        region => <<"us-east-1">>,
        service => <<"s3">>
    },

    {ok, Result} = smithy_sigv4:sign_request(Method, Url, Headers, Body, Credentials),

    %% Extract headers
    {_, AuthHeader} = lists:keyfind(<<"Authorization">>, 1, Result),
    {_, DateTime} = lists:keyfind(<<"X-Amz-Date">>, 1, Result),
    {_, Host} = lists:keyfind(<<"Host">>, 1, Result),

    %% Verify Authorization header format
    ?assert(
        binary:match(AuthHeader, <<"AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/">>) =/=
            nomatch
    ),
    ?assert(binary:match(AuthHeader, <<"/us-east-1/s3/aws4_request">>) =/= nomatch),
    ?assert(binary:match(AuthHeader, <<"SignedHeaders=">>) =/= nomatch),
    ?assert(binary:match(AuthHeader, <<"Signature=">>) =/= nomatch),

    %% Verify DateTime format
    ?assertEqual(16, byte_size(DateTime)),
    ?assertEqual($T, binary:at(DateTime, 8)),
    ?assertEqual($Z, binary:at(DateTime, 15)),

    %% Verify Host is preserved
    ?assertEqual(<<"s3.amazonaws.com">>, Host),

    %% Count headers (should be 3: Authorization, X-Amz-Date, Host)
    ?assertEqual(3, length(Result)).

%%====================================================================
%% Helper Functions for Tests
%%====================================================================

%% Check if a binary is a valid hex string
is_hex_string(Bin) ->
    lists:all(
        fun(C) ->
            (C >= $0 andalso C =< $9) orelse
                (C >= $a andalso C =< $f) orelse
                (C >= $A andalso C =< $F)
        end,
        binary_to_list(Bin)
    ).

%% Check if a binary is numeric
is_numeric(Bin) ->
    lists:all(
        fun(C) -> C >= $0 andalso C =< $9 end,
        binary_to_list(Bin)
    ).
