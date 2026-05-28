-module(user_types_test).

-include_lib("eunit/include/eunit.hrl").
-include("user_types.hrl").

%% -- user_data record ---------------------------------------------------------

user_data_all_fields_test() ->
    User = #user_data{
        user_id = <<"u-1">>,
        email = <<"alice@example.com">>,
        display_name = <<"Alice">>
    },
    ?assertEqual(<<"u-1">>, User#user_data.user_id),
    ?assertEqual(<<"alice@example.com">>, User#user_data.email),
    ?assertEqual(<<"Alice">>, User#user_data.display_name).

user_data_optional_display_name_test() ->
    User = #user_data{
        user_id = <<"u-1">>,
        email = <<"alice@example.com">>
    },
    ?assertEqual(undefined, User#user_data.display_name).

user_data_update_test() ->
    User0 = #user_data{
        user_id = <<"u-1">>,
        email = <<"a@example.com">>,
        display_name = <<"A">>
    },
    User1 = User0#user_data{display_name = <<"Alice">>},
    ?assertEqual(<<"u-1">>, User1#user_data.user_id),
    ?assertEqual(<<"Alice">>, User1#user_data.display_name).

%% -- get_user_input record ----------------------------------------------------

get_user_input_required_field_test() ->
    Input = #get_user_input{user_id = <<"u-1">>},
    ?assertEqual(<<"u-1">>, Input#get_user_input.user_id).
