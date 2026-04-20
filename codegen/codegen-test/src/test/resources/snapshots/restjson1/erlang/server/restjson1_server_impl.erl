-module(restjson1_server_impl).
-export([echo_message/2]).

%% This file will NOT be overwritten. Add your business logic here.
-behaviour(restjson1_server).

-spec echo_message(restjson1_server:echo_message_input(), map()) ->
    {ok, restjson1_server:echo_message_output()} | {error, term()}.
echo_message(_Input, _Context) ->
    {error, not_implemented}.
