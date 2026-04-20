-module(simple_server).
-export([handle_get_item/2]).

-spec handle_get_item(Req :: term(), State :: term()) -> {ok, term()} | {error, term()}.
handle_get_item(Req, State) ->
    {error, not_implemented}.

-callback get_item(Input :: get_item_input(), Context :: term()) ->
    {ok, get_item_output()} | {error, term()}.


-behaviour(smithy_handler).
