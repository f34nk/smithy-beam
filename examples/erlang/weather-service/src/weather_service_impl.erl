-module(weather_service_impl).

%% This file will NOT be overwritten. Add your business logic here.
-behaviour(weather_service_server).

-export([
    create_weather_report/2,
    get_weather/2
]).

-spec create_weather_report(weather_service_server:create_weather_report_input(), map()) ->
    {ok, weather_service_server:create_weather_report_output()} | {error, term()}.
create_weather_report(_Input, _Context) ->
    {ok, #{<<"reportId">> => <<"rpt-001">>}}.

-spec get_weather(weather_service_server:get_weather_input(), map()) ->
    {ok, weather_service_server:get_weather_output()} | {error, term()}.
get_weather(#{<<"city">> := _City}, _Context) ->
    {ok, #{<<"temperature">> => 22.5, <<"unit">> => <<"Celsius">>}}.
