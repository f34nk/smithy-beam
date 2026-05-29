-module(basic_rest_json_1_test).

-include_lib("eunit/include/eunit.hrl").
-include("basic_types.hrl").
-include("runtime_types.hrl").

%% encode_get_type_closure_request/1

encode_minimal_request_test() ->
    Input = #get_type_closure_input{name = <<"widget">>},
    Req = basic_rest_json_1:encode_get_type_closure_request(Input),
    ?assertEqual(<<"GET">>, Req#http_request.method),
    ?assertEqual(<<"/types/widget">>, Req#http_request.path),
    ?assertEqual(#{}, Req#http_request.query),
    ?assertEqual([{<<"Content-Type">>, <<"application/json">>}], Req#http_request.headers),
    ?assertEqual(<<>>, Req#http_request.body).

encode_full_request_test() ->
    Input = #get_type_closure_input{
        name = <<"widget">>,
        verbose = true,
        request_tag = <<"trace-1">>
    },
    Req = basic_rest_json_1:encode_get_type_closure_request(Input),
    ?assertEqual(#{<<"verbose">> => <<"true">>}, Req#http_request.query),
    ?assert(lists:member({<<"X-Request-Tag">>, <<"trace-1">>}, Req#http_request.headers)),
    ?assert(lists:member({<<"Content-Type">>, <<"application/json">>}, Req#http_request.headers)).

encode_uri_encodes_path_label_test() ->
    Input = #get_type_closure_input{name = <<"a/b c">>},
    Req = basic_rest_json_1:encode_get_type_closure_request(Input),
    ?assertEqual(<<"/types/", (uri_string:quote(<<"a/b c">>))/binary>>, Req#http_request.path).

encode_omits_optional_fields_test() ->
    Input = #get_type_closure_input{
        name = <<"x">>,
        verbose = undefined,
        request_tag = undefined
    },
    Req = basic_rest_json_1:encode_get_type_closure_request(Input),
    ?assertEqual(#{}, Req#http_request.query),
    ?assertEqual([{<<"Content-Type">>, <<"application/json">>}], Req#http_request.headers).

%% decode_get_type_closure_request/2

decode_get_type_closure_request_uses_label_map_test() ->
    Req = #http_request{
        method = <<"GET">>,
        path = <<"/types/widget">>,
        query = #{<<"verbose">> => <<"true">>},
        headers = [{<<"X-Request-Tag">>, <<"trace-1">>}],
        body = <<>>
    },
    LabelMap = #{<<"name">> => <<"widget">>},
    Input = basic_rest_json_1:decode_get_type_closure_request(Req, LabelMap),
    ?assertEqual(<<"widget">>, Input#get_type_closure_input.name),
    ?assertEqual(true, Input#get_type_closure_input.verbose),
    ?assertEqual(<<"trace-1">>, Input#get_type_closure_input.request_tag).

decode_minimal_request_test() ->
    Req = #http_request{
        method = <<"GET">>,
        path = <<"/types/widget">>,
        query = #{},
        headers = [{<<"Content-Type">>, <<"application/json">>}],
        body = <<>>
    },
    LabelMap = #{<<"name">> => <<"widget">>},
    Input = basic_rest_json_1:decode_get_type_closure_request(Req, LabelMap),
    ?assertEqual(<<"widget">>, Input#get_type_closure_input.name),
    ?assertEqual(undefined, Input#get_type_closure_input.verbose),
    ?assertEqual(undefined, Input#get_type_closure_input.request_tag).

decode_full_request_test() ->
    Req = #http_request{
        method = <<"GET">>,
        path = <<"/types/widget">>,
        query = #{<<"verbose">> => <<"true">>},
        headers = [{<<"X-Request-Tag">>, <<"trace-1">>}],
        body = <<>>
    },
    LabelMap = #{<<"name">> => <<"widget">>},
    Input = basic_rest_json_1:decode_get_type_closure_request(Req, LabelMap),
    ?assertEqual(<<"widget">>, Input#get_type_closure_input.name),
    ?assertEqual(true, Input#get_type_closure_input.verbose),
    ?assertEqual(<<"trace-1">>, Input#get_type_closure_input.request_tag).

decode_uri_decodes_path_label_test() ->
    Req = #http_request{
        method = <<"GET">>,
        path = <<"/types/hello%20world">>,
        query = #{},
        headers = [],
        body = <<>>
    },
    LabelMap = #{<<"name">> => <<"hello world">>},
    Input = basic_rest_json_1:decode_get_type_closure_request(Req, LabelMap),
    ?assertEqual(<<"hello world">>, Input#get_type_closure_input.name).

%% decode_get_type_closure_response/1

decode_success_empty_body_test() ->
    Resp = #http_response{
        status = 200,
        headers = [{<<"ETag">>, <<"\"v1\"">>}],
        body = <<>>
    },
    {ok, Out} = basic_rest_json_1:decode_get_type_closure_response(Resp),
    ?assertEqual(<<"\"v1\"">>, Out#get_type_closure_output.etag),
    ?assertEqual(undefined, Out#get_type_closure_output.basic_string).

decode_success_json_body_test() ->
    Body = jsone:encode(#{
        <<"basicString">> => <<"hello">>,
        <<"basicInteger">> => 42,
        <<"basicBoolean">> => true
    }),
    Resp = #http_response{
        status = 200,
        headers = [{<<"ETag">>, <<"\"etag\"">>}],
        body = Body
    },
    {ok, Out} = basic_rest_json_1:decode_get_type_closure_response(Resp),
    ?assertEqual(<<"\"etag\"">>, Out#get_type_closure_output.etag),
    ?assertEqual(<<"hello">>, Out#get_type_closure_output.basic_string),
    ?assertEqual(42, Out#get_type_closure_output.basic_integer),
    ?assertEqual(true, Out#get_type_closure_output.basic_boolean).

decode_invalid_json_returns_empty_document_test() ->
    Resp = #http_response{
        status = 200,
        headers = [],
        body = <<"{not json">>
    },
    {ok, Out} = basic_rest_json_1:decode_get_type_closure_response(Resp),
    ?assertEqual(undefined, Out#get_type_closure_output.basic_string).

decode_unknown_error_test() ->
    Resp = #http_response{status = 404, body = <<"{\"message\":\"missing\"}">>},
    ?assertEqual(
        {error, {unknown_error, 404, <<"{\"message\":\"missing\"}">>}},
        basic_rest_json_1:decode_get_type_closure_response(Resp)
    ).
