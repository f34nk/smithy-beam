-module(aws_query_test).
-include_lib("eunit/include/eunit.hrl").

%%%===================================================================
%%% Test Suite for AWS Query Protocol Encoding
%%%===================================================================

%%--------------------------------------------------------------------
%% Test: Basic encoding
%%--------------------------------------------------------------------
encode_simple_action_test() ->
    Result = aws_query:encode(<<"SendMessage">>, #{}),
    ?assert(is_binary(Result)),
    ?assert(binary:match(Result, <<"Action=SendMessage">>) =/= nomatch).

encode_with_version_test() ->
    Result = aws_query:encode(<<"SendMessage">>, #{}, <<"2012-11-05">>),
    ?assert(binary:match(Result, <<"Action=SendMessage">>) =/= nomatch),
    ?assert(binary:match(Result, <<"Version=2012-11-05">>) =/= nomatch).

encode_string_action_test() ->
    Result = aws_query:encode("CreateQueue", #{}),
    ?assert(binary:match(Result, <<"Action=CreateQueue">>) =/= nomatch).

%%--------------------------------------------------------------------
%% Test: Simple parameters
%%--------------------------------------------------------------------
encode_simple_params_test() ->
    Params = #{<<"QueueName">> => <<"my-queue">>},
    Result = aws_query:encode(<<"CreateQueue">>, Params),
    ?assert(binary:match(Result, <<"QueueName=my-queue">>) =/= nomatch).

encode_multiple_params_test() ->
    Params = #{
        <<"QueueName">> => <<"my-queue">>,
        <<"DelaySeconds">> => <<"10">>
    },
    Result = aws_query:encode(<<"CreateQueue">>, Params),
    ?assert(binary:match(Result, <<"QueueName=my-queue">>) =/= nomatch),
    ?assert(binary:match(Result, <<"DelaySeconds=10">>) =/= nomatch).

encode_integer_value_test() ->
    Params = #{<<"MaxNumberOfMessages">> => 10},
    Result = aws_query:encode(<<"ReceiveMessage">>, Params),
    ?assert(binary:match(Result, <<"MaxNumberOfMessages=10">>) =/= nomatch).

encode_boolean_true_test() ->
    Params = #{<<"Enabled">> => true},
    Result = aws_query:encode(<<"TestAction">>, Params),
    ?assert(binary:match(Result, <<"Enabled=true">>) =/= nomatch).

encode_boolean_false_test() ->
    Params = #{<<"Enabled">> => false},
    Result = aws_query:encode(<<"TestAction">>, Params),
    ?assert(binary:match(Result, <<"Enabled=false">>) =/= nomatch).

%%--------------------------------------------------------------------
%% Test: Nested structures (dot notation)
%%--------------------------------------------------------------------
encode_nested_map_test() ->
    Params = #{
        <<"Attribute">> => #{
            <<"Name">> => <<"Key">>,
            <<"Value">> => <<"12345">>
        }
    },
    Result = aws_query:encode(<<"SetQueueAttributes">>, Params),
    ?assert(binary:match(Result, <<"Attribute.Name=Key">>) =/= nomatch),
    ?assert(binary:match(Result, <<"Attribute.Value=12345">>) =/= nomatch).

encode_deeply_nested_test() ->
    Params = #{
        <<"Level1">> => #{
            <<"Level2">> => #{
                <<"Level3">> => <<"value">>
            }
        }
    },
    Result = aws_query:encode(<<"TestAction">>, Params),
    ?assert(binary:match(Result, <<"Level1.Level2.Level3=value">>) =/= nomatch).

%%--------------------------------------------------------------------
%% Test: List encoding with 1-based indexing
%%--------------------------------------------------------------------
encode_list_of_strings_test() ->
    Params = #{
        <<"Tags">> => [<<"tag1">>, <<"tag2">>, <<"tag3">>]
    },
    Result = aws_query:encode(<<"TestAction">>, Params),
    ?assert(binary:match(Result, <<"Tags.1=tag1">>) =/= nomatch),
    ?assert(binary:match(Result, <<"Tags.2=tag2">>) =/= nomatch),
    ?assert(binary:match(Result, <<"Tags.3=tag3">>) =/= nomatch).

