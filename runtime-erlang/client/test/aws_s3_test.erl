-module(aws_s3_test).

-include_lib("eunit/include/eunit.hrl").

%% ============================================================================
%% Path Style Detection Tests
%% ============================================================================

is_localhost_test() ->
    ?assert(aws_s3:is_localhost(<<"http://localhost:5050">>)),
    ?assert(aws_s3:is_localhost(<<"http://localhost">>)),
    ?assert(aws_s3:is_localhost(<<"https://localhost:4566">>)).

is_localhost_ip_test() ->
    ?assert(aws_s3:is_localhost(<<"http://127.0.0.1:5050">>)),
    ?assert(aws_s3:is_localhost(<<"http://127.0.0.1">>)),
    ?assert(aws_s3:is_localhost(<<"https://192.168.1.1:9000">>)).

is_not_localhost_test() ->
    ?assertNot(aws_s3:is_localhost(<<"https://s3.amazonaws.com">>)),
    ?assertNot(aws_s3:is_localhost(<<"https://s3.us-east-1.amazonaws.com">>)),
    ?assertNot(aws_s3:is_localhost(<<"https://bucket.s3.amazonaws.com">>)).

is_docker_host_test() ->
    ?assert(aws_s3:is_localhost(<<"http://moto:5050">>)),
    ?assert(aws_s3:is_localhost(<<"http://localstack:4566">>)),
    ?assert(aws_s3:is_localhost(<<"http://minio:9000">>)).

use_path_style_localhost_test() ->
    ?assert(aws_s3:use_path_style(<<"http://localhost:5050">>)),
    ?assert(aws_s3:use_path_style(<<"http://127.0.0.1:9000">>)),
    ?assert(aws_s3:use_path_style(<<"http://moto:5050">>)).

use_path_style_port_test() ->
    %% Any endpoint with a port should use path style
    ?assert(aws_s3:use_path_style(<<"https://s3.amazonaws.com:443">>)),
    ?assert(aws_s3:use_path_style(<<"http://custom-s3:9000">>)).

use_virtual_hosted_aws_test() ->
    %% Standard AWS endpoints should use virtual-hosted style
    ?assertNot(aws_s3:use_path_style(<<"https://s3.amazonaws.com">>)),
    ?assertNot(aws_s3:use_path_style(<<"https://s3.us-east-1.amazonaws.com">>)),
    ?assertNot(aws_s3:use_path_style(<<"https://s3.eu-west-1.amazonaws.com">>)).

%% ============================================================================
%% Path Style URL Building Tests
%% ============================================================================

build_path_style_basic_test() ->
    Client = #{endpoint => <<"http://localhost:5050">>},
    Url = aws_s3:build_url(Client, <<"my-bucket">>, <<"my-key">>, <<"">>),
    ?assertEqual(<<"http://localhost:5050/my-bucket/my-key">>, Url).

build_path_style_with_query_test() ->
    Client = #{endpoint => <<"http://localhost:5050">>},
    Url = aws_s3:build_url(Client, <<"my-bucket">>, <<"my-key">>, <<"?versionId=123">>),
    ?assertEqual(<<"http://localhost:5050/my-bucket/my-key?versionId=123">>, Url).

build_path_style_bucket_only_test() ->
    Client = #{endpoint => <<"http://localhost:5050">>},
    Url = aws_s3:build_url(Client, <<"my-bucket">>, <<>>, <<"">>),
    ?assertEqual(<<"http://localhost:5050/my-bucket">>, Url).

build_path_style_list_buckets_test() ->
    Client = #{endpoint => <<"http://localhost:5050">>},
    Url = aws_s3:build_url(Client, <<>>, <<>>, <<"">>),
    ?assertEqual(<<"http://localhost:5050/">>, Url).

build_path_style_moto_test() ->
    Client = #{endpoint => <<"http://moto:5050">>},
    Url = aws_s3:build_url(Client, <<"test-bucket">>, <<"path/to/object.txt">>, <<"">>),
    ?assertEqual(<<"http://moto:5050/test-bucket/path/to/object.txt">>, Url).

