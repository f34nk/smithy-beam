-module(simple_server_impl).
-export([get_item/2]).

%% This file will NOT be overwritten. Add your business logic here.
-behaviour(simple_server).

-spec get_item(simple_server:get_item_input(), map()) ->
    {ok, simple_server:get_item_output()} | {error, term()}.
get_item(_Input, _Context) ->
    {error, not_implemented}.
