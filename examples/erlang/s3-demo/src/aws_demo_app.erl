-module(aws_demo_app).
-export([run/0]).

-define(BUCKET_NAME, <<"us-east-1-nonprod-configs">>).
-define(OBJECT_KEY, <<"configs/test.txt">>).
-define(EXPECTED_BODY, <<"Hello World">>).

run() ->
    io:format("~n=== Running S3 Client Application ===~n~n"),

    io:format("Creating S3 client...~n"),
    Config = #{
        endpoint => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        region => <<"us-east-1">>,
        credentials => #{
            access_key_id => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    },
    {ok, Client} = aws_s3_client:new(Config),
    io:format("Client created successfully~n~n"),

    %% List buckets
    ListInput = #{},
    case aws_s3_client:list_buckets(Client, ListInput, #{enable_retry => false}) of
        {ok, ListOutput} ->
            io:format("~nSUCCESS: ListBuckets returned successfully!~n"),
            #{<<"ListAllMyBucketsResult">> := #{<<"Buckets">> := BucketsData}} = ListOutput,
            BucketList =
                case BucketsData of
                    M when is_map(M) -> [M];
                    L when is_list(L) -> L;
                    _ -> []
                end,
            case BucketList of
                [] -> erlang:error({assertion_failed, empty_bucket_list});
                _  -> io:format("SUCCESS: Found ~p bucket(s)~n", [length(BucketList)])
            end,
            BucketNames = [maps:get(<<"Name">>, maps:get(<<"Bucket">>, B, #{}), <<>>) || B <- BucketList],
            case lists:member(?BUCKET_NAME, BucketNames) of
                true  -> io:format("SUCCESS: Bucket '~s' found~n", [?BUCKET_NAME]);
                false -> erlang:error({assertion_failed, {bucket_not_found, ?BUCKET_NAME}, {in, BucketNames}})
            end,
            lists:foreach(
                fun(Bucket0) ->
                    Bucket = maps:get(<<"Bucket">>, Bucket0, #{}),
                    Name = maps:get(<<"Name">>, Bucket, <<"unknown">>),
                    io:format("  - ~s~n", [Name])
                end,
                BucketList
            ),
            io:format("~n");
        {error, {aws_error, StatusCode, Code, Message}} ->
            erlang:error({list_buckets_failed, {StatusCode, Code, Message}});
        {error, ListError} ->
            erlang:error({list_buckets_failed, ListError})
    end,

    %% Put an object to S3 bucket
    PutInput = #{
        <<"Bucket">> => ?BUCKET_NAME,
        <<"Key">> => ?OBJECT_KEY,
        <<"Body">> => ?EXPECTED_BODY,
        <<"ContentType">> => <<"text/plain">>
    },
    case aws_s3_client:put_object(Client, PutInput) of
        {ok, PutOutput} ->
            io:format("~nSUCCESS: PutObject returned successfully!~n"),
            io:format("Response: ~p~n~n", [PutOutput]);
        {error, PutError} ->
            erlang:error({put_object_failed, PutError})
    end,

    %% List objects in the bucket
    ListObjectsInput = #{
        <<"Bucket">> => ?BUCKET_NAME,
        <<"Prefix">> => <<"configs/">>,
        <<"MaxKeys">> => 100,
        <<"Delimiter">> => <<"">>
    },
    case aws_s3_client:list_objects(Client, ListObjectsInput) of
        {ok, ListObjectsOutput} ->
            io:format("~nSUCCESS: ListObjects returned successfully!~n"),
            ListBucketResult = maps:get(<<"ListBucketResult">>, ListObjectsOutput, #{}),
            ObjectsData =
                case maps:get(<<"Contents">>, ListBucketResult, []) of
                    M3 when is_map(M3) -> [M3];
                    L3 when is_list(L3) -> L3;
                    _ -> []
                end,
            ObjectList =
                case ObjectsData of
                    M2 when is_map(M2) -> [M2];
                    L2 when is_list(L2) -> L2;
                    _ -> []
                end,
            case ObjectList of
                [] -> erlang:error({assertion_failed, empty_object_list});
                _  -> io:format("SUCCESS: Found ~p object(s)~n", [length(ObjectList)])
            end,
            ObjectKeys = [maps:get(<<"Key">>, Obj, maps:get(<<"key">>, Obj, <<>>)) || Obj <- ObjectList],
            case lists:member(?OBJECT_KEY, ObjectKeys) of
                true  -> io:format("SUCCESS: Object '~s' found~n", [?OBJECT_KEY]);
                false -> erlang:error({assertion_failed, {object_not_found, ?OBJECT_KEY}, {in, ObjectKeys}})
            end,
            lists:foreach(
                fun(Object) ->
                    Key = maps:get(<<"Key">>, Object, maps:get(<<"key">>, Object, <<"unknown">>)),
                    io:format("  - ~s~n", [Key])
                end,
                ObjectList
            ),
            io:format("~n");
        {error, {aws_error, StatusCode2, Code2, Message2}} ->
            erlang:error({list_objects_failed, {StatusCode2, Code2, Message2}});
        {error, ListObjectsError} ->
            erlang:error({list_objects_failed, ListObjectsError})
    end,

    %% Get the object from S3 bucket
    GetInput = #{
        <<"Bucket">> => ?BUCKET_NAME,
        <<"Key">> => ?OBJECT_KEY
    },
    case aws_s3_client:get_object(Client, GetInput) of
        {ok, GetOutput} ->
            io:format("~nSUCCESS: GetObject returned successfully!~n"),
            Body = maps:get(<<"Body">>, GetOutput, <<>>),
            case Body =:= ?EXPECTED_BODY of
                true  -> io:format("SUCCESS: GetObject body matches~n");
                false -> erlang:error({assertion_failed, {expected_body, ?EXPECTED_BODY}, {got, Body}})
            end,
            io:format("Response: ~p~n~n", [GetOutput]);
        {error, GetError} ->
            erlang:error({get_object_failed, GetError})
    end,

    io:format("=== S3 Client Application Complete ===~n"),
    ok.
