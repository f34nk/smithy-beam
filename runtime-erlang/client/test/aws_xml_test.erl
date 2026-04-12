-module(aws_xml_test).
-include_lib("eunit/include/eunit.hrl").

%%%===================================================================
%%% Test Suite for AWS XML Encoding/Decoding
%%%===================================================================

%%--------------------------------------------------------------------
%% Test: Basic encoding
%%--------------------------------------------------------------------
encode_simple_map_test() ->
    Map = #{<<"Name">> => <<"John">>, <<"Age">> => <<"30">>},
    Result = aws_xml:encode(Map),
    ?assert(is_list(Result) orelse is_binary(Result)).

encode_with_root_element_test() ->
    Map = #{<<"Name">> => <<"John">>},
    Result = aws_xml:encode(Map, <<"Person">>),
    ResultBin = iolist_to_binary(Result),
    ?assert(binary:match(ResultBin, <<"<Person>">>) =/= nomatch),
    ?assert(binary:match(ResultBin, <<"</Person>">>) =/= nomatch).

encode_empty_map_test() ->
    Map = #{},
    Result = aws_xml:encode(Map),
    ?assertEqual([], Result).

%%--------------------------------------------------------------------
%% Test: Nested structure encoding
%%--------------------------------------------------------------------
encode_nested_map_test() ->
    Map = #{
        <<"Person">> => #{
            <<"Name">> => <<"John">>,
            <<"Address">> => #{
                <<"City">> => <<"Seattle">>,
                <<"State">> => <<"WA">>
            }
        }
    },
    Result = aws_xml:encode(Map),
    ?assert(is_list(Result)).

encode_deeply_nested_test() ->
    Map = #{
        <<"Level1">> => #{
            <<"Level2">> => #{
                <<"Level3">> => #{
                    <<"Value">> => <<"deep">>
                }
            }
        }
    },
    Result = aws_xml:encode(Map),
    ?assert(is_list(Result)).

%%--------------------------------------------------------------------
%% Test: List encoding
%%--------------------------------------------------------------------
encode_list_of_strings_test() ->
    Map = #{<<"Items">> => [<<"a">>, <<"b">>, <<"c">>]},
    Result = aws_xml:encode(Map),
    ?assert(is_list(Result)).

encode_list_of_maps_test() ->
    Map = #{
        <<"Items">> => [
            #{<<"Name">> => <<"Item1">>},
            #{<<"Name">> => <<"Item2">>}
        ]
    },
    Result = aws_xml:encode(Map),
    ?assert(is_list(Result)).

%%--------------------------------------------------------------------
%% Test: Value type encoding
%%--------------------------------------------------------------------
encode_binary_value_test() ->
    Result = aws_xml:encode_value(<<"hello">>),
    ?assertEqual(["hello"], Result).

encode_atom_value_test() ->
    Result = aws_xml:encode_value(hello),
    ?assertEqual(["hello"], Result).

encode_integer_value_test() ->
    Result = aws_xml:encode_value(42),
    ?assertEqual(["42"], Result).

encode_float_value_test() ->
    Result = aws_xml:encode_value(3.14),
    ?assert(is_list(Result)).

encode_boolean_true_test() ->
    Result = aws_xml:encode_value(true),
    ?assertEqual(["true"], Result).

encode_boolean_false_test() ->
    Result = aws_xml:encode_value(false),
    ?assertEqual(["false"], Result).

encode_undefined_value_test() ->
    Result = aws_xml:encode_value(undefined),
    ?assertEqual([], Result).

%%--------------------------------------------------------------------
%% Test: Basic decoding
%%--------------------------------------------------------------------
decode_simple_xml_test() ->
    Xml = <<"<Person><Name>John</Name></Person>">>,
    {ok, Result} = aws_xml:decode(Xml),
    ?assert(is_map(Result)),
    ?assert(maps:is_key(<<"Person">>, Result)).

