-module(awsjson11_server).
-export([handle_describe_item/2]).

-spec handle_describe_item(Req :: term(), State :: term()) -> {ok, term()} | {error, term()}.
handle_describe_item(Req, State) ->
    {error, not_implemented}.

-callback describe_item(Input :: describe_item_input(), Context :: term()) ->
    {ok, describe_item_output()} | {error, term()}.


-behaviour(smithy_handler).
