-module(demo_app).
-export([run/0]).

-include("sns_types.hrl").

-define(TOPIC_NAME, <<"sns-demo-erlang-topic">>).
-define(TEST_TOPIC_NAME, <<"sns-demo-erlang-test-topic">>).

run() ->
    {ok, _} = application:ensure_all_started(aws_credentials),
    io:format("~n=== Running SNS Client Application ===~n~n"),

    Config = client_config(),
    io:format("SNS client configured for ~s~n~n", [maps:get(base_url, Config)]),

    %% 1. List topics to see what was created by Terraform
    io:format("--- ListTopics ---~n"),
    case sns_client:list_topics(Config, #list_topics_input{}) of
        {ok, TopicList} when is_list(TopicList) ->
            case TopicList of
                [] -> erlang:error({assertion_failed, empty_topic_list});
                _  -> io:format("SUCCESS: Found ~p topic(s)~n", [length(TopicList)])
            end,
            lists:foreach(
                fun(#topic{topic_arn = TopicArn}) ->
                    io:format("  - ~s~n", [format_binary(TopicArn)])
                end,
                TopicList
            );
        {error, ListError} ->
            erlang:error({list_topics_failed, ListError})
    end,
    io:format("~n"),

    %% 2. Create a new topic for testing
    io:format("--- CreateTopic ---~n"),
    CreateInput = #create_topic_input{
        name = ?TEST_TOPIC_NAME,
        tags = [
            #tag{key = <<"Environment">>, value = <<"demo">>},
            #tag{key = <<"CreatedBy">>, value = <<"smithy-erlang">>}
        ]
    },
    TestTopicArn = case sns_client:create_topic(Config, CreateInput) of
        {ok, #create_topic_output{topic_arn = Arn}} ->
            case binary:match(format_binary(Arn), ?TEST_TOPIC_NAME) of
                nomatch -> erlang:error({assertion_failed, {arn_missing_topic_name, Arn}});
                _       -> io:format("SUCCESS: ARN = ~s~n", [Arn])
            end,
            Arn;
        {error, CreateError} ->
            erlang:error({create_topic_failed, CreateError})
    end,
    io:format("~n"),

    %% 3. Get topic attributes
    io:format("--- GetTopicAttributes ---~n"),
    AttrInput = #get_topic_attributes_input{topic_arn = TestTopicArn},
    case sns_client:get_topic_attributes(Config, AttrInput) of
        {ok, #get_topic_attributes_output{attributes = Attrs}} ->
            io:format("SUCCESS: Topic attributes:~n"),
            print_attributes(ensure_map(Attrs));
        {error, AttrError} ->
            erlang:error({get_topic_attributes_failed, AttrError})
    end,
    io:format("~n"),

    %% 4. List tags for the topic
    io:format("--- ListTagsForResource ---~n"),
    TagsInput = #list_tags_for_resource_input{resource_arn = TestTopicArn},
    case sns_client:list_tags_for_resource(Config, TagsInput) of
        {ok, #list_tags_for_resource_output{tags = Tags}} ->
            TagList = ensure_list(Tags),
            io:format("SUCCESS: Found ~p tag(s):~n", [length(TagList)]),
            lists:foreach(
                fun(#tag{key = Key, value = Value}) ->
                    io:format("    ~s = ~s~n", [format_binary(Key), format_binary(Value)])
                end,
                TagList
            );
        {error, TagsError} ->
            erlang:error({list_tags_for_resource_failed, TagsError})
    end,
    io:format("~n"),

    %% 5. Subscribe to the topic
    io:format("--- Subscribe ---~n"),
    SubscribeInput = #subscribe_input{
        topic_arn = TestTopicArn,
        protocol = <<"email-json">>,
        endpoint = <<"test@example.com">>
    },
    SubscriptionArn = case sns_client:subscribe(Config, SubscribeInput) of
        {ok, #subscribe_output{subscription_arn = SubArn}} ->
            case is_subscription_arn(SubArn) of
                true  -> io:format("SUCCESS: Subscription ARN: ~s~n", [SubArn]);
                false -> io:format("SUCCESS: Subscription pending confirmation~n")
            end,
            SubArn;
        {error, SubscribeError} ->
            erlang:error({subscribe_failed, SubscribeError})
    end,
    io:format("~n"),

    %% 6. List subscriptions by topic
    io:format("--- ListSubscriptionsByTopic ---~n"),
    ListSubsInput = #list_subscriptions_by_topic_input{topic_arn = TestTopicArn},
    case sns_client:list_subscriptions_by_topic(Config, ListSubsInput) of
        {ok, SubList} when is_list(SubList) ->
            case SubList of
                [] -> erlang:error({assertion_failed, empty_subscription_list});
                _  -> io:format("SUCCESS: Found ~p subscription(s):~n", [length(SubList)])
            end,
            lists:foreach(
                fun(#subscription{
                    subscription_arn = SubArn2,
                    protocol = Protocol,
                    endpoint = Endpoint
                }) ->
                    io:format("    Protocol: ~s, Endpoint: ~s~n", [
                        format_binary(Protocol), format_binary(Endpoint)
                    ]),
                    io:format("    ARN: ~s~n", [format_binary(SubArn2)])
                end,
                SubList
            );
        {error, ListSubsError} ->
            erlang:error({list_subscriptions_by_topic_failed, ListSubsError})
    end,
    io:format("~n"),

    %% 7. Publish a message to the topic
    io:format("--- Publish ---~n"),
    MessageBody = jsone:encode(#{
        <<"message">> => <<"Hello from Erlang!">>,
        <<"timestamp">> => list_to_binary(calendar:system_time_to_rfc3339(erlang:system_time(second))),
        <<"source">> => <<"smithy-erlang-sns-demo">>
    }),
    PublishInput = #publish_input{
        topic_arn = TestTopicArn,
        message = MessageBody,
        subject = <<"Test message from Smithy-Erlang">>,
        message_attributes = #{
            <<"Author">> => #message_attribute_value{
                data_type = <<"String">>,
                string_value = <<"Smithy-Erlang Demo">>
            }
        }
    },
    case sns_client:publish(Config, PublishInput) of
        {ok, #publish_output{message_id = MessageId}} ->
            case is_non_empty_binary(MessageId) of
                true  -> io:format("SUCCESS: Published message, ID: ~s~n", [MessageId]);
                false -> erlang:error({assertion_failed, publish_returned_empty_id})
            end;
        {error, PublishError} ->
            erlang:error({publish_failed, PublishError})
    end,
    io:format("~n"),

    %% 8. Publish another message
    io:format("--- Publish (second message) ---~n"),
    PublishInput2 = #publish_input{
        topic_arn = TestTopicArn,
        message = <<"This is a plain text message from the Erlang SNS demo.">>
    },
    case sns_client:publish(Config, PublishInput2) of
        {ok, #publish_output{message_id = MessageId2}} ->
            case is_non_empty_binary(MessageId2) of
                true  -> io:format("SUCCESS: Published message, ID: ~s~n", [MessageId2]);
                false -> erlang:error({assertion_failed, publish_returned_empty_id})
            end;
        {error, PublishError2} ->
            erlang:error({publish_failed, PublishError2})
    end,
    io:format("~n"),

    %% 9. Unsubscribe
    case is_subscription_arn(SubscriptionArn) of
        false ->
            io:format("--- Unsubscribe (skipped - pending confirmation) ---~n~n");
        true ->
            io:format("--- Unsubscribe ---~n"),
            UnsubInput = #unsubscribe_input{subscription_arn = SubscriptionArn},
            case sns_client:unsubscribe(Config, UnsubInput) of
                {ok, _} ->
                    io:format("SUCCESS: Unsubscribed~n");
                {error, UnsubError} ->
                    erlang:error({unsubscribe_failed, UnsubError})
            end,
            io:format("~n")
    end,

    %% 10. Delete the test topic
    io:format("--- DeleteTopic ---~n"),
    DeleteInput = #delete_topic_input{topic_arn = TestTopicArn},
    case sns_client:delete_topic(Config, DeleteInput) of
        {ok, _} ->
            io:format("SUCCESS: Topic deleted~n");
        {error, DeleteError} ->
            erlang:error({delete_topic_failed, DeleteError})
    end,
    io:format("~n"),

    %% 11. Verify topic was deleted by listing again
    io:format("--- ListTopics (verify deletion) ---~n"),
    case sns_client:list_topics(Config, #list_topics_input{}) of
        {ok, VerifyTopicList} when is_list(VerifyTopicList) ->
            TestTopicExists = lists:any(
                fun(#topic{topic_arn = Arn2}) ->
                    binary:match(format_binary(Arn2), ?TEST_TOPIC_NAME) =/= nomatch
                end,
                VerifyTopicList
            ),
            case TestTopicExists of
                false ->
                    io:format("SUCCESS: Test topic no longer exists~n");
                true ->
                    erlang:error({assertion_failed, topic_still_exists})
            end,
            io:format("Remaining topics: ~p~n", [length(VerifyTopicList)]);
        {error, VerifyError} ->
            erlang:error({list_topics_failed, VerifyError})
    end,
    io:format("~n"),

    io:format("=== SNS Client Application Complete ===~n"),
    ok.

ensure_list(undefined) -> [];
ensure_list(List) when is_list(List) -> List.

ensure_map(undefined) -> #{};
ensure_map(Map) when is_map(Map) -> Map.

is_non_empty_binary(Value) when is_binary(Value) -> byte_size(Value) > 0;
is_non_empty_binary(_) -> false.

is_subscription_arn(undefined) -> false;
is_subscription_arn(<<>>) -> false;
is_subscription_arn(Arn) when is_binary(Arn) ->
    case binary:match(Arn, <<"arn:">>) of
        nomatch -> false;
        _       -> true
    end;
is_subscription_arn(_) -> false.

print_attributes(Attrs) when is_map(Attrs) ->
    maps:foreach(
        fun(Key, Value) ->
            io:format("    ~s: ~s~n", [format_binary(Key), format_binary(Value)])
        end,
        Attrs
    ).

format_binary(undefined) -> <<"?">>;
format_binary(Value) when is_binary(Value) -> Value;
format_binary(Value) when is_atom(Value) -> atom_to_binary(Value, utf8);
format_binary(Value) -> io_lib:format("~p", [Value]).

client_config() ->
    #{
        region => <<"us-east-1">>,
        endpoint_prefix => <<"sns">>,
        signing_name => <<"sns">>,
        base_url => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        credentials => #{
            access_key_id => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    }.
