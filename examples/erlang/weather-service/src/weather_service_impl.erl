-module(weather_service_impl).

%% This file will NOT be overwritten. Add your business logic here.
-behaviour(weather_service_handler).

-export([
    create_weather_report/2,
    get_weather/2
]).

-spec create_weather_report(weather_service_handler:create_weather_report_input(), map()) ->
    {ok, weather_service_handler:create_weather_report_output()} | {error, term()}.
create_weather_report(_Input, _Context) ->
    {error, not_implemented}.

-spec get_weather(weather_service_handler:get_weather_input(), map()) ->
    {ok, weather_service_handler:get_weather_output()} | {error, term()}.
get_weather(_Input, _Context) ->
    {error, not_implemented}.

