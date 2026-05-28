-module(basic_types_test).

-include_lib("eunit/include/eunit.hrl").
-include("basic_types.hrl").

%% ── basic_not_found error record ─────────────────────────────────────────────

basic_not_found_record_test() ->
    Err = #basic_not_found{
        message = <<"missing">>,
        '__beam_error_kind' = client
    },
    ?assertEqual(<<"missing">>, Err#basic_not_found.message),
    ?assertEqual(client, Err#basic_not_found.'__beam_error_kind').

basic_not_found_default_kind_test() ->
    Err = #basic_not_found{message = <<"gone">>},
    ?assertEqual(client, Err#basic_not_found.'__beam_error_kind').

%% ── basic_item record ────────────────────────────────────────────────────────

basic_item_all_fields_test() ->
    Item = #basic_item{name = <<"hello">>, count = 42},
    ?assertEqual(<<"hello">>, Item#basic_item.name),
    ?assertEqual(42,          Item#basic_item.count).

basic_item_optional_count_test() ->
    Item = #basic_item{name = <<"hello">>},
    ?assertEqual(undefined, Item#basic_item.count).

basic_item_update_test() ->
    Item0 = #basic_item{name = <<"a">>, count = 1},
    Item1 = Item0#basic_item{count = 2},
    ?assertEqual(<<"a">>, Item1#basic_item.name),
    ?assertEqual(2,       Item1#basic_item.count).

%% ── basic_status (string enum as tagged-union type) ─────────────────────────
%% Erlang types are not enforced at runtime; the tests below verify that values
%% matching the type spec can be constructed and matched without error.

basic_status_known_values_test() ->
    Values = [active, inactive, pending],
    lists:foreach(fun(V) ->
        %% Pattern-match each known atom to confirm it is a valid Erlang term.
        ?assert(is_atom(V))
    end, Values).

basic_status_unknown_variant_test() ->
    Unknown = {unknown, <<"FUTURE_STATUS">>},
    {unknown, Wire} = Unknown,
    ?assertEqual(<<"FUTURE_STATUS">>, Wire).

%% ── basic_priority (integer enum as tagged-union type) ───────────────────────

basic_priority_known_values_test() ->
    Values = [low, medium, high],
    lists:foreach(fun(V) ->
        ?assert(is_atom(V))
    end, Values).

basic_priority_unknown_variant_test() ->
    Unknown = {unknown, 99},
    {unknown, Int} = Unknown,
    ?assertEqual(99, Int).

%% ── basic_union (tagged union) ───────────────────────────────────────────────

basic_union_text_variant_test() ->
    V = {text, <<"hello">>},
    {text, Str} = V,
    ?assertEqual(<<"hello">>, Str).

basic_union_number_variant_test() ->
    V = {number, 42},
    {number, N} = V,
    ?assertEqual(42, N).

basic_union_flag_variant_test() ->
    V = {flag, true},
    {flag, B} = V,
    ?assertEqual(true, B).

basic_union_unknown_variant_test() ->
    V = {unknown, <<"new_field">>},
    {unknown, Tag} = V,
    ?assertEqual(<<"new_field">>, Tag).
