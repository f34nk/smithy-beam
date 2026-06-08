-module(user_service_impl).
-behaviour(user_service_behaviour).

-include("user_service_types.hrl").

-export([
    handle_create_user/3,
    handle_delete_user/3,
    handle_get_user/3,
    handle_list_users/3,
    handle_update_user/3
]).

handle_create_user(_Ctx, _Input, _Meta) ->
    {error, not_implemented}.

handle_delete_user(_Ctx, _Input, _Meta) ->
    {error, not_implemented}.

handle_get_user(_Ctx, _Input, _Meta) ->
    {error, not_implemented}.

handle_list_users(_Ctx, _Input, _Meta) ->
    {error, not_implemented}.

handle_update_user(_Ctx, _Input, _Meta) ->
    {error, not_implemented}.
