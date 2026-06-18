-module(demo_app).
-export([run/0]).

-include("kinesis_types.hrl").

-define(STREAM_NAME, <<"kinesis-demo-stream">>).
-define(SENT_RECORDS, [
    #{<<"message">> => <<"Hello from Erlang!">>, <<"id">> => 1},
    #{<<"message">> => <<"Kinesis streaming data">>, <<"id">> => 2},
    #{<<"message">> => <<"Smithy-Erlang demo">>, <<"id">> => 3}
]).

run() ->
    io:format("~n=== Running Kinesis Client Application ===~n~n"),

    Config = client_config(),
    io:format("Kinesis client configured for ~s~n~n", [maps:get(base_url, Config)]),

    setup_infrastructure(Config),

    %% 1. List streams and verify the demo stream
    io:format("--- ListStreams ---~n"),
    case kinesis_client:list_streams(Config, #list_streams_input{}) of
        {ok, Pages} when is_list(Pages) ->
            StreamNames = stream_names_from_list_streams_pages(Pages),
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
    DescribeInput = #describe_stream_input{stream_name = ?STREAM_NAME},
    ShardId = case kinesis_client:describe_stream(Config, DescribeInput) of
        {ok, #describe_stream_output{
            stream_description = #stream_description{
                stream_status = Status,
                retention_period_hours = RetentionHours,
                shards = Shards
            }
        }} ->
            case Status =:= active of
                true  -> io:format("SUCCESS: Stream status is ACTIVE~n");
                false -> erlang:error({assertion_failed, {expected_active_stream, Status}})
            end,
            case Shards of
                [] -> erlang:error({assertion_failed, no_shards_in_stream});
                _  -> io:format("SUCCESS: Stream has ~p shard(s)~n", [length(Shards)])
            end,
            io:format("  Stream: ~s~n", [?STREAM_NAME]),
            io:format("  Status: ~p~n", [Status]),
            io:format("  Retention: ~p hours~n", [RetentionHours]),
            case Shards of
                [#shard{shard_id = FirstShardId} | _] -> FirstShardId;
                [] -> undefined
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
            PutInput = #put_record_input{
                stream_name = ?STREAM_NAME,
                data = Data,
                partition_key = PartitionKey
            },
            case kinesis_client:put_record(Config, PutInput) of
                {ok, #put_record_output{sequence_number = SeqNum, shard_id = ShardIdOut}} ->
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
            GetIterInput = #get_shard_iterator_input{
                stream_name = ?STREAM_NAME,
                shard_id = ShardId,
                shard_iterator_type = trim_horizon
            },
            case kinesis_client:get_shard_iterator(Config, GetIterInput) of
                {ok, #get_shard_iterator_output{shard_iterator = Iter}} ->
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
    GetRecordsInput = #get_records_input{
        shard_iterator = ShardIterator,
        limit = 10
    },
    case kinesis_client:get_records(Config, GetRecordsInput) of
        {ok, #get_records_output{records = FetchedRecords, millis_behind_latest = MillisBehind}} ->
            io:format("SUCCESS: Retrieved ~p record(s), ~p ms behind latest~n",
                      [length(FetchedRecords), MillisBehind]),
            case length(FetchedRecords) =:= 3 of
                true  -> io:format("SUCCESS: All 3 records retrieved~n");
                false -> erlang:error({assertion_failed, {expected_count, 3}, {got, length(FetchedRecords)}})
            end,
            DecodedBodies = [
                jsx:decode(base64:decode(Data), [return_maps])
                || #record{data = Data} <- FetchedRecords
            ],
            case DecodedBodies =:= ?SENT_RECORDS of
                true  -> io:format("SUCCESS: Record bodies match~n");
                false -> erlang:error({assertion_failed, {expected_records, ?SENT_RECORDS}, {got, DecodedBodies}})
            end,
            lists:foreach(
                fun(#record{partition_key = PartKey, sequence_number = SeqNum, data = Data}) ->
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
    SummaryInput = #describe_stream_summary_input{stream_name = ?STREAM_NAME},
    case kinesis_client:describe_stream_summary(Config, SummaryInput) of
        {ok, #describe_stream_summary_output{
            stream_description_summary = #stream_description_summary{
                open_shard_count = OpenShards,
                consumer_count = ConsumerCount
            }
        }} ->
            io:format("SUCCESS: Stream summary~n"),
            io:format("  Open shards: ~p~n", [OpenShards]),
            io:format("  Consumers: ~p~n", [ConsumerCount]);
        {error, SummaryError} ->
            erlang:error({describe_stream_summary_failed, SummaryError})
    end,
    io:format("~n"),

    delete_demo_stream(Config),

    io:format("=== Kinesis Client Application Complete ===~n"),
    ok.

create_demo_stream(Config) ->
    io:format("--- CreateStream ---~n"),
    Input = #create_stream_input{
        stream_name = ?STREAM_NAME,
        shard_count = 1,
        stream_mode_details = #stream_mode_details{stream_mode = provisioned},
        tags = #{
            <<"Name">> => ?STREAM_NAME,
            <<"Environment">> => <<"demo">>
        }
    },
    case kinesis_client:create_stream(Config, Input) of
        {ok, _} ->
            io:format("SUCCESS: Stream '~s' create requested~n", [?STREAM_NAME]);
        {error, #resource_in_use_exception{}} ->
            io:format("SUCCESS: Stream '~s' already exists~n", [?STREAM_NAME]);
        {error, Reason} ->
            erlang:error({create_stream_failed, Reason})
    end,
    io:format("--- Wait StreamExists ---~n"),
    WaitInput = #describe_stream_input{stream_name = ?STREAM_NAME},
    case kinesis_waiters:wait_stream_exists(Config, WaitInput, #{}) of
        {ok, _} ->
            io:format("SUCCESS: Stream '~s' is ACTIVE~n~n", [?STREAM_NAME]);
        {error, WaitReason} ->
            erlang:error({wait_stream_exists_failed, WaitReason})
    end,
    ?STREAM_NAME.

setup_infrastructure(Config) ->
    io:format("--- Setup infrastructure ---~n"),
    StreamName = create_demo_stream(Config),
    io:format("Infrastructure ready: StreamName=~s~n~n", [StreamName]),
    StreamName.

delete_demo_stream(Config) ->
    io:format("--- DeleteStream ---~n"),
    case kinesis_client:delete_stream(Config,
        #delete_stream_input{stream_name = ?STREAM_NAME}) of
        {ok, _} ->
            io:format("SUCCESS: Stream '~s' delete requested~n", [?STREAM_NAME]);
        {error, #resource_not_found_exception{}} ->
            io:format("SUCCESS: Stream '~s' already absent~n", [?STREAM_NAME]);
        {error, Reason} ->
            erlang:error({delete_stream_failed, Reason})
    end,
    io:format("--- Wait StreamNotExists ---~n"),
    WaitInput = #describe_stream_input{stream_name = ?STREAM_NAME},
    case kinesis_waiters:wait_stream_not_exists(Config, WaitInput, #{}) of
        {ok, _} ->
            io:format("SUCCESS: Stream '~s' removed~n~n", [?STREAM_NAME]);
        {error, WaitReason} ->
            erlang:error({wait_stream_not_exists_failed, WaitReason})
    end,
    ok.

client_config() ->
    #{
        region => <<"us-east-1">>,
        endpoint_prefix => <<"kinesis">>,
        signing_name => <<"kinesis">>,
        base_url => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        credentials => #{
            access_key_id => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    }.

stream_names_from_list_streams_pages(Pages) ->
    lists:flatmap(
        fun
            (#list_streams_output{stream_names = undefined}) ->
                [];
            (#list_streams_output{stream_names = Names}) when is_list(Names) ->
                Names;
            (_) ->
                []
        end,
        Pages
    ).
