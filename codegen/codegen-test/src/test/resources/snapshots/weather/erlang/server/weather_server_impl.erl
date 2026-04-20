-module(weather_server_impl).
-export([get_current_time/2, get_forecast/2]).

%% This file will NOT be overwritten. Add your business logic here.
-behaviour(weather_server).

-spec get_current_time(weather_server:get_current_time_input(), map()) ->
    {ok, weather_server:get_current_time_output()} | {error, term()}.
get_current_time(_Input, _Context) ->
    {error, not_implemented}.

-spec get_forecast(weather_server:get_forecast_input(), map()) ->
    {ok, weather_server:get_forecast_output()} | {error, term()}.
get_forecast(_Input, _Context) ->
    {error, not_implemented}.
