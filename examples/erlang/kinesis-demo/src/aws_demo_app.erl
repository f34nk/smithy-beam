-module(aws_demo_app).
-export([run/0]).

-define(STREAM_NAME, <<"kinesis-demo-stream">>).
-define(SENT_RECORDS, [
    #{<<"message">> => <<"Hello from Erlang!">>, <<"id">> => 1},
    #{<<"message">> => <<"Kinesis streaming data">>, <<"id">> => 2},
    #{<<"message">> => <<"Smithy-Erlang demo">>, <<"id">> => 3}
]).

run() ->
    io:format("~n=== Running Kinesis Client Application ===~n~n"),

    io:format("Creating Kinesis client...~n"),
    Config = #{
        endpoint => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        region => <<"us-east-1">>,
        service => <<"kinesis">>,
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
            case StreamNames of
                [] -> erlang:error({assertion_failed, empty_stream_list});
                _  -> io:format("SUCCESS: Found ~p stream(s)~n", [length(StreamNames)])
            end,
            case lists:member(?STREAM_NAME, StreamNames) of
                true  -> io:format("SUCCESS: Stream '~s' found~n", [?STREAM_NAME]);
                false -> erlang:error({assertion_failed, {stream_not_found, ?STREAM_NAME}})
            end,
            lists:foreach(
                fun(Name) -> io:format("  - ~s~n", [Name]) end,
                StreamNames
            );
        {error, ListError} ->
            erlang:error({list_streams_failed, ListError})
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
            case Status =:= <<"ACTIVE">> of
                true  -> io:format("SUCCESS: Stream status is ACTIVE~n");
                false -> erlang:error({assertion_failed, {expected_active_stream, Status}})
            end,
            case Shards of
                [] -> erlang:error({assertion_failed, no_shards_in_stream});
                _  -> io:format("SUCCESS: Stream has ~p shard(s)~n", [length(Shards)])
            end,
            io:format("  Stream: ~s~n", [?STREAM_NAME]),
            io:format("  Status: ~s~n", [Status]),
            io:format("  Retention: ~p hours~n", [RetentionHours]),
            case Shards of
                [FirstShard | _] ->
                    maps:get(<<"ShardId">>, FirstShard, undefined);
                [] ->
                    undefined
            end;
        {error, DescribeError} ->
            erlang:error({describe_stream_failed, DescribeError})
    end,
    io:format("~n"),

    %% 3. Put some records to the stream
    io:format("--- PutRecord (3 records) ---~n"),
    _SequenceNumbers = lists:map(
        fun({Idx, Record}) ->
            Data = base64:encode(jsx:encode(Record)),
            PartitionKey = <<"partition-", (integer_to_binary(Idx))/binary>>,
            PutInput = #{
                <<"StreamName">> => ?STREAM_NAME,
                <<"Data">> => Data,
                <<"PartitionKey">> => PartitionKey
            },
            case aws_kinesis_client:put_record(Client, PutInput, #{enable_retry => false}) of
                {ok, PutOutput} ->
                    SeqNum = maps:get(<<"SequenceNumber">>, PutOutput, <<>>),
                    ShardIdOut = maps:get(<<"ShardId">>, PutOutput, <<"unknown">>),
                    case byte_size(SeqNum) > 0 of
                        true  -> io:format("  Record ~p: ShardId=~s, Seq=~s~n", [Idx, ShardIdOut, SeqNum]);
                        false -> erlang:error({assertion_failed, {put_record_returned_empty_seq, Idx}})
                    end,
                    SeqNum;
                {error, PutError} ->
                    erlang:error({put_record_failed, {record, Idx}, PutError})
            end
        end,
        lists:zip(lists:seq(1, length(?SENT_RECORDS)), ?SENT_RECORDS)
    ),
    io:format("~n"),

    %% 4. Get a shard iterator to read records
    io:format("--- GetShardIterator ---~n"),
    ShardIterator = case ShardId of
        undefined ->
            erlang:error({assertion_failed, no_shard_id_available});
        _ ->
            GetIterInput = #{
                <<"StreamName">> => ?STREAM_NAME,
                <<"ShardId">> => ShardId,
                <<"ShardIteratorType">> => <<"TRIM_HORIZON">>
            },
            case aws_kinesis_client:get_shard_iterator(Client, GetIterInput, #{enable_retry => false}) of
                {ok, IterOutput} ->
                    Iter = maps:get(<<"ShardIterator">>, IterOutput, <<>>),
                    case byte_size(Iter) > 0 of
                        true  -> io:format("SUCCESS: Got shard iterator~n");
                        false -> erlang:error({assertion_failed, get_shard_iterator_returned_empty})
                    end,
                    Iter;
                {error, IterError} ->
                    erlang:error({get_shard_iterator_failed, IterError})
            end
    end,
    io:format("~n"),

    %% 5. Get records from the stream
    io:format("--- GetRecords ---~n"),
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
            case length(FetchedRecords) =:= 3 of
                true  -> io:format("SUCCESS: All 3 records retrieved~n");
                false -> erlang:error({assertion_failed, {expected_count, 3}, {got, length(FetchedRecords)}})
            end,
            DecodedBodies = [
                jsx:decode(base64:decode(maps:get(<<"Data">>, R, <<>>)), [return_maps])
                || R <- FetchedRecords
            ],
            case DecodedBodies =:= ?SENT_RECORDS of
                true  -> io:format("SUCCESS: Record bodies match~n");
                false -> erlang:error({assertion_failed, {expected_records, ?SENT_RECORDS}, {got, DecodedBodies}})
            end,
            lists:foreach(
                fun(Rec) ->
                    PartKey = maps:get(<<"PartitionKey">>, Rec, <<"unknown">>),
                    SeqNum = maps:get(<<"SequenceNumber">>, Rec, <<"unknown">>),
                    Data = maps:get(<<"Data">>, Rec, <<>>),
                    DecodedData = try jsx:decode(base64:decode(Data), [return_maps])
                                  catch _:_ -> Data end,
                    io:format("  Record: PartitionKey=~s~n", [PartKey]),
                    io:format("    SequenceNumber: ~s~n", [SeqNum]),
                    io:format("    Data: ~p~n", [DecodedData])
                end,
                FetchedRecords
            );
        {error, RecordsError} ->
            erlang:error({get_records_failed, RecordsError})
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
            erlang:error({describe_stream_summary_failed, SummaryError})
    end,
    io:format("~n"),

    io:format("=== Kinesis Client Application Complete ===~n"),
    ok.
