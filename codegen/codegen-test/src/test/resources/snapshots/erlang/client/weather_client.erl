-module(weather_client).
-export([get_current_time/2, get_forecast/2]).

-spec get_current_time(Client :: map(), Input :: get_current_time_input()) ->
    {ok, get_current_time_output()} | {error, term()}.
get_current_time(Config, Input) ->
    {error, not_implemented}.

-spec get_forecast(Client :: map(), Input :: get_forecast_input()) ->
    {ok, get_forecast_output()} | {error, no_such_resource_error()}.
get_forecast(Config, Input) ->
    {error, not_implemented}.
