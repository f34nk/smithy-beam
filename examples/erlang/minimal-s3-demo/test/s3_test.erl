-module(s3_test).

-include_lib("eunit/include/eunit.hrl").
-include("amazon_s3_types.hrl").

-define(BUCKET_NAME, <<"us-east-1-nonprod-configs">>).

list_buckets_test() ->
    Config = #{
        base_url => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
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
    {ok, #list_buckets_output{buckets = Buckets}} = amazon_s3_client:list_buckets(Config, Input),
    ?assert(length(Buckets) > 0),
    BucketNames = [Name || #bucket{name = Name} <- Buckets, Name =/= undefined],
    ?assert(lists:member(?BUCKET_NAME, BucketNames)).