build_path_style_forced_test() ->
    %% Force path style even for AWS endpoint
    Client = #{endpoint => <<"https://s3.amazonaws.com">>},
    Url = aws_s3:build_url(Client, <<"my-bucket">>, <<"my-key">>, <<"">>, #{path_style => true}),
    ?assertEqual(<<"https://s3.amazonaws.com/my-bucket/my-key">>, Url).

%% ============================================================================
%% Virtual Hosted Style URL Building Tests
%% ============================================================================

build_virtual_hosted_basic_test() ->
    Client = #{endpoint => <<"https://s3.amazonaws.com">>, region => <<"us-east-1">>},
    Url = aws_s3:build_url(Client, <<"my-bucket">>, <<"my-key">>, <<"">>),
    ?assertEqual(<<"https://my-bucket.s3.us-east-1.amazonaws.com/my-key">>, Url).

build_virtual_hosted_with_query_test() ->
    Client = #{endpoint => <<"https://s3.amazonaws.com">>, region => <<"us-west-2">>},
    Url = aws_s3:build_url(Client, <<"my-bucket">>, <<"my-key">>, <<"?versionId=456">>),
    ?assertEqual(<<"https://my-bucket.s3.us-west-2.amazonaws.com/my-key?versionId=456">>, Url).

build_virtual_hosted_root_test() ->
    Client = #{endpoint => <<"https://s3.amazonaws.com">>, region => <<"eu-west-1">>},
    Url = aws_s3:build_url(Client, <<"my-bucket">>, <<>>, <<"">>),
    ?assertEqual(<<"https://my-bucket.s3.eu-west-1.amazonaws.com/">>, Url).

build_virtual_hosted_list_buckets_test() ->
    Client = #{endpoint => <<"https://s3.amazonaws.com">>, region => <<"us-east-1">>},
    Url = aws_s3:build_url(Client, <<>>, <<>>, <<"">>),
    %% ListBuckets has no bucket - uses original host
    ?assertEqual(<<"https://s3.amazonaws.com/">>, Url).

build_virtual_hosted_regional_endpoint_test() ->
    Client = #{endpoint => <<"https://s3.us-west-2.amazonaws.com">>, region => <<"us-west-2">>},
    Url = aws_s3:build_url(Client, <<"my-bucket">>, <<"my-key">>, <<"">>),
    ?assertEqual(<<"https://my-bucket.s3.us-west-2.amazonaws.com/my-key">>, Url).

build_virtual_hosted_disabled_test() ->
    %% Force virtual-hosted style
    Client = #{endpoint => <<"http://localhost:5050">>, region => <<"us-east-1">>},
    Url = aws_s3:build_url(Client, <<"my-bucket">>, <<"my-key">>, <<"">>, #{path_style => false}),
    %% When path_style is false, use virtual hosted even for localhost
    ?assertEqual(<<"http://my-bucket.localhost:5050/my-key">>, Url).

%% ============================================================================
%% Content-MD5 Tests
%% ============================================================================

calculate_content_md5_empty_test() ->
    MD5 = aws_s3:calculate_content_md5(<<>>),
    %% MD5 of empty string is 1B2M2Y8AsgTpgAmY7PhCfg==
    ?assertEqual(<<"1B2M2Y8AsgTpgAmY7PhCfg==">>, MD5).

calculate_content_md5_hello_test() ->
    MD5 = aws_s3:calculate_content_md5(<<"hello">>),
    %% MD5 of "hello" is XUFAKrxLKna5cZ2REBfFkg==
    ?assertEqual(<<"XUFAKrxLKna5cZ2REBfFkg==">>, MD5).

calculate_content_md5_json_test() ->
    Body = <<"{\"key\": \"value\"}">>,
    MD5 = aws_s3:calculate_content_md5(Body),
    %% Just verify it's a valid base64 string
    ?assert(byte_size(MD5) > 0),
    ?assert(is_binary(base64:decode(MD5))).

calculate_content_md5_binary_test() ->
    %% Binary data (simulating a file upload)
    Body = <<0, 1, 2, 3, 4, 5, 6, 7, 8, 9>>,
    MD5 = aws_s3:calculate_content_md5(Body),
    ?assert(byte_size(MD5) > 0).

%% ============================================================================
%% Edge Cases Tests
%% ============================================================================

trailing_slash_endpoint_test() ->
    Client = #{endpoint => <<"http://localhost:5050/">>},
    Url = aws_s3:build_url(Client, <<"my-bucket">>, <<"my-key">>, <<"">>),
    ?assertEqual(<<"http://localhost:5050/my-bucket/my-key">>, Url).

special_characters_in_key_test() ->
    Client = #{endpoint => <<"http://localhost:5050">>},
    %% Note: URL encoding should be done separately before calling build_url
    Url = aws_s3:build_url(Client, <<"my-bucket">>, <<"path/to/file with spaces.txt">>, <<"">>),
    ?assertEqual(<<"http://localhost:5050/my-bucket/path/to/file with spaces.txt">>, Url).

empty_bucket_with_key_test() ->
    %% This is an edge case - shouldn't normally happen
    Client = #{endpoint => <<"http://localhost:5050">>},
    Url = aws_s3:build_url(Client, <<>>, <<"some-key">>, <<"">>),
    ?assertEqual(<<"http://localhost:5050/some-key">>, Url).

%% ============================================================================
%% Real-World Scenario Tests
%% ============================================================================

moto_put_object_test() ->
    %% Simulate PutObject to Moto
    Client = #{
        endpoint => <<"http://moto:5050">>,
        region => <<"us-east-1">>
    },
    Url = aws_s3:build_url(Client, <<"test-bucket">>, <<"documents/report.pdf">>, <<"">>),
    ?assertEqual(<<"http://moto:5050/test-bucket/documents/report.pdf">>, Url).

localstack_get_object_test() ->
    %% Simulate GetObject from LocalStack
    Client = #{
        endpoint => <<"http://localstack:4566">>,
        region => <<"us-east-1">>
    },
    Url = aws_s3:build_url(Client, <<"data-bucket">>, <<"data.json">>, <<"?versionId=abc123">>),
    ?assertEqual(<<"http://localstack:4566/data-bucket/data.json?versionId=abc123">>, Url).

aws_production_test() ->
    %% Simulate production AWS S3 access
    Client = #{
        endpoint => <<"https://s3.amazonaws.com">>,
        region => <<"us-east-1">>
    },
    Url = aws_s3:build_url(Client, <<"prod-bucket">>, <<"logs/2024/01/app.log">>, <<"">>),
    ?assertEqual(<<"https://prod-bucket.s3.us-east-1.amazonaws.com/logs/2024/01/app.log">>, Url).

minio_test() ->
    %% Simulate MinIO access
    Client = #{
        endpoint => <<"http://minio:9000">>,
        region => <<"us-east-1">>
    },
    Url = aws_s3:build_url(Client, <<"local-bucket">>, <<"file.txt">>, <<"">>),
    ?assertEqual(<<"http://minio:9000/local-bucket/file.txt">>, Url).
