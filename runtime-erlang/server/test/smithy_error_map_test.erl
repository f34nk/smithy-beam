-module(smithy_error_map_test).
-include_lib("eunit/include/eunit.hrl").

%%%===================================================================
%%% Test Suite for smithy_error_map
%%%===================================================================

%%--------------------------------------------------------------------
%% Named error tuples
%%--------------------------------------------------------------------
not_found_test() ->
    ?assertEqual({404, <<"Resource not found">>},
                 smithy_error_map:to_http({not_found, <<"Resource not found">>})).

conflict_test() ->
    ?assertEqual({409, <<"Already exists">>},
                 smithy_error_map:to_http({conflict, <<"Already exists">>})).

validation_test() ->
    ?assertEqual({400, <<"Bad input">>},
                 smithy_error_map:to_http({validation, <<"Bad input">>})).

internal_test() ->
    ?assertEqual({500, <<"Something went wrong">>},
                 smithy_error_map:to_http({internal, <<"Something went wrong">>})).

unauthorized_test() ->
    ?assertEqual({401, <<"Unauthorized">>},
                 smithy_error_map:to_http({unauthorized, <<"Unauthorized">>})).

forbidden_test() ->
    ?assertEqual({403, <<"Forbidden">>},
                 smithy_error_map:to_http({forbidden, <<"Forbidden">>})).

%%--------------------------------------------------------------------
%% Atom errors
%%--------------------------------------------------------------------
not_implemented_atom_test() ->
    ?assertEqual({501, <<"Not implemented">>},
                 smithy_error_map:to_http(not_implemented)).

%%--------------------------------------------------------------------
%% Catch-all / unknown errors
%%--------------------------------------------------------------------
unknown_atom_test() ->
    {Code, Msg} = smithy_error_map:to_http(some_unknown_error),
    ?assertEqual(500, Code),
    ?assert(is_binary(Msg)).

unknown_tuple_test() ->
    {Code, Msg} = smithy_error_map:to_http({unknown_error, <<"details">>}),
    ?assertEqual(500, Code),
    ?assert(is_binary(Msg)).

unknown_integer_test() ->
    {Code, Msg} = smithy_error_map:to_http(42),
    ?assertEqual(500, Code),
    ?assert(is_binary(Msg)).

%%--------------------------------------------------------------------
%% Return-value shape
%%--------------------------------------------------------------------
returns_tuple_of_two_test() ->
    Result = smithy_error_map:to_http({not_found, <<"x">>}),
    ?assertMatch({_, _}, Result).

status_code_is_integer_test() ->
    {Code, _} = smithy_error_map:to_http({validation, <<"bad">>}),
    ?assert(is_integer(Code)).

message_is_binary_test() ->
    {_, Msg} = smithy_error_map:to_http({forbidden, <<"no">>}),
    ?assert(is_binary(Msg)).
