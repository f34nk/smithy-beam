-module(awsjson11_server_impl).
-export([describe_item/2]).

%% This file will NOT be overwritten. Add your business logic here.
-behaviour(awsjson11_server).

-spec describe_item(awsjson11_server:describe_item_input(), map()) ->
    {ok, awsjson11_server:describe_item_output()} | {error, term()}.
describe_item(_Input, _Context) ->
    {error, not_implemented}.
