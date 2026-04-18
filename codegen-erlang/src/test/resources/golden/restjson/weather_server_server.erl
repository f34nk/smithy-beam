-module(weather_server_server).

%% Generated Smithy server for weather_server. Do not edit.

-behaviour(cowboy_handler).

-export([
    init/2,
    handle/3,
    route/2
]).

-type get_weather_input() :: #{
    city => binary()
}.
-type get_weather_output() :: #{
    temperature => float()
}.
-type list_cities_input() :: #{
    filter => binary(),
    custom_header => binary(),
    next_token => binary(),
    max_results => integer()
}.
-type list_cities_output() :: #{
    next_token => binary(),
    cities => [binary()]
}.

-callback get_weather(Input :: get_weather_input(), Context :: map()) ->
    {ok, get_weather_output()} | {error, term()}.
-callback list_cities(Input :: list_cities_input(), Context :: map()) ->
    {ok, list_cities_output()} | {error, term()}.

init(Req0, State) ->
    {Code, Headers, Body} =
        handle(weather_server_impl, Req0, #{}),
    Req = cowboy_req:reply(Code, maps:from_list(Headers), Body, Req0),
    {ok, Req, State}.

-spec handle(module(), term(), map()) -> {pos_integer(), list(), binary()}.
handle(Impl, Req, Context) ->
    {Method, Path, Headers, Body} = smithy_server:extract(Req),
    case route(Method, Path) of
        {ok, get_weather} -> dispatch_get_weather(Impl, Path, Headers, Body, Context);
        {ok, list_cities} -> dispatch_list_cities(Impl, Path, Headers, Body, Context);
        {error, not_found} -> smithy_server:not_found()
    end.

route(<<"GET">>, <<"/weather/", _/binary>>) -> {ok, get_weather};
route(<<"GET">>, <<"/cities">>) -> {ok, list_cities};
route(_, _) -> {error, not_found}.

dispatch_get_weather(Impl, Path, Headers, Body, Context) ->
    Input = deserialize_get_weather(Path, Headers, Body),
    case Impl:get_weather(Input, Context) of
        {ok, Output} -> smithy_server:response(200, serialize_get_weather(Output));
        {error, Err} -> smithy_server:error_response(Err)
    end.

deserialize_get_weather(Path, _Headers, Body) ->
    <<"/weather/", City/binary>> = Path,
    _ = Body,
    #{<<"city">> => City}.

serialize_get_weather(Output) ->
    jsx:encode(Output).

dispatch_list_cities(Impl, Path, Headers, Body, Context) ->
    Input = deserialize_list_cities(Path, Headers, Body),
    case Impl:list_cities(Input, Context) of
        {ok, Output} -> smithy_server:response(200, serialize_list_cities(Output));
        {error, Err} -> smithy_server:error_response(Err)
    end.

deserialize_list_cities(Path, _Headers, Body) ->
    _ = Path,
    _ = Body,
    #{}.

serialize_list_cities(Output) ->
    jsx:encode(Output).
