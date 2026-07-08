-module(rest_json_1_test).

-include_lib("eunit/include/eunit.hrl").
-include("user_service_types.hrl").
-include("http_types.hrl").

%% decode_get_user_request/2

decode_get_user_request_uses_label_map_test() ->
    Req = #http_request{
        method = <<"GET">>,
        path = <<"/users/u-1">>,
        query = #{},
        headers = [],
        body = <<>>
    },
    LabelMap = #{<<"userId">> => <<"u-1">>},
    Input = user_service_rest_json_1:decode_get_user_request(Req, LabelMap),
    ?assertEqual(<<"u-1">>, Input#get_user_input.user_id).

decode_uri_decodes_path_label_test() ->
    Req = #http_request{
        method = <<"GET">>,
        path = <<"/users/hello%20world">>,
        query = #{},
        headers = [],
        body = <<>>
    },
    LabelMap = #{<<"userId">> => <<"hello world">>},
    Input = user_service_rest_json_1:decode_get_user_request(Req, LabelMap),
    ?assertEqual(<<"hello world">>, Input#get_user_input.user_id).

%% encode_get_user_response/1

encode_get_user_response_test() ->
    User = #user_data{
        user_id = <<"u-1">>,
        email = <<"alice@example.com">>,
        display_name = <<"Alice">>
    },
    Out = #get_user_output{user = User},
    Resp = user_service_rest_json_1:encode_get_user_response(Out),
    ?assertEqual(200, Resp#http_response.status),
    ?assertEqual(
        jsone:encode(#{
            <<"user">> => #{
                <<"userId">> => <<"u-1">>,
                <<"email">> => <<"alice@example.com">>,
                <<"displayName">> => <<"Alice">>
            }
        }),
        Resp#http_response.body
    ).

encode_get_user_response_omits_undefined_test() ->
    Out = #get_user_output{user = undefined},
    Resp = user_service_rest_json_1:encode_get_user_response(Out),
    ?assertEqual(200, Resp#http_response.status),
    ?assertEqual(jsone:encode(#{}), Resp#http_response.body).

decode_update_user_request_test() ->
    Req = #http_request{
        method = <<"PUT">>,
        path = <<"/users/u-1">>,
        query = #{},
        headers = [{<<"Content-Type">>, <<"application/json">>}],
        body = jsone:encode(#{<<"displayName">> => <<"Alice">>})
    },
    LabelMap = #{<<"userId">> => <<"u-1">>},
    Input = user_service_rest_json_1:decode_update_user_request(Req, LabelMap),
    ?assertEqual(<<"u-1">>, Input#update_user_input.user_id),
    ?assertEqual(undefined, Input#update_user_input.email),
    ?assertEqual(<<"Alice">>, Input#update_user_input.display_name).

decode_create_user_request_test() ->
    Req = #http_request{
        method = <<"POST">>,
        path = <<"/users">>,
        query = #{},
        headers = [{<<"Content-Type">>, <<"application/json">>}],
        body = jsone:encode(#{<<"email">> => <<"bob@example.com">>, <<"displayName">> => <<"Bob">>})
    },
    Input = user_service_rest_json_1:decode_create_user_request(Req),
    ?assertEqual(<<"bob@example.com">>, Input#create_user_input.email),
    ?assertEqual(<<"Bob">>, Input#create_user_input.display_name).

encode_delete_user_response_test() ->
    Resp = user_service_rest_json_1:encode_delete_user_response(#delete_user_output{}),
    ?assertEqual(204, Resp#http_response.status),
    ?assertEqual(<<>>, Resp#http_response.body).
