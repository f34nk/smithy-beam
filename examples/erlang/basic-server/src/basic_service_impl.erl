-module(basic_service_impl).
-behaviour(basic_service_behaviour).

-include("basic_service_types.hrl").

-export([handle_get_type_closure/3, handle_list_basic_items/3]).

handle_get_type_closure(_Ctx, _Input, _Meta) ->
    {error, not_implemented}.

handle_list_basic_items(_Ctx, _Input, _Meta) ->
    {error, not_implemented}.
