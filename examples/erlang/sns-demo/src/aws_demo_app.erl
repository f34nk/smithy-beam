-module(aws_demo_app).
-export([run/0]).

-define(TOPIC_NAME, <<"sns-demo-topic">>).
-define(TEST_TOPIC_NAME, <<"sns-demo-test-topic">>).

run() ->
    io:format("~n=== Running SNS Client Application ===~n~n"),

    %% Create SNS client instance with AWS credentials
    io:format("Creating SNS client...~n"),
    Config = #{
        endpoint => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        region => <<"us-east-1">>,
        service => <<"sns">>,  %% Required for SigV4 signing with custom endpoints
        credentials => #{
            access_key_id => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    },
    {ok, Client} = aws_sns_client:new(Config),
    io:format("Client created successfully~n~n"),

    %% 1. List topics to see what was created by Terraform
    io:format("--- ListTopics ---~n"),
    case aws_sns_client:list_topics(Client, #{}, #{enable_retry => false}) of
        {ok, ListOutput} ->
            Topics = get_topics_from_response(ListOutput),
            io:format("SUCCESS: Found ~p topic(s)~n", [length(Topics)]),
            lists:foreach(
                fun(Topic) ->
                    TopicArn = maps:get(<<"TopicArn">>, Topic, <<"unknown">>),
                    io:format("  - ~s~n", [TopicArn])
                end,
                Topics
            );
        {error, ListError} ->
            io:format("ERROR: ~p~n", [ListError])
    end,
    io:format("~n"),

    %% 2. Create a new topic for testing
    io:format("--- CreateTopic ---~n"),
    CreateInput = #{
        <<"Name">> => ?TEST_TOPIC_NAME,
        <<"Tags">> => [
            #{<<"Key">> => <<"Environment">>, <<"Value">> => <<"demo">>},
            #{<<"Key">> => <<"CreatedBy">>, <<"Value">> => <<"smithy-erlang">>}
        ]
    },
    TestTopicArn = case aws_sns_client:create_topic(Client, CreateInput, #{enable_retry => false}) of
        {ok, CreateOutput} ->
            Arn = maps:get(<<"TopicArn">>, CreateOutput, undefined),
            io:format("SUCCESS: Created topic: ~s~n", [Arn]),
            Arn;
        {error, CreateError} ->
            io:format("ERROR: ~p~n", [CreateError]),
            undefined
    end,
    io:format("~n"),

    case TestTopicArn of
        undefined ->
            io:format("Cannot continue without topic ARN~n");
        _ ->
            %% 3. Get topic attributes
            io:format("--- GetTopicAttributes ---~n"),
            AttrInput = #{<<"TopicArn">> => TestTopicArn},
            case aws_sns_client:get_topic_attributes(Client, AttrInput, #{enable_retry => false}) of
                {ok, AttrOutput} ->
                    Attrs = maps:get(<<"Attributes">>, AttrOutput, #{}),
                    io:format("SUCCESS: Topic attributes:~n"),
                    print_attributes(Attrs);
                {error, AttrError} ->
                    io:format("ERROR: ~p~n", [AttrError])
            end,
            io:format("~n"),

            %% 4. List tags for the topic
            io:format("--- ListTagsForResource ---~n"),
            TagsInput = #{<<"ResourceArn">> => TestTopicArn},
            case aws_sns_client:list_tags_for_resource(Client, TagsInput, #{enable_retry => false}) of
                {ok, TagsOutput} ->
                    Tags = get_tags_from_response(TagsOutput),
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

            %% 5. Subscribe to the topic (using email-json protocol as an example)
            %% In LocalStack, subscriptions are auto-confirmed
            io:format("--- Subscribe ---~n"),
            SubscribeInput = #{
                <<"TopicArn">> => TestTopicArn,
                <<"Protocol">> => <<"email-json">>,
                <<"Endpoint">> => <<"test@example.com">>
            },
            SubscriptionArn = case aws_sns_client:subscribe(Client, SubscribeInput, #{enable_retry => false}) of
                {ok, SubscribeOutput} ->
                    SubArn = maps:get(<<"SubscriptionArn">>, SubscribeOutput, <<"pending">>),
                    io:format("SUCCESS: Subscription ARN: ~s~n", [SubArn]),
                    SubArn;
                {error, SubscribeError} ->
                    io:format("ERROR: ~p~n", [SubscribeError]),
                    undefined
            end,
            io:format("~n"),

            %% 6. List subscriptions by topic
            io:format("--- ListSubscriptionsByTopic ---~n"),
            ListSubsInput = #{<<"TopicArn">> => TestTopicArn},
            case aws_sns_client:list_subscriptions_by_topic(Client, ListSubsInput, #{enable_retry => false}) of
                {ok, ListSubsOutput} ->
                    Subscriptions = get_subscriptions_from_response(ListSubsOutput),
                    io:format("SUCCESS: Found ~p subscription(s):~n", [length(Subscriptions)]),
                    lists:foreach(
                        fun(Sub) ->
                            SubArn2 = maps:get(<<"SubscriptionArn">>, Sub, <<"unknown">>),
                            Protocol = maps:get(<<"Protocol">>, Sub, <<"unknown">>),
                            Endpoint = maps:get(<<"Endpoint">>, Sub, <<"unknown">>),
                            io:format("    Protocol: ~s, Endpoint: ~s~n", [Protocol, Endpoint]),
                            io:format("    ARN: ~s~n", [SubArn2])
                        end,
                        Subscriptions
                    );
                {error, ListSubsError} ->
                    io:format("ERROR: ~p~n", [ListSubsError])
            end,
            io:format("~n"),

            %% 7. Publish a message to the topic
            io:format("--- Publish ---~n"),
            MessageBody = jsx:encode(#{
                <<"message">> => <<"Hello from Erlang!">>,
                <<"timestamp">> => list_to_binary(calendar:system_time_to_rfc3339(erlang:system_time(second))),
                <<"source">> => <<"smithy-erlang-sns-demo">>
            }),
            PublishInput = #{
                <<"TopicArn">> => TestTopicArn,
                <<"Message">> => MessageBody,
                <<"Subject">> => <<"Test message from Smithy-Erlang">>,
                <<"MessageAttributes">> => #{
                    <<"Author">> => #{
                        <<"DataType">> => <<"String">>,
                        <<"StringValue">> => <<"Smithy-Erlang Demo">>
                    }
                }
            },
            case aws_sns_client:publish(Client, PublishInput, #{enable_retry => false}) of
                {ok, PublishOutput} ->
                    MessageId = maps:get(<<"MessageId">>, PublishOutput, <<"unknown">>),
                    io:format("SUCCESS: Published message, ID: ~s~n", [MessageId]);
                {error, PublishError} ->
                    io:format("ERROR: ~p~n", [PublishError])
            end,
            io:format("~n"),

            %% 8. Publish another message
            io:format("--- Publish (second message) ---~n"),
            Message2 = <<"This is a plain text message from the Erlang SNS demo.">>,
            PublishInput2 = #{
                <<"TopicArn">> => TestTopicArn,
                <<"Message">> => Message2
            },
            case aws_sns_client:publish(Client, PublishInput2, #{enable_retry => false}) of
                {ok, PublishOutput2} ->
                    MessageId2 = maps:get(<<"MessageId">>, PublishOutput2, <<"unknown">>),
                    io:format("SUCCESS: Published message, ID: ~s~n", [MessageId2]);
                {error, PublishError2} ->
                    io:format("ERROR: ~p~n", [PublishError2])
            end,
            io:format("~n"),

            %% 9. Unsubscribe
            case SubscriptionArn of
                undefined ->
                    io:format("--- Unsubscribe (skipped - no subscription) ---~n~n");
                <<"pending">> ->
                    io:format("--- Unsubscribe (skipped - pending confirmation) ---~n~n");
                <<"PendingConfirmation">> ->
                    io:format("--- Unsubscribe (skipped - pending confirmation) ---~n~n");
                ValidArn ->
                    io:format("--- Unsubscribe ---~n"),
                    UnsubInput = #{<<"SubscriptionArn">> => ValidArn},
                    case aws_sns_client:unsubscribe(Client, UnsubInput, #{enable_retry => false}) of
                        {ok, _} ->
                            io:format("SUCCESS: Unsubscribed~n");
                        {error, UnsubError} ->
                            io:format("ERROR: ~p~n", [UnsubError])
                    end,
                    io:format("~n")
            end,

            %% 10. Delete the test topic
            io:format("--- DeleteTopic ---~n"),
            DeleteInput = #{<<"TopicArn">> => TestTopicArn},
            case aws_sns_client:delete_topic(Client, DeleteInput, #{enable_retry => false}) of
                {ok, _} ->
                    io:format("SUCCESS: Topic deleted~n");
                {error, DeleteError} ->
                    io:format("ERROR: ~p~n", [DeleteError])
            end,
            io:format("~n"),

            %% 11. Verify topic was deleted by listing again
            io:format("--- ListTopics (verify deletion) ---~n"),
            case aws_sns_client:list_topics(Client, #{}, #{enable_retry => false}) of
                {ok, VerifyOutput} ->
                    VerifyTopics = get_topics_from_response(VerifyOutput),
                    TestTopicExists = lists:any(
                        fun(T) ->
                            Arn2 = maps:get(<<"TopicArn">>, T, <<>>),
                            binary:match(Arn2, ?TEST_TOPIC_NAME) =/= nomatch
                        end,
                        VerifyTopics
                    ),
                    case TestTopicExists of
                        false ->
                            io:format("SUCCESS: Test topic no longer exists~n");
                        true ->
                            io:format("INFO: Test topic still exists (may take time to propagate)~n")
                    end,
                    io:format("Remaining topics: ~p~n", [length(VerifyTopics)]);
                {error, VerifyError} ->
                    io:format("ERROR: ~p~n", [VerifyError])
            end
    end,
    io:format("~n"),

    io:format("=== SNS Client Application Complete ===~n"),
    ok.

%% Helper to extract topics from ListTopics response
%% Handles the XML-based response format from AWS Query protocol
get_topics_from_response(Response) ->
    case maps:get(<<"Topics">>, Response, undefined) of
        undefined ->
            [];
        TopicsWrapper when is_map(TopicsWrapper) ->
            %% Response may have nested "member" key for lists
            case maps:get(<<"member">>, TopicsWrapper, undefined) of
                undefined -> [];
                Member when is_list(Member) -> Member;
                Member when is_map(Member) -> [Member]
            end;
        Topics when is_list(Topics) ->
            Topics
    end.

%% Helper to extract tags from ListTagsForResource response
get_tags_from_response(Response) ->
    case maps:get(<<"Tags">>, Response, undefined) of
        undefined -> [];
        TagsWrapper when is_map(TagsWrapper) ->
            case maps:get(<<"member">>, TagsWrapper, undefined) of
                undefined -> [];
                Member when is_list(Member) -> Member;
                Member when is_map(Member) -> [Member]
            end;
        Tags when is_list(Tags) -> Tags
    end.

%% Helper to extract subscriptions from ListSubscriptionsByTopic response
get_subscriptions_from_response(Response) ->
    case maps:get(<<"Subscriptions">>, Response, undefined) of
        undefined -> [];
        SubsWrapper when is_map(SubsWrapper) ->
            case maps:get(<<"member">>, SubsWrapper, undefined) of
                undefined -> [];
                Member when is_list(Member) -> Member;
                Member when is_map(Member) -> [Member]
            end;
        Subs when is_list(Subs) -> Subs
    end.

%% Helper to print topic attributes
%% Handles AWS Query response format where attributes come as entry list
print_attributes(Attrs) when is_map(Attrs) ->
    case maps:get(<<"entry">>, Attrs, undefined) of
        undefined ->
            %% Direct map format
            maps:foreach(
                fun(Key, Value) ->
                    io:format("    ~s: ~s~n", [Key, format_value(Value)])
                end,
                Attrs
            );
        Entries when is_list(Entries) ->
            %% Entry list format from AWS Query
            lists:foreach(
                fun(Entry) ->
                    Key = maps:get(<<"key">>, Entry, <<"unknown">>),
                    Value = maps:get(<<"value">>, Entry, <<>>),
                    io:format("    ~s: ~s~n", [Key, format_value(Value)])
                end,
                Entries
            );
        Entry when is_map(Entry) ->
            %% Single entry
            Key = maps:get(<<"key">>, Entry, <<"unknown">>),
            Value = maps:get(<<"value">>, Entry, <<>>),
            io:format("    ~s: ~s~n", [Key, format_value(Value)])
    end.

format_value(Value) when is_binary(Value) -> Value;
format_value(Value) when is_integer(Value) -> integer_to_binary(Value);
format_value(Value) when is_atom(Value) -> atom_to_binary(Value, utf8);
format_value(#{} = Map) when map_size(Map) =:= 0 -> <<"(empty)">>;
format_value(Value) -> io_lib:format("~p", [Value]).
