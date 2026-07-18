-module(rest_json_1_test).

-include_lib("eunit/include/eunit.hrl").
-include("user_service_types.hrl").
-include("runtime_types.hrl").

%% encode_get_user_request/1

encode_minimal_request_test() ->
    Input = #get_user_input{user_id = <<"u-1">>},
    Req = user_service_rest_json_1:encode_get_user_request(Input),
    ?assertEqual(<<"GET">>, Req#http_request.method),
    ?assertEqual(<<"/users/u-1">>, Req#http_request.path),
    ?assertEqual(#{}, Req#http_request.query),
    ?assertEqual([{<<"Content-Type">>, <<"application/json">>}], Req#http_request.headers),
    ?assertEqual(<<>>, Req#http_request.body).

encode_uri_encodes_path_label_test() ->
    Input = #get_user_input{user_id = <<"a/b c">>},
    Req = user_service_rest_json_1:encode_get_user_request(Input),
    ?assertEqual(<<"/users/", (uri_string:quote(<<"a/b c">>))/binary>>, Req#http_request.path).

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
    {ok, Out} = user_service_rest_json_1:decode_get_user_response(Resp),
    ?assertEqual(<<"u-1">>, Out#get_user_output.user#user_data.user_id),
    ?assertEqual(<<"alice@example.com">>, Out#get_user_output.user#user_data.email),
    ?assertEqual(<<"Alice">>, Out#get_user_output.user#user_data.display_name).

decode_success_empty_body_test() ->
    Resp = #http_response{status = 200, headers = [], body = <<>>},
    {ok, Out} = user_service_rest_json_1:decode_get_user_response(Resp),
    ?assertEqual(undefined, Out#get_user_output.user).

decode_unknown_error_test() ->
    Resp = #http_response{status = 404, body = <<"{\"message\":\"missing\"}">>},
    ?assertEqual(
        {error, {unknown_error, 404, <<"{\"message\":\"missing\"}">>}},
        user_service_rest_json_1:decode_get_user_response(Resp)
    ).

%% encode_create_user_request/1

encode_create_user_request_test() ->
    Input = #create_user_input{
        email = <<"bob@example.com">>,
        display_name = <<"Bob">>
    },
    Req = user_service_rest_json_1:encode_create_user_request(Input),
    ?assertEqual(<<"POST">>, Req#http_request.method),
    ?assertEqual(<<"/users">>, Req#http_request.path),
    ?assertEqual(
        jsone:encode(#{<<"email">> => <<"bob@example.com">>, <<"displayName">> => <<"Bob">>}),
        Req#http_request.body
    ).

decode_create_user_response_test() ->
    Body = jsone:encode(#{
        <<"user">> => #{
            <<"userId">> => <<"u-2">>,
            <<"email">> => <<"bob@example.com">>
        }
    }),
    Resp = #http_response{status = 201, headers = [], body = Body},
    {ok, Out} = user_service_rest_json_1:decode_create_user_response(Resp),
    ?assertEqual(<<"u-2">>, Out#create_user_output.user#user_data.user_id).

%% encode_list_users_request/1

encode_list_users_request_test() ->
    Input = #list_users_input{},
    Req = user_service_rest_json_1:encode_list_users_request(Input),
    ?assertEqual(<<"GET">>, Req#http_request.method),
    ?assertEqual(<<"/users">>, Req#http_request.path),
    ?assertEqual(<<>>, Req#http_request.body).

decode_list_users_response_test() ->
    Body = jsone:encode(#{
        <<"users">> => [
            #{<<"userId">> => <<"u-1">>, <<"email">> => <<"a@example.com">>},
            #{<<"userId">> => <<"u-2">>, <<"email">> => <<"b@example.com">>}
        ]
    }),
    Resp = #http_response{status = 200, headers = [], body = Body},
    {ok, Out} = user_service_rest_json_1:decode_list_users_response(Resp),
    ?assertEqual(2, length(Out#list_users_output.users)).
