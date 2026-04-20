-module(weather_server).
-export([handle_get_current_time/2, handle_get_forecast/2]).

-spec handle_get_current_time(Req :: term(), State :: term()) -> {ok, term()} | {error, term()}.
handle_get_current_time(Req, State) ->
    {error, not_implemented}.

-callback get_current_time(Input :: get_current_time_input(), Context :: term()) ->
    {ok, get_current_time_output()} | {error, term()}.


-spec handle_get_forecast(Req :: term(), State :: term()) -> {ok, term()} | {error, term()}.
handle_get_forecast(Req, State) ->
    {error, not_implemented}.

-callback get_forecast(Input :: get_forecast_input(), Context :: term()) ->
    {ok, get_forecast_output()} | {error, no_such_resource_error()}.


-behaviour(smithy_handler).
