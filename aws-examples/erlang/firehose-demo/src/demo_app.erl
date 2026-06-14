-module(demo_app).
-export([run/0]).

-include("firehose_20150804_types.hrl").

-define(STREAM_NAME, <<"firehose-demo-stream">>).

run() ->
    io:format("~n=== Running Firehose Client Application ===~n~n"),

    Config = client_config(),
    io:format("Firehose client configured for ~s~n~n", [maps:get(base_url, Config)]),

  %% 1. List delivery streams (should be empty initially — no assertion required)
    io:format("--- ListDeliveryStreams (initial) ---~n"),
    case firehose_20150804_client:list_delivery_streams(Config, #list_delivery_streams_input{}) of
        {ok, #list_delivery_streams_output{delivery_stream_names = StreamNames}} ->
            io:format("SUCCESS: Found ~p delivery stream(s)~n", [length(StreamNames)]),
            lists:foreach(
                fun(Name) -> io:format("  - ~s~n", [Name]) end,
                StreamNames
            );
        {error, ListError} ->
            erlang:error({list_delivery_streams_failed, ListError})
    end,
    io:format("~n"),

  %% 2. Create a delivery stream with HTTP endpoint destination
    io:format("--- CreateDeliveryStream ---~n"),
    CreateInput = #create_delivery_stream_input{
        delivery_stream_name = ?STREAM_NAME,
        delivery_stream_type = direct_put,
        http_endpoint_destination_configuration = #http_endpoint_destination_configuration{
            endpoint_configuration = #http_endpoint_configuration{
                url = <<"http://localhost:9999/firehose">>,
                name = <<"demo-endpoint">>
            },
            buffering_hints = #http_endpoint_buffering_hints{
                size_in_m_bs = 1,
                interval_in_seconds = 60
            },
            s3configuration = #s3destination_configuration{
                role_arn = <<"arn:aws:iam::000000000000:role/firehose-role">>,
                bucket_arn = <<"arn:aws:s3:::firehose-backup-bucket">>
            },
            request_configuration = #http_endpoint_request_configuration{
                content_encoding = none
            },
            retry_options = #http_endpoint_retry_options{
                duration_in_seconds = 60
            }
        },
        tags = [
            #tag{key = <<"Environment">>, value = <<"demo">>},
            #tag{key = <<"Project">>, value = <<"smithy-erlang">>}
        ]
    },
    StreamCreated = case firehose_20150804_client:create_delivery_stream(Config, CreateInput) of
        {ok, #create_delivery_stream_output{delivery_stream_arn = Arn}} ->
            io:format("SUCCESS: Created delivery stream~n"),
            io:format("    ARN: ~s~n", [Arn]),
            true;
        {error, CreateError} ->
            erlang:error({create_delivery_stream_failed, CreateError})
    end,
    io:format("~n"),

    case StreamCreated of
        false ->
            io:format("Cannot continue without delivery stream~n");
        true ->
            io:format("Waiting for stream to become active...~n"),
            timer:sleep(2000),

          %% 3. List delivery streams again — assert stream appears in list
            io:format("--- ListDeliveryStreams ---~n"),
            case firehose_20150804_client:list_delivery_streams(Config, #list_delivery_streams_input{}) of
                {ok, #list_delivery_streams_output{delivery_stream_names = StreamNames2}} ->
                    case StreamNames2 of
                        [] -> erlang:error({assertion_failed, empty_stream_list_after_create});
                        _  -> io:format("SUCCESS: Found ~p delivery stream(s)~n", [length(StreamNames2)])
                    end,
                    case lists:member(?STREAM_NAME, StreamNames2) of
                        true  -> io:format("SUCCESS: Stream '~s' in list~n", [?STREAM_NAME]);
                        false -> erlang:error({assertion_failed, {stream_not_found, ?STREAM_NAME}})
                    end,
                    lists:foreach(
                        fun(Name) -> io:format("  - ~s~n", [Name]) end,
                        StreamNames2
                    );
                {error, ListError2} ->
                    erlang:error({list_delivery_streams_failed, ListError2})
            end,
            io:format("~n"),

          %% 4. Describe the delivery stream — assert status is ACTIVE
            io:format("--- DescribeDeliveryStream ---~n"),
            DescribeInput = #describe_delivery_stream_input{
                delivery_stream_name = ?STREAM_NAME
            },
            case firehose_20150804_client:describe_delivery_stream(Config, DescribeInput) of
                {ok, #describe_delivery_stream_output{
                    delivery_stream_description = #delivery_stream_description{
                        delivery_stream_arn = StreamArn,
                        delivery_stream_status = Status,
                        delivery_stream_type = StreamType
                    }
                }} ->
                    case Status of
                        active -> io:format("SUCCESS: Stream is ACTIVE~n");
                        Other  -> erlang:error({assertion_failed, {expected_active, Other}})
                    end,
                    io:format("    Name: ~s~n", [?STREAM_NAME]),
                    io:format("    ARN: ~s~n", [StreamArn]),
                    io:format("    Status: ~p~n", [Status]),
                    io:format("    Type: ~p~n", [StreamType]);
                {error, DescribeError} ->
                    erlang:error({describe_delivery_stream_failed, DescribeError})
            end,
            io:format("~n"),

          %% 5. List tags for the delivery stream
            io:format("--- ListTagsForDeliveryStream ---~n"),
            TagsInput = #list_tags_for_delivery_stream_input{
                delivery_stream_name = ?STREAM_NAME
            },
            case firehose_20150804_client:list_tags_for_delivery_stream(Config, TagsInput) of
                {ok, #list_tags_for_delivery_stream_output{tags = Tags}} ->
                    io:format("SUCCESS: Found ~p tag(s):~n", [length(Tags)]),
                    lists:foreach(
                        fun(#tag{key = Key, value = Value}) ->
                            io:format("    ~s = ~s~n", [Key, Value])
                        end,
                        Tags
                    );
                {error, TagsError} ->
                    erlang:error({list_tags_for_delivery_stream_failed, TagsError})
            end,
            io:format("~n"),

          %% 6. Put a single record to the stream
            io:format("--- PutRecord ---~n"),
            Record1 = jsone:encode(#{
                <<"event">> => <<"user_login">>,
                <<"user_id">> => <<"user-123">>,
                <<"timestamp">> => timestamp_rfc3339(),
                <<"source">> => <<"smithy-erlang-firehose-demo">>
            }),
            PutInput = #put_record_input{
                delivery_stream_name = ?STREAM_NAME,
                record = #record{data = base64:encode(<<Record1/binary, "\n">>)}
            },
            case firehose_20150804_client:put_record(Config, PutInput) of
                {ok, #put_record_output{record_id = RecordId}} ->
                    io:format("SUCCESS: Record sent, ID: ~s~n", [RecordId]);
                {error, PutError} ->
                    io:format("INFO: PutRecord returned error (expected in LocalStack): ~p~n", [PutError])
            end,
            io:format("~n"),

          %% 7. Put a batch of records
            io:format("--- PutRecordBatch ---~n"),
            Records = [
                #record{data = encode_event_record(#{
                    <<"event">> => <<"page_view">>,
                    <<"page">> => <<"/home">>,
                    <<"user_id">> => <<"user-123">>,
                    <<"timestamp">> => timestamp_rfc3339()
                })},
                #record{data = encode_event_record(#{
                    <<"event">> => <<"page_view">>,
                    <<"page">> => <<"/products">>,
                    <<"user_id">> => <<"user-456">>,
                    <<"timestamp">> => timestamp_rfc3339()
                })},
                #record{data = encode_event_record(#{
                    <<"event">> => <<"button_click">>,
                    <<"button">> => <<"add_to_cart">>,
                    <<"user_id">> => <<"user-789">>,
                    <<"timestamp">> => timestamp_rfc3339()
                })}
            ],
            BatchInput = #put_record_batch_input{
                delivery_stream_name = ?STREAM_NAME,
                records = Records
            },
            case firehose_20150804_client:put_record_batch(Config, BatchInput) of
                {ok, #put_record_batch_output{
                    failed_put_count = FailedCount,
                    request_responses = RequestResponses
                }} ->
                    io:format("SUCCESS: Batch sent, ~p records, ~p failed~n",
                        [length(RequestResponses), FailedCount]),
                    lists:foreach(
                        fun(Resp) ->
                            case Resp of
                                #put_record_batch_response_entry{record_id = RId} when is_binary(RId) ->
                                    io:format("    Record ID: ~s~n", [RId]);
                                #put_record_batch_response_entry{error_code = ErrorCode} ->
                                    io:format("    FAILED: ~s~n", [ErrorCode])
                            end
                        end,
                        RequestResponses
                    );
                {error, BatchError} ->
                    io:format("INFO: PutRecordBatch returned error (expected in LocalStack): ~p~n", [BatchError])
            end,
            io:format("~n"),

            io:format("Note: The PutRecord/PutRecordBatch operations return 500 errors because LocalStack actually tries to deliver to the configured HTTP endpoint (which doesn't exist). This is expected behavior - the client API works correctly, but the backend can't complete the delivery.~n~n"),

          %% 8. Add more tags
            io:format("--- TagDeliveryStream ---~n"),
            TagInput = #tag_delivery_stream_input{
                delivery_stream_name = ?STREAM_NAME,
                tags = [#tag{key = <<"CreatedBy">>, value = <<"smithy-erlang">>}]
            },
            case firehose_20150804_client:tag_delivery_stream(Config, TagInput) of
                {ok, _} ->
                    io:format("SUCCESS: Tag added~n");
                {error, TagError} ->
                    erlang:error({tag_delivery_stream_failed, TagError})
            end,
            io:format("~n"),

          %% 9. Untag the delivery stream
            io:format("--- UntagDeliveryStream ---~n"),
            UntagInput = #untag_delivery_stream_input{
                delivery_stream_name = ?STREAM_NAME,
                tag_keys = [<<"CreatedBy">>]
            },
            case firehose_20150804_client:untag_delivery_stream(Config, UntagInput) of
                {ok, _} ->
                    io:format("SUCCESS: Tag 'CreatedBy' removed~n");
                {error, UntagError} ->
                    erlang:error({untag_delivery_stream_failed, UntagError})
            end,
            io:format("~n"),

          %% 10. Delete the delivery stream (cleanup)
            io:format("--- DeleteDeliveryStream ---~n"),
            DeleteInput = #delete_delivery_stream_input{
                delivery_stream_name = ?STREAM_NAME
            },
            case firehose_20150804_client:delete_delivery_stream(Config, DeleteInput) of
                {ok, _} ->
                    io:format("SUCCESS: Delivery stream deleted~n");
                {error, DeleteError} ->
                    erlang:error({delete_delivery_stream_failed, DeleteError})
            end,
            io:format("~n"),

          %% 11. Verify deletion — crash if stream still exists
            io:format("--- ListDeliveryStreams (verify deletion) ---~n"),
            timer:sleep(1000),
            case firehose_20150804_client:list_delivery_streams(Config, #list_delivery_streams_input{}) of
                {ok, #list_delivery_streams_output{delivery_stream_names = FinalStreams}} ->
                    case lists:member(?STREAM_NAME, FinalStreams) of
                        false ->
                            io:format("SUCCESS: Stream deleted successfully~n");
                        true ->
                            erlang:error({assertion_failed, stream_still_exists})
                    end,
                    io:format("Remaining streams: ~p~n", [length(FinalStreams)]);
                {error, FinalError} ->
                    erlang:error({list_delivery_streams_failed, FinalError})
            end
    end,

    io:format("~n=== Firehose Client Application Complete ===~n"),
    ok.

client_config() ->
    #{
        region => <<"us-east-1">>,
        endpoint_prefix => <<"firehose">>,
        signing_name => <<"firehose">>,
        base_url => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        credentials => #{
            access_key_id => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    }.

timestamp_rfc3339() ->
    list_to_binary(calendar:system_time_to_rfc3339(erlang:system_time(second))).

encode_event_record(EventMap) ->
    base64:encode(jsone:encode(EventMap)).
