-module(aws_demo_app).
-export([run/0]).

-define(STREAM_NAME, <<"kinesis-demo-stream">>).

run() ->
    io:format("~n=== Running Kinesis Client Application ===~n~n"),

    %% Create Kinesis client instance with AWS credentials
    io:format("Creating Kinesis client...~n"),
    Config = #{
        endpoint => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        region => <<"us-east-1">>,
        service => <<"kinesis">>,  %% Required for SigV4 signing with custom endpoints
        credentials => #{
            access_key_id => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    },
    {ok, Client} = aws_kinesis_client:new(Config),
    io:format("Client created successfully~n~n"),

    %% 1. List streams to see what was created by Terraform
    io:format("--- ListStreams ---~n"),
    case aws_kinesis_client:list_streams(Client, #{}, #{enable_retry => false}) of
        {ok, ListOutput} ->
            StreamNames = maps:get(<<"StreamNames">>, ListOutput, []),
            io:format("SUCCESS: Found ~p stream(s)~n", [length(StreamNames)]),
            lists:foreach(
                fun(Name) -> io:format("  - ~s~n", [Name]) end,
                StreamNames
            );
        {error, ListError} ->
            io:format("ERROR: ~p~n", [ListError])
    end,
    io:format("~n"),

    %% 2. Describe the demo stream
    io:format("--- DescribeStream ---~n"),
    DescribeInput = #{<<"StreamName">> => ?STREAM_NAME},
    ShardId = case aws_kinesis_client:describe_stream(Client, DescribeInput, #{enable_retry => false}) of
        {ok, DescribeOutput} ->
            StreamDesc = maps:get(<<"StreamDescription">>, DescribeOutput, #{}),
            Status = maps:get(<<"StreamStatus">>, StreamDesc, <<"unknown">>),
            RetentionHours = maps:get(<<"RetentionPeriodHours">>, StreamDesc, 0),
            Shards = maps:get(<<"Shards">>, StreamDesc, []),
            io:format("SUCCESS: Stream '~s'~n", [?STREAM_NAME]),
            io:format("  Status: ~s~n", [Status]),
            io:format("  Retention: ~p hours~n", [RetentionHours]),
            io:format("  Shards: ~p~n", [length(Shards)]),
            case Shards of
                [FirstShard | _] ->
                    maps:get(<<"ShardId">>, FirstShard, undefined);
                [] ->
                    undefined
            end;
        {error, DescribeError} ->
            io:format("ERROR: ~p~n", [DescribeError]),
            undefined
    end,
    io:format("~n"),

    %% 3. Put some records to the stream
    io:format("--- PutRecord (3 records) ---~n"),
    Records = [
        #{<<"message">> => <<"Hello from Erlang!">>, <<"id">> => 1},
        #{<<"message">> => <<"Kinesis streaming data">>, <<"id">> => 2},
        #{<<"message">> => <<"Smithy-Erlang demo">>, <<"id">> => 3}
    ],
    SequenceNumbers = lists:map(
        fun({Idx, Record}) ->
            %% Data must be base64 encoded for Kinesis
            Data = base64:encode(jsx:encode(Record)),
            PartitionKey = <<"partition-", (integer_to_binary(Idx))/binary>>,
            PutInput = #{
                <<"StreamName">> => ?STREAM_NAME,
                <<"Data">> => Data,
                <<"PartitionKey">> => PartitionKey
            },
            case aws_kinesis_client:put_record(Client, PutInput, #{enable_retry => false}) of
                {ok, PutOutput} ->
                    SeqNum = maps:get(<<"SequenceNumber">>, PutOutput, <<"unknown">>),
                    ShardIdOut = maps:get(<<"ShardId">>, PutOutput, <<"unknown">>),
                    io:format("  Record ~p: ShardId=~s, Seq=~s~n", [Idx, ShardIdOut, SeqNum]),
                    SeqNum;
                {error, PutError} ->
                    io:format("  Record ~p ERROR: ~p~n", [Idx, PutError]),
                    undefined
            end
        end,
        lists:zip(lists:seq(1, length(Records)), Records)
    ),
    io:format("~n"),

    %% 4. Get a shard iterator to read records
    io:format("--- GetShardIterator ---~n"),
    ShardIterator = case ShardId of
        undefined ->
            io:format("ERROR: No shard ID available~n"),
            undefined;
        _ ->
            GetIterInput = #{
                <<"StreamName">> => ?STREAM_NAME,
                <<"ShardId">> => ShardId,
                <<"ShardIteratorType">> => <<"TRIM_HORIZON">>
            },
            case aws_kinesis_client:get_shard_iterator(Client, GetIterInput, #{enable_retry => false}) of
                {ok, IterOutput} ->
                    Iter = maps:get(<<"ShardIterator">>, IterOutput, undefined),
                    io:format("SUCCESS: Got shard iterator~n"),
                    Iter;
                {error, IterError} ->
                    io:format("ERROR: ~p~n", [IterError]),
                    undefined
            end
    end,
    io:format("~n"),

    %% 5. Get records from the stream
    io:format("--- GetRecords ---~n"),
    case ShardIterator of
        undefined ->
            io:format("ERROR: No shard iterator available~n");
        _ ->
            GetRecordsInput = #{
                <<"ShardIterator">> => ShardIterator,
                <<"Limit">> => 10
            },
            case aws_kinesis_client:get_records(Client, GetRecordsInput, #{enable_retry => false}) of
                {ok, RecordsOutput} ->
                    FetchedRecords = maps:get(<<"Records">>, RecordsOutput, []),
                    MillisBehind = maps:get(<<"MillisBehindLatest">>, RecordsOutput, 0),
                    io:format("SUCCESS: Retrieved ~p record(s), ~p ms behind latest~n", 
                              [length(FetchedRecords), MillisBehind]),
                    lists:foreach(
                        fun(Rec) ->
                            Data = maps:get(<<"Data">>, Rec, <<>>),
                            PartKey = maps:get(<<"PartitionKey">>, Rec, <<"unknown">>),
                            SeqNum = maps:get(<<"SequenceNumber">>, Rec, <<"unknown">>),
                            %% Decode the base64 data
                            DecodedData = try
                                jsx:decode(base64:decode(Data))
                            catch
                                _:_ -> Data
                            end,
                            io:format("  Record: PartitionKey=~s~n", [PartKey]),
                            io:format("    SequenceNumber: ~s~n", [SeqNum]),
                            io:format("    Data: ~p~n", [DecodedData])
                        end,
                        FetchedRecords
                    );
                {error, RecordsError} ->
                    io:format("ERROR: ~p~n", [RecordsError])
            end
    end,
    io:format("~n"),

    %% 6. Describe stream summary
    io:format("--- DescribeStreamSummary ---~n"),
    SummaryInput = #{<<"StreamName">> => ?STREAM_NAME},
    case aws_kinesis_client:describe_stream_summary(Client, SummaryInput, #{enable_retry => false}) of
        {ok, SummaryOutput} ->
            Summary = maps:get(<<"StreamDescriptionSummary">>, SummaryOutput, #{}),
            OpenShards = maps:get(<<"OpenShardCount">>, Summary, 0),
            ConsumerCount = maps:get(<<"ConsumerCount">>, Summary, 0),
            io:format("SUCCESS: Stream summary~n"),
            io:format("  Open shards: ~p~n", [OpenShards]),
            io:format("  Consumers: ~p~n", [ConsumerCount]);
        {error, SummaryError} ->
            io:format("ERROR: ~p~n", [SummaryError])
    end,
    io:format("~n"),

    io:format("=== Kinesis Client Application Complete ===~n"),
    ok.
