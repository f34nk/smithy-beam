-module(smithy_validator_test).
-include_lib("eunit/include/eunit.hrl").

%%%===================================================================
%%% Test Suite for smithy_validator
%%%===================================================================

%%--------------------------------------------------------------------
%% validate/2 — all required fields present
%%--------------------------------------------------------------------
validate_all_present_test() ->
    Input = #{<<"name">> => <<"Alice">>, <<"age">> => 30},
    ?assertEqual(ok, smithy_validator:validate(Input, [<<"name">>, <<"age">>])).

validate_empty_required_list_test() ->
    Input = #{<<"name">> => <<"Alice">>},
    ?assertEqual(ok, smithy_validator:validate(Input, [])).

validate_empty_input_no_required_test() ->
    ?assertEqual(ok, smithy_validator:validate(#{}, [])).

%%--------------------------------------------------------------------
%% validate/2 — missing fields
%%--------------------------------------------------------------------
validate_single_missing_test() ->
    Input = #{<<"name">> => <<"Alice">>},
    Result = smithy_validator:validate(Input, [<<"name">>, <<"email">>]),
    ?assertEqual({error, {missing_required_fields, [<<"email">>]}}, Result).

validate_all_missing_test() ->
    Input = #{},
    Result = smithy_validator:validate(Input, [<<"name">>, <<"age">>]),
    ?assertMatch({error, {missing_required_fields, _}}, Result),
    {error, {missing_required_fields, Missing}} = Result,
    ?assertEqual(lists:sort([<<"name">>, <<"age">>]), lists:sort(Missing)).

validate_preserves_order_test() ->
    %% Missing fields are reported in the order they appear in RequiredFields
    Input = #{},
    {error, {missing_required_fields, Missing}} =
        smithy_validator:validate(Input, [<<"a">>, <<"b">>, <<"c">>]),
    ?assertEqual([<<"a">>, <<"b">>, <<"c">>], Missing).

validate_present_fields_not_in_missing_test() ->
    Input = #{<<"a">> => 1, <<"c">> => 3},
    {error, {missing_required_fields, Missing}} =
        smithy_validator:validate(Input, [<<"a">>, <<"b">>, <<"c">>]),
    ?assertEqual([<<"b">>], Missing).

%%--------------------------------------------------------------------
%% format/1 — error formatting
%%--------------------------------------------------------------------
format_single_field_test() ->
    Reason = {missing_required_fields, [<<"name">>]},
    ?assertEqual(<<"Missing required fields: name">>, smithy_validator:format(Reason)).

format_multiple_fields_test() ->
    Reason = {missing_required_fields, [<<"name">>, <<"email">>]},
    Result = smithy_validator:format(Reason),
    ?assertEqual(<<"Missing required fields: name, email">>, Result).

format_three_fields_test() ->
    Reason = {missing_required_fields, [<<"a">>, <<"b">>, <<"c">>]},
    Result = smithy_validator:format(Reason),
    ?assertEqual(<<"Missing required fields: a, b, c">>, Result).

format_returns_binary_test() ->
    Reason = {missing_required_fields, [<<"x">>]},
    Result = smithy_validator:format(Reason),
    ?assert(is_binary(Result)).

%%--------------------------------------------------------------------
%% Integration: validate then format
%%--------------------------------------------------------------------
validate_and_format_test() ->
    Input = #{<<"email">> => <<"user@example.com">>},
    {error, Reason} = smithy_validator:validate(Input, [<<"name">>, <<"email">>]),
    Msg = smithy_validator:format(Reason),
    ?assertEqual(<<"Missing required fields: name">>, Msg).
