-module(s3_test).

-include_lib("eunit/include/eunit.hrl").
-include("amazon_s3_types.hrl").
-include("runtime_types.hrl").

-define(BUCKET_NAME, <<"smithy-beam-minimal-s3-erlang">>).

presign_url_test() ->
    Config = #{
        base_url => <<"http://localhost:4566">>,
        region => <<"us-east-1">>,
        endpoint_prefix => <<"s3">>,
        signing_name => <<"s3">>,
        s3_addressing_style => path_style,
        presign_expires => 3600,
        credentials => #{
            access_key_id => <<"AKIAIOSFODNN7EXAMPLE">>,
            secret_access_key => <<"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY">>
        }
    },
    Request = #http_request{
        method = <<"GET">>,
        path = <<"/my-bucket/object.txt">>,
        host = <<"localhost:4566">>
    },
    {ok, Url} = amazon_s3_presigner:presign_url(Config, get_object, Request),
    ?assertMatch(
        <<"https://localhost:4566/my-bucket/object.txt?", _/binary>>,
        Url
    ),
    ?assertMatch({_, _}, binary:match(Url, <<"X-Amz-Algorithm=AWS4-HMAC-SHA256">>)),
    ?assertMatch({_, _}, binary:match(Url, <<"X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F">>)),
    ?assertMatch({_, _}, binary:match(Url, <<"X-Amz-Expires=3600">>)),
    ?assertMatch({_, _}, binary:match(Url, <<"X-Amz-Signature=">>)).

list_buckets_test() ->
    Endpoint =
        case os:getenv("AWS_ENDPOINT") of
            false ->
                error("AWS_ENDPOINT must be set (run via make demo or export AWS_ENDPOINT)");
            "" ->
                error("AWS_ENDPOINT must be set (run via make demo or export AWS_ENDPOINT)");
            Value ->
                unicode:characters_to_binary(Value)
        end,
    Config = #{
        base_url => Endpoint,
        region => <<"us-east-1">>,
        endpoint_prefix => <<"s3">>,
        signing_name => <<"s3">>,
        s3_addressing_style => path_style,
        credentials => #{
            access_key_id => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    },
    Input = #list_buckets_input{},
    {ok, #list_buckets_output{buckets = Buckets}} =
        amazon_s3_client:list_buckets(Config, Input),
    ?assert(length(Buckets) > 0),
    BucketNames = [Name || #bucket{name = Name} <- Buckets, Name =/= undefined],
    ?assert(lists:member(?BUCKET_NAME, BucketNames)).
