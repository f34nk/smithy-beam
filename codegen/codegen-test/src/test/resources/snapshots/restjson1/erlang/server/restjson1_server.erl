-module(restjson1_server).
-export([handle_echo_message/2]).

-spec handle_echo_message(Req :: term(), State :: term()) -> {ok, term()} | {error, term()}.
handle_echo_message(Req, State) ->
    {error, not_implemented}.

-callback echo_message(Input :: echo_message_input(), Context :: term()) ->
    {ok, echo_message_output()} | {error, term()}.


-behaviour(smithy_handler).
