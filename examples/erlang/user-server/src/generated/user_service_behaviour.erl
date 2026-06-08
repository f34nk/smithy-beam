%% Generated Erlang server behaviour for smithy.beam.demo.user#UserService.
-module(user_service_behaviour).
-include("user_service_types.hrl").

-callback handle_create_user(
    Ctx :: term(),
    Input :: create_user_input(),
    Meta :: term()
) -> {ok, create_user_output()} | {error, term()}.

-callback handle_delete_user(
    Ctx :: term(),
    Input :: delete_user_input(),
    Meta :: term()
) -> {ok, delete_user_output()} | {error, term()}.

-callback handle_get_user(
    Ctx :: term(),
    Input :: get_user_input(),
    Meta :: term()
) -> {ok, get_user_output()} | {error, term()}.

-callback handle_list_users(
    Ctx :: term(),
    Input :: list_users_input(),
    Meta :: term()
) -> {ok, list_users_output()} | {error, term()}.

-callback handle_update_user(
    Ctx :: term(),
    Input :: update_user_input(),
    Meta :: term()
) -> {ok, update_user_output()} | {error, term()}.