encode_list_of_maps_test() ->
    Params = #{
        <<"Attributes">> => [
            #{<<"Name">> => <<"Key1">>, <<"Value">> => <<"Val1">>},
            #{<<"Name">> => <<"Key2">>, <<"Value">> => <<"Val2">>}
        ]
    },
    Result = aws_query:encode(<<"TestAction">>, Params),
    ?assert(binary:match(Result, <<"Attributes.1.Name=Key1">>) =/= nomatch),
    ?assert(binary:match(Result, <<"Attributes.1.Value=Val1">>) =/= nomatch),
    ?assert(binary:match(Result, <<"Attributes.2.Name=Key2">>) =/= nomatch),
    ?assert(binary:match(Result, <<"Attributes.2.Value=Val2">>) =/= nomatch).

encode_empty_list_test() ->
    Params = #{<<"Items">> => []},
    Result = aws_query:encode(<<"TestAction">>, Params),
    %% Empty list should not add any Items.N parameters
    ?assertEqual(nomatch, binary:match(Result, <<"Items">>)).

%%--------------------------------------------------------------------
%% Test: SQS-style message attributes
%%--------------------------------------------------------------------
encode_sqs_message_attributes_test() ->
    Params = #{
        <<"QueueUrl">> => <<"https://sqs.us-east-1.amazonaws.com/123456789012/my-queue">>,
        <<"MessageBody">> => <<"Hello World">>,
        <<"MessageAttribute">> => [
            #{
                <<"Name">> => <<"CustomAttribute">>,
                <<"Value">> => #{
                    <<"DataType">> => <<"String">>,
                    <<"StringValue">> => <<"CustomValue">>
                }
            }
        ]
    },
    Result = aws_query:encode(<<"SendMessage">>, Params),
    ?assert(binary:match(Result, <<"MessageAttribute.1.Name=CustomAttribute">>) =/= nomatch),
    ?assert(binary:match(Result, <<"MessageAttribute.1.Value.DataType=String">>) =/= nomatch),
    ?assert(
        binary:match(Result, <<"MessageAttribute.1.Value.StringValue=CustomValue">>) =/= nomatch
    ).

%%--------------------------------------------------------------------
%% Test: URL encoding of special characters
%%--------------------------------------------------------------------
encode_special_chars_test() ->
    Params = #{<<"Message">> => <<"Hello World!">>},
    Result = aws_query:encode(<<"TestAction">>, Params),
    %% Space should be encoded as %20
    ?assert(binary:match(Result, <<"Message=Hello%20World%21">>) =/= nomatch).

encode_ampersand_test() ->
    Params = #{<<"Query">> => <<"a&b">>},
    Result = aws_query:encode(<<"TestAction">>, Params),
    %% & should be encoded as %26
    ?assert(binary:match(Result, <<"Query=a%26b">>) =/= nomatch).

encode_equals_sign_test() ->
    Params = #{<<"Expression">> => <<"a=b">>},
    Result = aws_query:encode(<<"TestAction">>, Params),
    %% = should be encoded as %3D
    ?assert(binary:match(Result, <<"Expression=a%3Db">>) =/= nomatch).

