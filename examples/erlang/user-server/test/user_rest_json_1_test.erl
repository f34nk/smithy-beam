-module(user_rest_json_1_test).

-include_lib("eunit/include/eunit.hrl").
-include("user_types.hrl").
-include("user_runtime_types.hrl").

%% encode_get_user_request/1

encode_minimal_request_test() ->
    Input = #get_user_input{user_id = <<"u-1">>},
    Req = user_rest_json_1:encode_get_user_request(Input),
    ?assertEqual(<<"GET">>, Req#http_request.method),
    ?assertEqual(<<"/users/u-1">>, Req#http_request.path),
    ?assertEqual(#{}, Req#http_request.query),
    ?assertEqual([{<<"Content-Type">>, <<"application/json">>}], Req#http_request.headers),
    ?assertEqual(<<>>, Req#http_request.body).

encode_uri_encodes_path_label_test() ->
    Input = #get_user_input{user_id = <<"a/b c">>},
    Req = user_rest_json_1:encode_get_user_request(Input),
    ?assertEqual(<<"/users/", (uri_string:quote(<<"a/b c">>))/binary>>, Req#http_request.path).

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
    Input = user_rest_json_1:decode_get_user_request(Req, LabelMap),
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
    Input = user_rest_json_1:decode_get_user_request(Req, LabelMap),
    ?assertEqual(<<"hello world">>, Input#get_user_input.user_id).

%% decode_get_user_response/1

decode_success_json_body_test() ->
    Body = jsone:encode(#{
        <<"user">> => #{
            <<"userId">> => <<"u-1">>,
            <<"email">> => <<"alice@example.com">>,
            <<"displayName">> => <<"Alice">>
        }
    }),
    Resp = #http_response{status = 200, headers = [], body = Body},
    {ok, Out} = user_rest_json_1:decode_get_user_response(Resp),
    ?assertMatch(#{<<"userId">> := <<"u-1">>}, Out#get_user_output.user),
    ?assertEqual(<<"alice@example.com">>, maps:get(<<"email">>, Out#get_user_output.user)),
    ?assertEqual(<<"Alice">>, maps:get(<<"displayName">>, Out#get_user_output.user)).

decode_success_empty_body_test() ->
    Resp = #http_response{status = 200, headers = [], body = <<>>},
    {ok, Out} = user_rest_json_1:decode_get_user_response(Resp),
    ?assertEqual(undefined, Out#get_user_output.user).

decode_invalid_json_returns_empty_document_test() ->
    Resp = #http_response{status = 200, headers = [], body = <<"{not json">>},
    {ok, Out} = user_rest_json_1:decode_get_user_response(Resp),
    ?assertEqual(undefined, Out#get_user_output.user).

decode_http_error_test() ->
    Resp = #http_response{status = 404, body = <<"{\"message\":\"missing\"}">>},
    ?assertEqual(
        {error, {http_error, 404, <<"{\"message\":\"missing\"}">>}},
        user_rest_json_1:decode_get_user_response(Resp)
    ).

%% encode_update_user_request/1

encode_update_user_request_test() ->
    Input = #update_user_input{
        user_id = <<"u-1">>,
        email = <<"new@example.com">>,
        display_name = undefined
    },
    Req = user_rest_json_1:encode_update_user_request(Input),
    ?assertEqual(<<"PUT">>, Req#http_request.method),
    ?assertEqual(<<"/users/u-1">>, Req#http_request.path),
    ?assertEqual(jsone:encode(#{<<"email">> => <<"new@example.com">>}), Req#http_request.body).

decode_update_user_request_test() ->
    Req = #http_request{
        method = <<"PUT">>,
        path = <<"/users/u-1">>,
        query = #{},
        headers = [{<<"Content-Type">>, <<"application/json">>}],
        body = jsone:encode(#{<<"displayName">> => <<"Alice">>})
    },
    LabelMap = #{<<"userId">> => <<"u-1">>},
    Input = user_rest_json_1:decode_update_user_request(Req, LabelMap),
    ?assertEqual(<<"u-1">>, Input#update_user_input.user_id),
    ?assertEqual(undefined, Input#update_user_input.email),
    ?assertEqual(<<"Alice">>, Input#update_user_input.display_name).

decode_delete_user_response_test() ->
    Resp = #http_response{status = 204, headers = [], body = <<>>},
    {ok, Out} = user_rest_json_1:decode_delete_user_response(Resp),
    ?assertEqual(#delete_user_output{}, Out).
