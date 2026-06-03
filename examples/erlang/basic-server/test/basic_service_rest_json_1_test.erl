-module(basic_service_rest_json_1_test).

-include_lib("eunit/include/eunit.hrl").
-include("basic_service_types.hrl").
-include("runtime_types.hrl").

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
    Input = basic_service_rest_json_1:decode_get_type_closure_request(Req, LabelMap),
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
    Input = basic_service_rest_json_1:decode_get_type_closure_request(Req, LabelMap),
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
    Input = basic_service_rest_json_1:decode_get_type_closure_request(Req, LabelMap),
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
    Input = basic_service_rest_json_1:decode_get_type_closure_request(Req, LabelMap),
    ?assertEqual(<<"hello world">>, Input#get_type_closure_input.name).

%% encode_get_type_closure_response/1

encode_get_type_closure_response_empty_body_test() ->
    Out = #get_type_closure_output{
        etag = <<"\"v1\"">>,
        basic_string = undefined
    },
    Resp = basic_service_rest_json_1:encode_get_type_closure_response(Out),
    ?assertEqual(200, Resp#http_response.status),
    ?assertEqual(jsone:encode(#{}), Resp#http_response.body).

encode_get_type_closure_response_json_body_test() ->
    Out = #get_type_closure_output{
        etag = <<"\"etag\"">>,
        basic_string = <<"hello">>,
        basic_integer = 42,
        basic_boolean = true
    },
    Resp = basic_service_rest_json_1:encode_get_type_closure_response(Out),
    ?assertEqual(200, Resp#http_response.status),
    ?assertEqual(
        jsone:encode(#{
            <<"basicString">> => <<"hello">>,
            <<"basicInteger">> => 42,
            <<"basicBoolean">> => true
        }),
        Resp#http_response.body
    ).
