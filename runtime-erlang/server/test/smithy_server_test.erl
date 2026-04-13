-module(smithy_server_test).
-include_lib("eunit/include/eunit.hrl").

%%%===================================================================
%%% Test Suite for smithy_server
%%%
%%% extract/1 is tested via cowboy_req.erl — a test-only mock module
%%% that accepts plain maps instead of real Cowboy request objects.
%%% All other helpers are pure functions with no framework dependency.
%%%===================================================================

%%--------------------------------------------------------------------
%% extract/1 — Cowboy request decomposition (via mock cowboy_req)
%%--------------------------------------------------------------------
extract_method_test() ->
    Req = fake_req(<<"POST">>, <<"/items">>, #{}, <<"">>),
    {Method, _Path, _Headers, _Body} = smithy_server:extract(Req),
    ?assertEqual(<<"POST">>, Method).

extract_path_test() ->
    Req = fake_req(<<"GET">>, <<"/weather/NYC">>, #{}, <<"">>),
    {_Method, Path, _Headers, _Body} = smithy_server:extract(Req),
    ?assertEqual(<<"/weather/NYC">>, Path).

extract_headers_test() ->
    H = #{<<"content-type">> => <<"application/json">>, <<"x-api-key">> => <<"secret">>},
    Req = fake_req(<<"GET">>, <<"/">>, H, <<"">>),
    {_Method, _Path, Headers, _Body} = smithy_server:extract(Req),
    ?assertEqual(H, Headers).

extract_body_test() ->
    Body = <<"{\"name\":\"Alice\"}">>,
    Req = fake_req(<<"PUT">>, <<"/users/1">>, #{}, Body),
    {_Method, _Path, _Headers, ExtractedBody} = smithy_server:extract(Req),
    ?assertEqual(Body, ExtractedBody).

extract_empty_body_test() ->
    Req = fake_req(<<"DELETE">>, <<"/items/99">>, #{}, <<"">>),
    {_Method, _Path, _Headers, Body} = smithy_server:extract(Req),
    ?assertEqual(<<"">>, Body).

extract_returns_four_tuple_test() ->
    Req = fake_req(<<"GET">>, <<"/">>, #{}, <<"">>),
    Result = smithy_server:extract(Req),
    ?assertMatch({_, _, _, _}, Result).

extract_all_fields_test() ->
    H = #{<<"accept">> => <<"*/*">>},
    B = <<"data">>,
    Req = fake_req(<<"PATCH">>, <<"/resource">>, H, B),
    ?assertEqual({<<"PATCH">>, <<"/resource">>, H, B}, smithy_server:extract(Req)).

%%--------------------------------------------------------------------
%% response/2
%%--------------------------------------------------------------------
response_200_test() ->
    Body = <<"{}">>,
    {Code, Headers, RBody} = smithy_server:response(200, Body),
    ?assertEqual(200, Code),
    ?assertEqual(Body, RBody),
    ?assert(lists:member({<<"content-type">>, <<"application/json">>}, Headers)).

response_201_test() ->
    {Code, _Headers, _Body} = smithy_server:response(201, <<"{\"id\":\"1\"}">>),
    ?assertEqual(201, Code).

response_content_type_test() ->
    {_Code, Headers, _Body} = smithy_server:response(200, <<"data">>),
    ?assertEqual([{<<"content-type">>, <<"application/json">>}], Headers).

%%--------------------------------------------------------------------
%% error_response/1 — delegates to smithy_error_map, encodes JSON
%%--------------------------------------------------------------------
error_response_not_found_test() ->
    {Code, Headers, Body} = smithy_server:error_response({not_found, <<"gone">>}),
    ?assertEqual(404, Code),
    ?assert(lists:member({<<"content-type">>, <<"application/json">>}, Headers)),
    ?assert(is_binary(Body)).

error_response_conflict_test() ->
    {Code, _Headers, _Body} = smithy_server:error_response({conflict, <<"dup">>}),
    ?assertEqual(409, Code).

error_response_internal_test() ->
    {Code, _Headers, _Body} = smithy_server:error_response({internal, <<"oops">>}),
    ?assertEqual(500, Code).

error_response_unknown_test() ->
    {Code, _Headers, _Body} = smithy_server:error_response(totally_unknown),
    ?assertEqual(500, Code).

error_response_body_has_message_key_test() ->
    {_Code, _Headers, Body} = smithy_server:error_response({forbidden, <<"no access">>}),
    ?assertNotEqual(nomatch, binary:match(Body, <<"\"message\"">>)).

error_response_body_contains_message_value_test() ->
    {_Code, _Headers, Body} = smithy_server:error_response({not_found, <<"item gone">>}),
    ?assertNotEqual(nomatch, binary:match(Body, <<"item gone">>)).

%%--------------------------------------------------------------------
%% validation_error/1
%%--------------------------------------------------------------------
validation_error_returns_400_test() ->
    Reason = {missing_required_fields, [<<"name">>]},
    {Code, _Headers, _Body} = smithy_server:validation_error(Reason),
    ?assertEqual(400, Code).

validation_error_content_type_test() ->
    Reason = {missing_required_fields, [<<"email">>]},
    {_Code, Headers, _Body} = smithy_server:validation_error(Reason),
    ?assertEqual([{<<"content-type">>, <<"application/json">>}], Headers).

validation_error_body_has_message_key_test() ->
    Reason = {missing_required_fields, [<<"name">>, <<"email">>]},
    {_Code, _Headers, Body} = smithy_server:validation_error(Reason),
    ?assertNotEqual(nomatch, binary:match(Body, <<"\"message\"">>)).

validation_error_message_mentions_field_test() ->
    Reason = {missing_required_fields, [<<"name">>]},
    {_Code, _Headers, Body} = smithy_server:validation_error(Reason),
    ?assertNotEqual(nomatch, binary:match(Body, <<"name">>)).

%%--------------------------------------------------------------------
%% not_found/0
%%--------------------------------------------------------------------
not_found_returns_404_test() ->
    {Code, _Headers, _Body} = smithy_server:not_found(),
    ?assertEqual(404, Code).

not_found_body_test() ->
    {_Code, _Headers, Body} = smithy_server:not_found(),
    ?assertEqual(<<"Not Found">>, Body).

not_found_empty_headers_test() ->
    {_Code, Headers, _Body} = smithy_server:not_found(),
    ?assertEqual([], Headers).

%%%===================================================================
%%% Helpers
%%%===================================================================

fake_req(Method, Path, Headers, Body) ->
    #{method => Method, path => Path, headers => Headers, body => Body}.
