-module(aws_demo_app).
-export([run/0]).

-define(QUEUE_NAME, <<"sqs-demo-queue">>).
-define(MSG1_BODY, <<"Hello from Erlang! This is message 1.">>).
-define(MSG2_BODY, <<"Hello from Erlang! This is message 2.">>).

run() ->
    io:format("~n=== Running SQS Client Application ===~n~n"),

    io:format("Creating SQS client...~n"),
    Config = #{
        endpoint => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        region => <<"us-east-1">>,
        service => <<"sqs">>,
        credentials => #{
            access_key_id => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    },
    {ok, Client} = aws_sqs_client:new(Config),
    io:format("Client created successfully~n~n"),

    %% 1. List queues to see what was created by Terraform
    io:format("--- ListQueues ---~n"),
    case aws_sqs_client:list_queues(Client, #{}, #{enable_retry => false}) of
        {ok, ListOutput} ->
            QueueUrls = maps:get(<<"QueueUrls">>, ListOutput, []),
            case QueueUrls of
                [] -> erlang:error({assertion_failed, empty_queue_list});
                _  -> io:format("SUCCESS: Found ~p queue(s)~n", [length(QueueUrls)])
            end,
            QueueNameBin = ?QUEUE_NAME,
            case lists:any(fun(Url) -> binary:match(Url, QueueNameBin) =/= nomatch end, QueueUrls) of
                true  -> io:format("SUCCESS: Queue '~s' found in list~n", [?QUEUE_NAME]);
                false -> erlang:error({assertion_failed, {queue_not_found, ?QUEUE_NAME}, {in, QueueUrls}})
            end,
            lists:foreach(
                fun(Url) -> io:format("  - ~s~n", [Url]) end,
                QueueUrls
            );
        {error, ListError} ->
            erlang:error({list_queues_failed, ListError})
    end,
    io:format("~n"),

    %% 2. Get the queue URL for our demo queue
    io:format("--- GetQueueUrl ---~n"),
    QueueUrl = case aws_sqs_client:get_queue_url(Client, #{<<"QueueName">> => ?QUEUE_NAME}, #{enable_retry => false}) of
        {ok, UrlOutput} ->
            Url = maps:get(<<"QueueUrl">>, UrlOutput, undefined),
            case Url of
                undefined -> erlang:error({assertion_failed, get_queue_url_returned_empty});
                _ when is_binary(Url) andalso byte_size(Url) > 0 ->
                    io:format("SUCCESS: Queue URL: ~s~n", [Url]),
                    Url
            end;
        {error, UrlError} ->
            erlang:error({get_queue_url_failed, UrlError})
    end,
    io:format("~n"),

    %% 3. Send a message to the queue
    io:format("--- SendMessage ---~n"),
    SendInput1 = #{
        <<"QueueUrl">> => QueueUrl,
        <<"MessageBody">> => ?MSG1_BODY,
        <<"MessageAttributes">> => #{
            <<"Author">> => #{
                <<"DataType">> => <<"String">>,
                <<"StringValue">> => <<"Smithy-Erlang Demo">>
            }
        }
    },
    case aws_sqs_client:send_message(Client, SendInput1, #{enable_retry => false}) of
        {ok, SendOutput1} ->
            MessageId1 = maps:get(<<"MessageId">>, SendOutput1, <<>>),
            case byte_size(MessageId1) > 0 of
                true  -> io:format("SUCCESS: Message sent, ID: ~s~n", [MessageId1]);
                false -> erlang:error({assertion_failed, send_message_returned_empty_id})
            end;
        {error, SendError1} ->
            erlang:error({send_message_failed, SendError1})
    end,
    io:format("~n"),

    %% 4. Send a second message
    io:format("--- SendMessage (second message) ---~n"),
    SendInput2 = #{
        <<"QueueUrl">> => QueueUrl,
        <<"MessageBody">> => ?MSG2_BODY
    },
    case aws_sqs_client:send_message(Client, SendInput2, #{enable_retry => false}) of
        {ok, SendOutput2} ->
            MessageId2 = maps:get(<<"MessageId">>, SendOutput2, <<>>),
            case byte_size(MessageId2) > 0 of
                true  -> io:format("SUCCESS: Message sent, ID: ~s~n", [MessageId2]);
                false -> erlang:error({assertion_failed, send_message_returned_empty_id})
            end;
        {error, SendError2} ->
            erlang:error({send_message_failed, SendError2})
    end,
    io:format("~n"),

    %% 5. Get queue attributes
    io:format("--- GetQueueAttributes ---~n"),
    AttrInput = #{
        <<"QueueUrl">> => QueueUrl,
        <<"AttributeNames">> => [<<"All">>]
    },
    case aws_sqs_client:get_queue_attributes(Client, AttrInput, #{enable_retry => false}) of
        {ok, AttrOutput} ->
            Attrs = maps:get(<<"Attributes">>, AttrOutput, #{}),
            io:format("SUCCESS: Queue attributes:~n"),
            print_attributes(Attrs);
        {error, AttrError} ->
            erlang:error({get_queue_attributes_failed, AttrError})
    end,
    io:format("~n"),

    %% 6. Receive messages from the queue
    io:format("--- ReceiveMessage ---~n"),
    ReceiveInput = #{
        <<"QueueUrl">> => QueueUrl,
        <<"MaxNumberOfMessages">> => 10,
        <<"WaitTimeSeconds">> => 1,
        <<"MessageAttributeNames">> => [<<"All">>]
    },
    ReceiptHandles = case aws_sqs_client:receive_message(Client, ReceiveInput, #{enable_retry => false}) of
        {ok, ReceiveOutput} ->
            Messages = maps:get(<<"Messages">>, ReceiveOutput, []),
            case Messages of
                [] -> erlang:error({assertion_failed, no_messages_received});
                _  -> io:format("SUCCESS: Received ~p message(s)~n", [length(Messages)])
            end,
            ReceivedBodies = [maps:get(<<"Body">>, M, <<>>) || M <- Messages],
            ExpectedBodies = lists:sort([?MSG1_BODY, ?MSG2_BODY]),
            case lists:sort(ReceivedBodies) =:= ExpectedBodies of
                true  -> io:format("SUCCESS: Message bodies match~n");
                false -> erlang:error({assertion_failed, {expected_bodies, ExpectedBodies}, {got, ReceivedBodies}})
            end,
            lists:map(
                fun(Msg) ->
                    MsgId = maps:get(<<"MessageId">>, Msg, <<"unknown">>),
                    Body = maps:get(<<"Body">>, Msg, <<"empty">>),
                    Handle = maps:get(<<"ReceiptHandle">>, Msg, undefined),
                    io:format("  Message ID: ~s~n", [MsgId]),
                    io:format("    Body: ~s~n", [Body]),
                    Handle
                end,
                Messages
            );
        {error, ReceiveError} ->
            erlang:error({receive_message_failed, ReceiveError})
    end,
    io:format("~n"),

    %% 7. Delete the received messages
    io:format("--- DeleteMessage ---~n"),
    lists:foreach(
        fun(undefined) ->
                ok;
           (Handle) ->
                DeleteInput = #{
                    <<"QueueUrl">> => QueueUrl,
                    <<"ReceiptHandle">> => Handle
                },
                case aws_sqs_client:delete_message(Client, DeleteInput, #{enable_retry => false}) of
                    {ok, _} ->
                        io:format("SUCCESS: Message deleted~n");
                    {error, DeleteError} ->
                        erlang:error({delete_message_failed, DeleteError})
                end
        end,
        ReceiptHandles
    ),
    io:format("~n"),

    %% 8. Verify queue is empty
    io:format("--- ReceiveMessage (verify empty) ---~n"),
    case aws_sqs_client:receive_message(Client, ReceiveInput, #{enable_retry => false}) of
        {ok, VerifyOutput} ->
            VerifyMessages = maps:get(<<"Messages">>, VerifyOutput, []),
            case VerifyMessages of
                [] ->
                    io:format("SUCCESS: Queue is empty~n");
                _ ->
                    io:format("INFO: Queue still has ~p message(s)~n", [length(VerifyMessages)])
            end;
        {error, VerifyError} ->
            erlang:error({receive_message_failed, VerifyError})
    end,
    io:format("~n"),

    io:format("=== SQS Client Application Complete ===~n"),
    ok.

%% Helper to print queue attributes
print_attributes(Attrs) when is_map(Attrs) ->
    maps:foreach(
        fun(Key, Value) ->
            io:format("    ~s: ~s~n", [Key, Value])
        end,
        Attrs
    ).
