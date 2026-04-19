-module(weather_server).
-export([handle_get_current_time/2, handle_get_forecast/2]).

handle_get_current_time(Req, State) ->
    {error, not_implemented}.


handle_get_forecast(Req, State) ->
    {error, not_implemented}.


-behaviour(smithy_handler).
