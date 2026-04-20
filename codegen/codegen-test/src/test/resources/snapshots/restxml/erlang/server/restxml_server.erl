-module(restxml_server).
-export([handle_list_buckets/2]).

-spec handle_list_buckets(Req :: term(), State :: term()) -> {ok, term()} | {error, term()}.
handle_list_buckets(Req, State) ->
    {error, not_implemented}.

-callback list_buckets(Input :: list_buckets_input(), Context :: term()) ->
    {ok, list_buckets_output()} | {error, term()}.


-behaviour(smithy_handler).
