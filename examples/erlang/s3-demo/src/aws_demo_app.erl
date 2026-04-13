-module(aws_demo_app).
-export([run/0]).

run() ->
    io:format("~n=== Running S3 Client Application ===~n~n"),

    %% Create S3 client instance with AWS credentials
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

    %% List buckets (disable retry to see the actual error)
    ListInput = #{},
    try aws_s3_client:list_buckets(Client, ListInput, #{enable_retry => false}) of
        {ok, ListOutput} ->
            io:format("~nSUCCESS: ListBuckets returned successfully!~n"),
            io:format("Response: ~p~n~n", [ListOutput]),
            %% Extract buckets from S3 XML response structure
            %% Structure: ListAllMyBucketsResult -> Buckets -> Bucket (may be map or list)
            %% Response Syntax: https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListBuckets.html#API_ListBuckets_ResponseSyntax
            #{<<"ListAllMyBucketsResult">> := #{<<"Buckets">> := BucketsData}} = ListOutput,
            %% Normalize to list (single bucket comes as map, multiple as list)
            BucketList =
                case BucketsData of
                    M when is_map(M) -> [M];
                    L when is_list(L) -> L;
                    _ -> []
                end,
            io:format("Found ~p bucket(s):~n", [length(BucketList)]),
            lists:foreach(
                fun(Bucket0) ->
                    Bucket = maps:get(<<"Bucket">>, Bucket0, #{}),
                    Name = maps:get(
                        <<"Name">>, Bucket, <<"unknown">>
                    ),
                    io:format("  - ~s~n", [Name])
                end,
                BucketList
            ),
            io:format("~n");
        {error, {aws_error, StatusCode, Code, Message}} ->
            io:format("AWS Error: ~p ~s - ~s~n", [StatusCode, Code, Message]);
        {error, ListError} ->
            io:format("Error from S3: ~p~n", [ListError])
    catch
        error:Reason:Stacktrace ->
            io:format("Unexpected error: ~p~n", [Reason]),
            io:format("Stacktrace: ~p~n~n", [Stacktrace])
    end,

    %% Put an object to S3 bucket
    PutInput = #{
        <<"Bucket">> => <<"us-east-1-nonprod-configs">>,
        <<"Key">> => <<"configs/test.txt">>,
        <<"Body">> => <<"Hello World">>,
        <<"ContentType">> => <<"text/plain">>
    },
    case aws_s3_client:put_object(Client, PutInput) of
        {ok, PutOutput} ->
            io:format("~nSUCCESS: PutObject returned successfully!~n"),
            io:format("Response: ~p~n~n", [PutOutput]);
        {error, PutError} ->
            io:format("Failed to upload object: ~p~n~n", [PutError])
    end,

    %% List objects in the bucket
    ListObjectsInput = #{
        <<"Bucket">> => <<"us-east-1-nonprod-configs">>,
        <<"Prefix">> => <<"configs/">>,
        <<"MaxKeys">> => 100,
        <<"Delimiter">> => <<"">>
    },
    try aws_s3_client:list_objects(Client, ListObjectsInput) of
        {ok, ListObjectsOutput} ->
            io:format("~nSUCCESS: ListObjects returned successfully!~n"),
            io:format("Response: ~p~n~n", [ListObjectsOutput]),
            %% Extract list of objects from ListObjectsOutput (REST-XML decoded map)
            %% Response shape: #{<<"ListBucketResult">> => #{<<"Contents">> => [ ... ]}}
            %% Response Syntax: https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListObjects.html#API_ListObjects_ResponseSyntax
            ListBucketResult = maps:get(<<"ListBucketResult">>, ListObjectsOutput, #{}),
            %% "Contents" can be a list, a single map, or missing
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
            io:format("Found ~p object(s):~n", [length(ObjectList)]),
            lists:foreach(
                fun(Object) ->
                    Key = maps:get(<<"Key">>, Object, maps:get(<<"key">>, Object, <<"unknown">>)),
                    io:format("  - ~s~n", [Key])
                end,
                ObjectList
            ),
            io:format("~n");
        {error, {aws_error, StatusCode2, Code2, Message2}} ->
            io:format("AWS Error: ~p ~s - ~s~n", [StatusCode2, Code2, Message2]);
        {error, ListObjectsError} ->
            io:format("Error from S3: ~p~n", [ListObjectsError])
    catch
        error:Reason2:Stacktrace2 ->
            io:format("Unexpected error: ~p~n", [Reason2]),
            io:format("Stacktrace: ~p~n~n", [Stacktrace2])
    end,

    %% Get the object from S3 bucket
    GetInput = #{
        <<"Bucket">> => <<"us-east-1-nonprod-configs">>,
        <<"Key">> => <<"configs/test.txt">>
    },
    case aws_s3_client:get_object(Client, GetInput) of
        {ok, GetOutput} ->
            io:format("~nSUCCESS: GetObject returned successfully!~n"),
            io:format("Response: ~p~n~n", [GetOutput]);
        {error, GetError} ->
            io:format("Failed to get object: ~p~n~n", [GetError])
    end,

    io:format("=== S3 Client Application Complete ===~n"),
    ok.