%%--------------------------------------------------------------------
%% Test: Helper functions
%%--------------------------------------------------------------------
flatten_params_empty_test() ->
    Result = aws_query:flatten_params(#{}),
    ?assertEqual([], Result).

flatten_params_simple_test() ->
    Result = aws_query:flatten_params(#{<<"Key">> => <<"Value">>}),
    ?assertEqual([{<<"Key">>, <<"Value">>}], Result).

to_query_key_binary_test() ->
    ?assertEqual(<<"Key">>, aws_query:to_query_key(<<"Key">>)).

to_query_key_atom_test() ->
    ?assertEqual(<<"key">>, aws_query:to_query_key(key)).

to_query_key_string_test() ->
    ?assertEqual(<<"Key">>, aws_query:to_query_key("Key")).

to_query_value_binary_test() ->
    ?assertEqual(<<"value">>, aws_query:to_query_value(<<"value">>)).

to_query_value_integer_test() ->
    ?assertEqual(<<"42">>, aws_query:to_query_value(42)).

to_query_value_atom_test() ->
    ?assertEqual(<<"enabled">>, aws_query:to_query_value(enabled)).

%%--------------------------------------------------------------------
%% Test: Real AWS service examples
%%--------------------------------------------------------------------

%% SQS CreateQueue
sqs_create_queue_test() ->
    Params = #{
        <<"QueueName">> => <<"my-queue">>,
        <<"Attribute">> => [
            #{<<"Name">> => <<"DelaySeconds">>, <<"Value">> => <<"5">>},
            #{<<"Name">> => <<"MaximumMessageSize">>, <<"Value">> => <<"262144">>}
        ]
    },
    Result = aws_query:encode(<<"CreateQueue">>, Params, <<"2012-11-05">>),
    ?assert(binary:match(Result, <<"Action=CreateQueue">>) =/= nomatch),
    ?assert(binary:match(Result, <<"Version=2012-11-05">>) =/= nomatch),
    ?assert(binary:match(Result, <<"QueueName=my-queue">>) =/= nomatch).

%% SNS Publish
sns_publish_test() ->
    Params = #{
        <<"TopicArn">> => <<"arn:aws:sns:us-east-1:123456789012:my-topic">>,
        <<"Message">> => <<"Hello from SNS">>
    },
    Result = aws_query:encode(<<"Publish">>, Params, <<"2010-03-31">>),
    ?assert(binary:match(Result, <<"Action=Publish">>) =/= nomatch),
    ?assert(binary:match(Result, <<"TopicArn=arn">>) =/= nomatch).

%% CloudFormation CreateStack
cloudformation_create_stack_test() ->
    Params = #{
        <<"StackName">> => <<"my-stack">>,
        <<"TemplateURL">> => <<"https://s3.amazonaws.com/mybucket/mytemplate.json">>,
        <<"Parameters">> => [
            #{<<"ParameterKey">> => <<"KeyName">>, <<"ParameterValue">> => <<"my-key">>}
        ]
    },
    Result = aws_query:encode(<<"CreateStack">>, Params),
    ?assert(binary:match(Result, <<"StackName=my-stack">>) =/= nomatch),
    ?assert(binary:match(Result, <<"Parameters.1.ParameterKey=KeyName">>) =/= nomatch),
    ?assert(binary:match(Result, <<"Parameters.1.ParameterValue=my-key">>) =/= nomatch).

%%--------------------------------------------------------------------
%% Test: Edge cases
%%--------------------------------------------------------------------
encode_empty_string_value_test() ->
    Params = #{<<"Key">> => <<>>},
    Result = aws_query:encode(<<"TestAction">>, Params),
    ?assert(binary:match(Result, <<"Key=">>) =/= nomatch).

encode_atom_key_test() ->
    Params = #{queue_name => <<"my-queue">>},
    Result = aws_query:encode(<<"TestAction">>, Params),
    ?assert(binary:match(Result, <<"queue_name=my-queue">>) =/= nomatch).

encode_mixed_key_types_test() ->
    Params = #{
        <<"BinaryKey">> => <<"value1">>,
        atom_key => <<"value2">>,
        "StringKey" => <<"value3">>
    },
    Result = aws_query:encode(<<"TestAction">>, Params),
    ?assert(binary:match(Result, <<"BinaryKey=value1">>) =/= nomatch),
    ?assert(binary:match(Result, <<"atom_key=value2">>) =/= nomatch),
    ?assert(binary:match(Result, <<"StringKey=value3">>) =/= nomatch).
