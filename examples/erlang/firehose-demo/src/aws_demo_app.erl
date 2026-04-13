-module(aws_demo_app).
-export([run/0]).

-define(STREAM_NAME, <<"firehose-demo-stream">>).

run() ->
    io:format("~n=== Running Firehose Client Application ===~n~n"),

    %% Create Firehose client instance with AWS credentials
    io:format("Creating Firehose client...~n"),
    Config = #{
        endpoint => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        region => <<"us-east-1">>,
        service => <<"firehose">>,  %% Required for SigV4 signing with custom endpoints
        credentials => #{
            access_key_id => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    },
    {ok, Client} = aws_firehose_client:new(Config),
    io:format("Client created successfully~n~n"),

    %% 1. List delivery streams (should be empty initially)
    io:format("--- ListDeliveryStreams (initial) ---~n"),
    case aws_firehose_client:list_delivery_streams(Client, #{}, #{enable_retry => false}) of
        {ok, ListOutput} ->
            StreamNames = maps:get(<<"DeliveryStreamNames">>, ListOutput, []),
            io:format("SUCCESS: Found ~p delivery stream(s)~n", [length(StreamNames)]),
            lists:foreach(
                fun(Name) -> io:format("  - ~s~n", [Name]) end,
                StreamNames
            );
        {error, ListError} ->
            io:format("ERROR: ~p~n", [ListError])
    end,
    io:format("~n"),

    %% 2. Create a delivery stream with HTTP endpoint destination
    %% Using HTTP endpoint as it's simpler for LocalStack demo
    io:format("--- CreateDeliveryStream ---~n"),
    CreateInput = #{
        <<"DeliveryStreamName">> => ?STREAM_NAME,
        <<"DeliveryStreamType">> => <<"DirectPut">>,
        <<"HttpEndpointDestinationConfiguration">> => #{
            <<"EndpointConfiguration">> => #{
                <<"Url">> => <<"http://localhost:9999/firehose">>,
                <<"Name">> => <<"demo-endpoint">>
            },
            <<"BufferingHints">> => #{
                <<"SizeInMBs">> => 1,
                <<"IntervalInSeconds">> => 60
            },
            <<"S3Configuration">> => #{
                <<"RoleARN">> => <<"arn:aws:iam::000000000000:role/firehose-role">>,
                <<"BucketARN">> => <<"arn:aws:s3:::firehose-backup-bucket">>
            },
            <<"RequestConfiguration">> => #{
                <<"ContentEncoding">> => <<"NONE">>
            },
            <<"RetryOptions">> => #{
                <<"DurationInSeconds">> => 60
            }
        },
        <<"Tags">> => [
            #{<<"Key">> => <<"Environment">>, <<"Value">> => <<"demo">>},
            #{<<"Key">> => <<"Project">>, <<"Value">> => <<"smithy-erlang">>}
        ]
    },
    StreamCreated = case aws_firehose_client:create_delivery_stream(Client, CreateInput, #{enable_retry => false}) of
        {ok, CreateOutput} ->
            Arn = maps:get(<<"DeliveryStreamARN">>, CreateOutput, <<"unknown">>),
            io:format("SUCCESS: Created delivery stream~n"),
            io:format("    ARN: ~s~n", [Arn]),
            true;
        {error, CreateError} ->
            io:format("ERROR: ~p~n", [CreateError]),
            false
    end,
    io:format("~n"),

    case StreamCreated of
        false ->
            io:format("Cannot continue without delivery stream~n");
        true ->
            %% Wait a moment for stream to become active
            io:format("Waiting for stream to become active...~n"),
            timer:sleep(2000),

            %% 3. List delivery streams again
            io:format("--- ListDeliveryStreams ---~n"),
            case aws_firehose_client:list_delivery_streams(Client, #{}, #{enable_retry => false}) of
                {ok, ListOutput2} ->
                    StreamNames2 = maps:get(<<"DeliveryStreamNames">>, ListOutput2, []),
                    io:format("SUCCESS: Found ~p delivery stream(s)~n", [length(StreamNames2)]),
                    lists:foreach(
                        fun(Name) -> io:format("  - ~s~n", [Name]) end,
                        StreamNames2
                    );
                {error, ListError2} ->
                    io:format("ERROR: ~p~n", [ListError2])
            end,
            io:format("~n"),

            %% 4. Describe the delivery stream
            io:format("--- DescribeDeliveryStream ---~n"),
            DescribeInput = #{<<"DeliveryStreamName">> => ?STREAM_NAME},
            case aws_firehose_client:describe_delivery_stream(Client, DescribeInput, #{enable_retry => false}) of
                {ok, DescribeOutput} ->
                    StreamDesc = maps:get(<<"DeliveryStreamDescription">>, DescribeOutput, #{}),
                    StreamArn = maps:get(<<"DeliveryStreamARN">>, StreamDesc, <<"unknown">>),
                    Status = maps:get(<<"DeliveryStreamStatus">>, StreamDesc, <<"unknown">>),
                    StreamType = maps:get(<<"DeliveryStreamType">>, StreamDesc, <<"unknown">>),
                    io:format("SUCCESS: Stream details:~n"),
                    io:format("    Name: ~s~n", [?STREAM_NAME]),
                    io:format("    ARN: ~s~n", [StreamArn]),
                    io:format("    Status: ~s~n", [Status]),
                    io:format("    Type: ~s~n", [StreamType]);
                {error, DescribeError} ->
                    io:format("ERROR: ~p~n", [DescribeError])
            end,
            io:format("~n"),

            %% 5. List tags for the delivery stream
            io:format("--- ListTagsForDeliveryStream ---~n"),
            TagsInput = #{<<"DeliveryStreamName">> => ?STREAM_NAME},
            case aws_firehose_client:list_tags_for_delivery_stream(Client, TagsInput, #{enable_retry => false}) of
                {ok, TagsOutput} ->
                    Tags = maps:get(<<"Tags">>, TagsOutput, []),
                    io:format("SUCCESS: Found ~p tag(s):~n", [length(Tags)]),
                    lists:foreach(
                        fun(Tag) ->
                            Key = maps:get(<<"Key">>, Tag, <<"unknown">>),
                            Value = maps:get(<<"Value">>, Tag, <<"unknown">>),
                            io:format("    ~s = ~s~n", [Key, Value])
                        end,
                        Tags
                    );
                {error, TagsError} ->
                    io:format("ERROR: ~p~n", [TagsError])
            end,
            io:format("~n"),

            %% 6. Put a single record to the stream
            io:format("--- PutRecord ---~n"),
            Record1 = jsx:encode(#{
                <<"event">> => <<"user_login">>,
                <<"user_id">> => <<"user-123">>,
                <<"timestamp">> => list_to_binary(calendar:system_time_to_rfc3339(erlang:system_time(second))),
                <<"source">> => <<"smithy-erlang-firehose-demo">>
            }),
            PutInput = #{
                <<"DeliveryStreamName">> => ?STREAM_NAME,
                <<"Record">> => #{
                    <<"Data">> => base64:encode(<<Record1/binary, "\n">>)
                }
            },
            case aws_firehose_client:put_record(Client, PutInput, #{enable_retry => false}) of
                {ok, PutOutput} ->
                    RecordId = maps:get(<<"RecordId">>, PutOutput, <<"unknown">>),
                    io:format("SUCCESS: Record sent, ID: ~s~n", [RecordId]);
                {error, PutError} ->
                    io:format("ERROR: ~p~n", [PutError])
            end,
            io:format("~n"),

            %% 7. Put a batch of records
            io:format("--- PutRecordBatch ---~n"),
            Records = [
                #{<<"Data">> => base64:encode(jsx:encode(#{
                    <<"event">> => <<"page_view">>,
                    <<"page">> => <<"/home">>,
                    <<"user_id">> => <<"user-123">>,
                    <<"timestamp">> => list_to_binary(calendar:system_time_to_rfc3339(erlang:system_time(second)))
                }))},
                #{<<"Data">> => base64:encode(jsx:encode(#{
                    <<"event">> => <<"page_view">>,
                    <<"page">> => <<"/products">>,
                    <<"user_id">> => <<"user-456">>,
                    <<"timestamp">> => list_to_binary(calendar:system_time_to_rfc3339(erlang:system_time(second)))
                }))},
                #{<<"Data">> => base64:encode(jsx:encode(#{
                    <<"event">> => <<"button_click">>,
                    <<"button">> => <<"add_to_cart">>,
                    <<"user_id">> => <<"user-789">>,
                    <<"timestamp">> => list_to_binary(calendar:system_time_to_rfc3339(erlang:system_time(second)))
                }))}
            ],
            BatchInput = #{
                <<"DeliveryStreamName">> => ?STREAM_NAME,
                <<"Records">> => Records
            },
            case aws_firehose_client:put_record_batch(Client, BatchInput, #{enable_retry => false}) of
                {ok, BatchOutput} ->
                    FailedCount = maps:get(<<"FailedPutCount">>, BatchOutput, 0),
                    RequestResponses = maps:get(<<"RequestResponses">>, BatchOutput, []),
                    io:format("SUCCESS: Batch sent, ~p records, ~p failed~n", [length(RequestResponses), FailedCount]),
                    lists:foreach(
                        fun(Resp) ->
                            case maps:get(<<"RecordId">>, Resp, undefined) of
                                undefined ->
                                    ErrorCode = maps:get(<<"ErrorCode">>, Resp, <<"unknown">>),
                                    io:format("    FAILED: ~s~n", [ErrorCode]);
                                RId ->
                                    io:format("    Record ID: ~s~n", [RId])
                            end
                        end,
                        RequestResponses
                    );
                {error, BatchError} ->
                    io:format("ERROR: ~p~n", [BatchError])
            end,
            io:format("~n"),

            io:format("Note: The PutRecord/PutRecordBatch operations return 500 errors because LocalStack actually tries to deliver to the configured HTTP endpoint (which doesn't exist). This is expected behavior - the client API works correctly, but the backend can't complete the delivery.~n~n"),

            %% 8. Add more tags
            io:format("--- TagDeliveryStream ---~n"),
            TagInput = #{
                <<"DeliveryStreamName">> => ?STREAM_NAME,
                <<"Tags">> => [
                    #{<<"Key">> => <<"CreatedBy">>, <<"Value">> => <<"smithy-erlang">>}
                ]
            },
            case aws_firehose_client:tag_delivery_stream(Client, TagInput, #{enable_retry => false}) of
                {ok, _} ->
                    io:format("SUCCESS: Tag added~n");
                {error, TagError} ->
                    io:format("ERROR: ~p~n", [TagError])
            end,
            io:format("~n"),

            %% 9. Untag the delivery stream
            io:format("--- UntagDeliveryStream ---~n"),
            UntagInput = #{
                <<"DeliveryStreamName">> => ?STREAM_NAME,
                <<"TagKeys">> => [<<"CreatedBy">>]
            },
            case aws_firehose_client:untag_delivery_stream(Client, UntagInput, #{enable_retry => false}) of
                {ok, _} ->
                    io:format("SUCCESS: Tag 'CreatedBy' removed~n");
                {error, UntagError} ->
                    io:format("ERROR: ~p~n", [UntagError])
            end,
            io:format("~n"),

            %% 10. Delete the delivery stream (cleanup)
            io:format("--- DeleteDeliveryStream ---~n"),
            DeleteInput = #{<<"DeliveryStreamName">> => ?STREAM_NAME},
            case aws_firehose_client:delete_delivery_stream(Client, DeleteInput, #{enable_retry => false}) of
                {ok, _} ->
                    io:format("SUCCESS: Delivery stream deleted~n");
                {error, DeleteError} ->
                    io:format("ERROR: ~p~n", [DeleteError])
            end,
            io:format("~n"),

            %% 11. Verify deletion
            io:format("--- ListDeliveryStreams (verify deletion) ---~n"),
            timer:sleep(1000),
            case aws_firehose_client:list_delivery_streams(Client, #{}, #{enable_retry => false}) of
                {ok, FinalOutput} ->
                    FinalStreams = maps:get(<<"DeliveryStreamNames">>, FinalOutput, []),
                    case lists:member(?STREAM_NAME, FinalStreams) of
                        false ->
                            io:format("SUCCESS: Stream deleted successfully~n");
                        true ->
                            io:format("INFO: Stream still exists (deletion may be in progress)~n")
                    end,
                    io:format("Remaining streams: ~p~n", [length(FinalStreams)]);
                {error, FinalError} ->
                    io:format("ERROR: ~p~n", [FinalError])
            end
    end,

    io:format("~n=== Firehose Client Application Complete ===~n"),
    ok.
