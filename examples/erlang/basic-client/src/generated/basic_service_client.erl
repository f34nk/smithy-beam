%% Generated Erlang client for smithy.beam.demo.basic#BasicService.
%% Operation stubs use arity 2: (Config, Input).
-module(basic_service_client).
-include("basic_service_types.hrl").
-export([get_type_closure/2, list_basic_items/2]).

%% @doc
%% Returns every named type in the service closure for the given name.
%%
%% ## How to call
%%
%% Supply `name` as the path label. Set the `verbose` query parameter when you want
%% optional fields in the response.
%%
%% ```
%% config = %{base_url: "http://localhost:8080"}
%% input  = %{name: "example", verbose: true}
%% {:ok, output} = BasicServiceClient.get_type_closure(config, input)
%% ```
%%
%% Failures surface as `{:error, term()}`. A missing name may map to the modeled
%% `BasicNotFound` error.

-spec get_type_closure(client_config(), get_type_closure_input()) ->
    {'ok', get_type_closure_output()} | {'error', term()}.
get_type_closure(Config, Input) ->
    RetryOpts = maps:get(retry, Config, #{}),
    basic_service_retry:with_retry(fun() ->
        Req = basic_service_rest_json_1:encode_get_type_closure_request(Input),
        case runtime_http:dispatch(Config, Req) of
            {ok, Resp} ->
                basic_service_rest_json_1:decode_get_type_closure_response(Resp);
            {error, Reason} ->
                {error, Reason}
        end
    end, RetryOpts).

%% HTTP request bindings for smithy.beam.demo.basic#GetTypeClosure:
%%   name @ LABEL
%%   requestTag @ HEADER
%%   verbose @ QUERY

%% @doc
%% Returns basic items across all pages.
%%
%% ## How to call
%%
%% Erlang:
%%
%% {ok, Items} = basic_service_client:list_basic_items(Config, Input).
%%
%% Elixir:
%%
%% ```
%% config = %{base_url: "http://localhost:8080", http_client: MyHttpMock}
%% input = %{page_size: 10}
%% {:ok, items} = BasicServiceClient.list_basic_items(config, input)
%% ```

-spec list_basic_items(client_config(), list_basic_items_input()) ->
    {'ok', [basic_item()]} | {'error', term()}.
list_basic_items(Config, Input) ->
    list_basic_items(Config, Input, []).

list_basic_items(Config, Input, Acc) ->
    Req = basic_service_rest_json_1:encode_list_basic_items_request(Input),
    case runtime_http:dispatch(Config, Req) of
        {ok, Resp} ->
            case basic_service_rest_json_1:decode_list_basic_items_response(Resp) of
                {ok, Output} ->
                    NewAcc = Acc ++ element(#list_basic_items_output.items, Output),
                    case element(#list_basic_items_output.next_token, Output) of
                        undefined ->
                            {ok, NewAcc};
                        NextToken ->
                            NextInput = Input#list_basic_items_input{next_token = NextToken},
                            list_basic_items(Config, NextInput, NewAcc)
                    end;
                {error, Reason} ->
                    {error, Reason}
            end;
        {error, Reason} ->
            {error, Reason}
    end.

%% HTTP request bindings for smithy.beam.demo.basic#ListBasicItems:
%%   nextToken @ QUERY
%%   pageSize @ QUERY

%% Service closure: smithy.beam.demo.basic#BasicService
%% Client configuration is intentionally opaque at this layer; endpoint, transport, and protocol live in future runtime modules.
-type client_config() :: #{binary() => term()}.
