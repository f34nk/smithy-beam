-module(weather_server_impl).

%% This file will NOT be overwritten. Add your business logic here.
-behaviour(weather_server).

-export([
    get_weather/2,
    list_cities/2
]).

-spec get_weather(weather_server:get_weather_input(), map()) ->
    {ok, weather_server:get_weather_output()} | {error, term()}.
get_weather(_Input, _Context) ->
    {error, not_implemented}.

-spec list_cities(weather_server:list_cities_input(), map()) ->
    {ok, weather_server:list_cities_output()} | {error, term()}.
list_cities(_Input, _Context) ->
    {error, not_implemented}.
