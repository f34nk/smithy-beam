-module(demo_app).
-export([run/0]).

-include("sqs_types.hrl").

-define(QUEUE_NAME, <<"sqs-demo-queue">>).
-define(MSG1_BODY, <<"Hello from Erlang! This is message 1.">>).
-define(MSG2_BODY, <<"Hello from Erlang! This is message 2.">>).

run() ->
    io:format("~n=== Running SQS Client Application ===~n~n"),

    Config = client_config(),
    io:format("SQS client configured for ~s~n~n", [maps:get(base_url, Config)]),

    QueueUrl = setup_infrastructure(Config),

    list_queues(Config),
    get_queue_url(Config),
    send_messages(Config, QueueUrl),
    get_queue_attributes(Config, QueueUrl),
    ReceiptHandles = receive_messages(Config, QueueUrl),
    delete_messages(Config, QueueUrl, ReceiptHandles),
    verify_empty_queue(Config, QueueUrl),
    delete_demo_queue(Config, QueueUrl),

    io:format("~n=== SQS Client Application Complete ===~n"),
    ok.

client_config() ->
    #{
        region => <<"us-east-1">>,
        endpoint_prefix => <<"sqs">>,
        signing_name => <<"sqs">>,
        base_url => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        credentials => #{
            access_key_id => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    }.

setup_infrastructure(Config) ->
    io:format("--- Setup infrastructure ---~n"),
    QueueUrl = create_demo_queue(Config),
    io:format("Infrastructure ready: Queue=~s~n~n", [?QUEUE_NAME]),
    QueueUrl.