decode_with_text_content_test() ->
    Xml = <<"<Name>John</Name>">>,
    {ok, Result} = aws_xml:decode(Xml),
    ?assertEqual(#{<<"Name">> => <<"John">>}, Result).

decode_empty_element_test() ->
    Xml = <<"<Empty></Empty>">>,
    {ok, Result} = aws_xml:decode(Xml),
    ?assert(is_map(Result)).

decode_string_input_test() ->
    Xml = "<Name>John</Name>",
    {ok, Result} = aws_xml:decode(Xml),
    ?assert(is_map(Result)).

%%--------------------------------------------------------------------
%% Test: Nested structure decoding
%%--------------------------------------------------------------------
decode_nested_xml_test() ->
    Xml = <<"<Person><Name>John</Name><Address><City>Seattle</City></Address></Person>">>,
    {ok, Result} = aws_xml:decode(Xml),
    ?assert(maps:is_key(<<"Person">>, Result)).

decode_deeply_nested_test() ->
    Xml = <<"<A><B><C><D>value</D></C></B></A>">>,
    {ok, Result} = aws_xml:decode(Xml),
    ?assert(is_map(Result)).

%%--------------------------------------------------------------------
%% Test: List handling in decoding
%%--------------------------------------------------------------------
decode_duplicate_elements_test() ->
    Xml = <<"<Root><Item>1</Item><Item>2</Item><Item>3</Item></Root>">>,
    {ok, Result} = aws_xml:decode(Xml),
    Root = maps:get(<<"Root">>, Result),
    Items = maps:get(<<"Item">>, Root),
    ?assert(is_list(Items)),
    ?assertEqual(3, length(Items)).

%%--------------------------------------------------------------------
%% Test: Error handling
%%--------------------------------------------------------------------
decode_invalid_xml_test() ->
    Xml = <<"<Invalid><Unclosed>">>,
    Result = aws_xml:decode(Xml),
    ?assertMatch({error, {xml_parse_error, _}}, Result).

decode_empty_input_test() ->
    Result = aws_xml:decode(<<>>),
    ?assertMatch({error, {xml_parse_error, _}}, Result).

%%--------------------------------------------------------------------
%% Test: AWS S3-style XML
%%--------------------------------------------------------------------
decode_s3_list_buckets_response_test() ->
    Xml =
        <<"<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
            "<ListAllMyBucketsResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">",
            "<Owner><ID>abc123</ID><DisplayName>myuser</DisplayName></Owner>", "<Buckets>",
            "<Bucket><Name>bucket1</Name><CreationDate>2024-01-01T00:00:00.000Z</CreationDate></Bucket>",
            "<Bucket><Name>bucket2</Name><CreationDate>2024-01-02T00:00:00.000Z</CreationDate></Bucket>",
            "</Buckets>", "</ListAllMyBucketsResult>">>,
    {ok, Result} = aws_xml:decode(Xml),
    ?assert(maps:is_key(<<"ListAllMyBucketsResult">>, Result)).

decode_s3_error_response_test() ->
    Xml =
        <<"<?xml version=\"1.0\" encoding=\"UTF-8\"?>", "<Error>", "<Code>NoSuchBucket</Code>",
            "<Message>The specified bucket does not exist</Message>",
            "<BucketName>mybucket</BucketName>", "<RequestId>abc123</RequestId>", "</Error>">>,
    {ok, Result} = aws_xml:decode(Xml),
    ErrorMap = maps:get(<<"Error">>, Result),
    ?assertEqual(<<"NoSuchBucket">>, maps:get(<<"Code">>, ErrorMap)).

%%--------------------------------------------------------------------
%% Test: Round-trip encoding/decoding
%%--------------------------------------------------------------------
roundtrip_simple_test() ->
    Original = #{<<"Name">> => <<"John">>, <<"Age">> => <<"30">>},
    Encoded = aws_xml:encode(Original, <<"Person">>),
    EncodedBin = iolist_to_binary(Encoded),
    {ok, Decoded} = aws_xml:decode(EncodedBin),
    ?assert(is_map(Decoded)).

%%--------------------------------------------------------------------
%% Test: Helper functions
%%--------------------------------------------------------------------
encode_element_test() ->
    Element = aws_xml:encode_element(<<"Name">>, <<"John">>),
    ?assertMatch({'Name', [], _}, Element).

extract_text_empty_test() ->
    Result = aws_xml:extract_text([]),
    ?assertEqual(<<>>, Result).

%%--------------------------------------------------------------------
%% Test: Special characters handling
%%--------------------------------------------------------------------
encode_special_chars_test() ->
    Map = #{<<"Content">> => <<"Hello & Goodbye">>},
    Result = aws_xml:encode(Map),
    ?assert(is_list(Result)).

%%--------------------------------------------------------------------
%% Test: Whitespace handling
%%--------------------------------------------------------------------
decode_with_whitespace_test() ->
    Xml =
        <<"<Root>\n"
        "              <Name>John</Name>\n"
        "              <City>Seattle</City>\n"
        "            </Root>">>,
    {ok, Result} = aws_xml:decode(Xml),
    ?assert(maps:is_key(<<"Root">>, Result)).

decode_preserves_text_content_test() ->
    Xml = <<"<Message>Hello World</Message>">>,
    {ok, Result} = aws_xml:decode(Xml),
    ?assertEqual(#{<<"Message">> => <<"Hello World">>}, Result).
