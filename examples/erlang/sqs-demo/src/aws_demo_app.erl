-module(aws_demo_app).
-export([run/0]).

-define(QUEUE_NAME, <<"sqs-demo-queue">>).

run() ->
    io:format("~n=== Running SQS Client Application ===~n~n"),

    %% Create SQS client instance with AWS credentials
    io:format("Creating SQS client...~n"),
    Config = #{
        endpoint => <<"http://localhost:4568">>,
        region => <<"us-east-1">>,
        service => <<"sqs">>,  %% Required for SigV4 signing with custom endpoints
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
            io:format("SUCCESS: Found ~p queue(s)~n", [length(QueueUrls)]),
            lists:foreach(
                fun(Url) -> io:format("  - ~s~n", [Url]) end,
                QueueUrls
            );
        {error, ListError} ->
            io:format("ERROR: ~p~n", [ListError])
    end,
    io:format("~n"),

    %% 2. Get the queue URL for our demo queue
    io:format("--- GetQueueUrl ---~n"),
    QueueUrl = case aws_sqs_client:get_queue_url(Client, #{<<"QueueName">> => ?QUEUE_NAME}, #{enable_retry => false}) of
        {ok, UrlOutput} ->
            Url = maps:get(<<"QueueUrl">>, UrlOutput, undefined),
            io:format("SUCCESS: Queue URL: ~s~n", [Url]),
            Url;
        {error, UrlError} ->
            io:format("ERROR: ~p~n", [UrlError]),
            undefined
    end,
    io:format("~n"),

    case QueueUrl of
        undefined ->
            io:format("Cannot continue without queue URL~n");
        _ ->
            %% 3. Send a message to the queue
            io:format("--- SendMessage ---~n"),
            Message1 = <<"Hello from Erlang! This is message 1.">>,
            SendInput1 = #{
                <<"QueueUrl">> => QueueUrl,
                <<"MessageBody">> => Message1,
                <<"MessageAttributes">> => #{
                    <<"Author">> => #{
                        <<"DataType">> => <<"String">>,
                        <<"StringValue">> => <<"Smithy-Erlang Demo">>
                    }
                }
            },
            case aws_sqs_client:send_message(Client, SendInput1, #{enable_retry => false}) of
                {ok, SendOutput1} ->
                    MessageId1 = maps:get(<<"MessageId">>, SendOutput1, <<"unknown">>),
                    io:format("SUCCESS: Message sent, ID: ~s~n", [MessageId1]);
                {error, SendError1} ->
                    io:format("ERROR: ~p~n", [SendError1])
            end,
            io:format("~n"),

            %% 4. Send a second message
            io:format("--- SendMessage (second message) ---~n"),
            Message2 = <<"Hello from Erlang! This is message 2.">>,
            SendInput2 = #{
                <<"QueueUrl">> => QueueUrl,
                <<"MessageBody">> => Message2
            },
            case aws_sqs_client:send_message(Client, SendInput2, #{enable_retry => false}) of
                {ok, SendOutput2} ->
                    MessageId2 = maps:get(<<"MessageId">>, SendOutput2, <<"unknown">>),
                    io:format("SUCCESS: Message sent, ID: ~s~n", [MessageId2]);
                {error, SendError2} ->
                    io:format("ERROR: ~p~n", [SendError2])
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
                    io:format("ERROR: ~p~n", [AttrError])
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
                    io:format("SUCCESS: Received ~p message(s)~n", [length(Messages)]),
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
                    io:format("ERROR: ~p~n", [ReceiveError]),
                    []
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
                                io:format("ERROR: ~p~n", [DeleteError])
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
                    io:format("ERROR: ~p~n", [VerifyError])
            end
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