create_demo_queue(Config) ->
    io:format("--- CreateQueue ---~n"),
    Input = #create_queue_input{
        queue_name = ?QUEUE_NAME,
        attributes = #{
            delay_seconds => <<"0">>,
            maximum_message_size => <<"262144">>,
            message_retention_period => <<"345600">>,
            receive_message_wait_time_seconds => <<"0">>,
            visibility_timeout => <<"30">>
        },
        tags = #{
            <<"Name">> => ?QUEUE_NAME,
            <<"Environment">> => <<"demo">>
        }
    },
    case sqs_client:create_queue(Config, Input) of
        {ok, #create_queue_output{queue_url = Url}} when is_binary(Url), byte_size(Url) > 0 ->
            io:format("SUCCESS: Queue '~s' created~n", [?QUEUE_NAME]),
            io:format("SUCCESS: Queue URL: ~s~n~n", [Url]),
            Url;
        {ok, _} ->
            erlang:error({assertion_failed, create_queue_returned_empty_url});
        {error, #queue_name_exists{}} ->
            io:format("SUCCESS: Queue '~s' already exists~n", [?QUEUE_NAME]),
            case sqs_client:get_queue_url(Config, #get_queue_url_input{queue_name = ?QUEUE_NAME}) of
                {ok, #get_queue_url_output{queue_url = Url}} when is_binary(Url), byte_size(Url) > 0 ->
                    io:format("SUCCESS: Queue URL: ~s~n~n", [Url]),
                    Url;
                {error, Reason} ->
                    erlang:error({get_queue_url_failed, Reason})
            end;
        {error, Reason} ->
            erlang:error({create_queue_failed, Reason})
    end.

list_queues(Config) ->
    io:format("--- ListQueues ---~n"),
    case sqs_client:list_queues(Config, #list_queues_input{}) of
        {ok, Urls} when is_list(Urls), Urls =/= [] ->
            io:format("SUCCESS: Found ~p queue(s)~n", [length(Urls)]),
            case lists:any(fun(Url) -> binary:match(Url, ?QUEUE_NAME) =/= nomatch end, Urls) of
                true  -> io:format("SUCCESS: Queue '~s' found in list~n", [?QUEUE_NAME]);
                false -> erlang:error({assertion_failed, {queue_not_found, ?QUEUE_NAME}, {in, Urls}})
            end,
            lists:foreach(fun(Url) -> io:format("  - ~s~n", [Url]) end, Urls);
        {ok, _} ->
            erlang:error({assertion_failed, empty_queue_list});
        {error, Reason} ->
            erlang:error({list_queues_failed, Reason})
    end,
    io:format("~n").

get_queue_url(Config) ->
    io:format("--- GetQueueUrl ---~n"),
    case sqs_client:get_queue_url(Config, #get_queue_url_input{queue_name = ?QUEUE_NAME}) of
        {ok, #get_queue_url_output{queue_url = Url}} when is_binary(Url), byte_size(Url) > 0 ->
            io:format("SUCCESS: Queue URL: ~s~n", [Url]);
        {ok, _} ->
            erlang:error({assertion_failed, get_queue_url_returned_empty});
        {error, Reason} ->
            erlang:error({get_queue_url_failed, Reason})
    end,
    io:format("~n").

send_messages(Config, QueueUrl) ->
    io:format("--- SendMessage ---~n"),
    Input1 = #send_message_input{
        queue_url = QueueUrl,
        message_body = ?MSG1_BODY,
        message_attributes = #{
            <<"Author">> => #message_attribute_value{
                data_type = <<"String">>,
                string_value = <<"Smithy-Erlang Demo">>
            }
        }
    },
    send_message(Config, Input1),
    io:format("~n"),

    io:format("--- SendMessage (second message) ---~n"),
    Input2 = #send_message_input{
        queue_url = QueueUrl,
        message_body = ?MSG2_BODY
    },
    send_message(Config, Input2),
    io:format("~n").

send_message(Config, Input) ->
    case sqs_client:send_message(Config, Input) of
        {ok, #send_message_output{message_id = MsgId}} when is_binary(MsgId), byte_size(MsgId) > 0 ->
            io:format("SUCCESS: Message sent, ID: ~s~n", [MsgId]);
        {ok, _} ->
            erlang:error({assertion_failed, send_message_returned_empty_id});
        {error, Reason} ->
            erlang:error({send_message_failed, Reason})
    end.

get_queue_attributes(Config, QueueUrl) ->
    io:format("--- GetQueueAttributes ---~n"),
    Input = #get_queue_attributes_input{
        queue_url = QueueUrl,
        attribute_names = [all]
    },
    case sqs_client:get_queue_attributes(Config, Input) of
        {ok, #get_queue_attributes_output{attributes = Attrs}} when is_map(Attrs) ->
            io:format("SUCCESS: Queue attributes:~n"),
            print_attributes(Attrs);
        {ok, _} ->
            erlang:error({assertion_failed, get_queue_attributes_returned_no_attributes});
        {error, Reason} ->
            erlang:error({get_queue_attributes_failed, Reason})
    end,
    io:format("~n").

receive_messages(Config, QueueUrl) ->
    io:format("--- ReceiveMessage ---~n"),
    Input = #receive_message_input{
        queue_url = QueueUrl,
        max_number_of_messages = 10,
        wait_time_seconds = 1,
        message_attribute_names = [<<"All">>]
    },
    case sqs_client:receive_message(Config, Input) of
        {ok, #receive_message_output{messages = Messages}} when is_list(Messages), Messages =/= [] ->
            io:format("SUCCESS: Received ~p message(s)~n", [length(Messages)]),
            ReceivedBodies = [Body || #message{body = Body} <- Messages, Body =/= undefined],
            ExpectedBodies = lists:sort([?MSG1_BODY, ?MSG2_BODY]),
            case lists:sort(ReceivedBodies) =:= ExpectedBodies of
                true  -> io:format("SUCCESS: Message bodies match~n");
                false -> erlang:error({assertion_failed, {expected_bodies, ExpectedBodies}, {got, ReceivedBodies}})
            end,
            lists:map(
                fun(#message{message_id = MsgId, body = Body, receipt_handle = Handle}) ->
                    io:format("  Message ID: ~s~n", [format_string(MsgId)]),
                    io:format("    Body: ~s~n", [format_string(Body)]),
                    Handle
                end,
                Messages
            );
        {ok, _} ->
            erlang:error({assertion_failed, no_messages_received});
        {error, Reason} ->
            erlang:error({receive_message_failed, Reason})
    end.

delete_messages(Config, QueueUrl, ReceiptHandles) ->
    io:format("~n--- DeleteMessage ---~n"),
    lists:foreach(
        fun(undefined) ->
                ok;
           (Handle) ->
                DeleteInput = #delete_message_input{
                    queue_url = QueueUrl,
                    receipt_handle = Handle
                },
                case sqs_client:delete_message(Config, DeleteInput) of
                    {ok, _} ->
                        io:format("SUCCESS: Message deleted~n");
                    {error, Reason} ->
                        erlang:error({delete_message_failed, Reason})
                end
        end,
        ReceiptHandles
    ),
    io:format("~n").

verify_empty_queue(Config, QueueUrl) ->
    io:format("--- ReceiveMessage (verify empty) ---~n"),
    Input = #receive_message_input{
        queue_url = QueueUrl,
        max_number_of_messages = 10,
        wait_time_seconds = 1,
        message_attribute_names = [<<"All">>]
    },
    case sqs_client:receive_message(Config, Input) of
        {ok, #receive_message_output{messages = Messages}} ->
            case messages_or_empty(Messages) of
                [] -> io:format("SUCCESS: Queue is empty~n");
                Remaining -> io:format("INFO: Queue still has ~p message(s)~n", [length(Remaining)])
            end;
        {error, Reason} ->
            erlang:error({receive_message_failed, Reason})
    end,
    io:format("~n").

delete_demo_queue(Config, QueueUrl) ->
    io:format("--- DeleteQueue ---~n"),
    case sqs_client:delete_queue(Config, #delete_queue_input{queue_url = QueueUrl}) of
        {ok, _} ->
            io:format("SUCCESS: Queue '~s' deleted~n", [?QUEUE_NAME]);
        {error, #queue_does_not_exist{}} ->
            io:format("SUCCESS: Queue '~s' already absent~n", [?QUEUE_NAME]);
        {error, Reason} ->
            erlang:error({delete_queue_failed, Reason})
    end,
    io:format("~n").

print_attributes(Attrs) when is_map(Attrs) ->
    maps:foreach(
        fun(Key, Value) ->
            io:format("    ~p: ~s~n", [Key, Value])
        end,
        Attrs
    ).

messages_or_empty(undefined) -> [];
messages_or_empty(Messages) when is_list(Messages) -> Messages;
messages_or_empty(_) -> [].

format_string(undefined) -> <<"unknown">>;
format_string(Value) when is_binary(Value) -> Value.
