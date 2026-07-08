-module(types_test).

-include_lib("eunit/include/eunit.hrl").
-include("user_service_types.hrl").

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

%% -- create_user_input record -------------------------------------------------

create_user_input_all_fields_test() ->
    Input = #create_user_input{
        email = <<"bob@example.com">>,
        display_name = <<"Bob">>
    },
    ?assertEqual(<<"bob@example.com">>, Input#create_user_input.email),
    ?assertEqual(<<"Bob">>, Input#create_user_input.display_name).

create_user_input_optional_display_name_test() ->
    Input = #create_user_input{email = <<"bob@example.com">>},
    ?assertEqual(undefined, Input#create_user_input.display_name).
