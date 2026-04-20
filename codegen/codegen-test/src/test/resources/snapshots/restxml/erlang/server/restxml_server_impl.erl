-module(restxml_server_impl).
-export([list_buckets/2]).

%% This file will NOT be overwritten. Add your business logic here.
-behaviour(restxml_server).

-spec list_buckets(restxml_server:list_buckets_input(), map()) ->
    {ok, restxml_server:list_buckets_output()} | {error, term()}.
list_buckets(_Input, _Context) ->
    {error, not_implemented}.
