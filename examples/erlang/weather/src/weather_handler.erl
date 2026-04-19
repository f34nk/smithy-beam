-module(weather_handler).

%% Concrete implementation of the Weather service server callbacks.
%%
%% This module implements the `smithy_handler` behaviour.  The generated
%% `src/generated/weather_server.erl` scaffold lists the same callbacks as
%% stubs; copy that file and replace the `{error, not_implemented}` bodies
%% with real logic, or keep the generated scaffold as-is and adapt this
%% module to match your preferred structure.
%%
%% Callback contract (from smithy_handler):
%%
%%   handle_request(OperationName :: atom(), Input :: map(), Context :: map())
%%       -> {ok, Output :: map()} | {error, Reason :: term()}
%%
%% `OperationName` is the snake_case operation atom defined in the Smithy
%% model (e.g. `get_current_time`, `get_forecast`).
%% `Input` contains the decoded request fields keyed by binary member names.
%% `Context` carries request metadata (headers, path bindings, …).

-behaviour(smithy_handler).

-export([handle_request/3]).

%%====================================================================
%% smithy_handler callbacks
%%====================================================================

%% GetCurrentTime — returns the current UTC time as a Unix timestamp.
handle_request(get_current_time, _Input, _Context) ->
    Now  = calendar:universal_time(),
    Secs = calendar:datetime_to_gregorian_seconds(Now)
         - calendar:datetime_to_gregorian_seconds({{1970, 1, 1}, {0, 0, 0}}),
    {ok, #{<<"time">> => Secs}};

%% GetForecast — returns a fake rain-chance forecast for the requested city.
handle_request(get_forecast, #{<<"cityId">> := CityId}, _Context) ->
    Chance = city_rain_chance(CityId),
    {ok, #{<<"chanceOfRain">> => Chance}};

handle_request(get_forecast, _Input, _Context) ->
    {ok, #{<<"chanceOfRain">> => 0.5}};

%% Unknown operations.
handle_request(_Operation, _Input, _Context) ->
    {error, not_found}.

%%====================================================================
%% Internal helpers
%%====================================================================

city_rain_chance(<<"Berlin">>) -> 0.6;
city_rain_chance(<<"London">>) -> 0.7;
city_rain_chance(<<"Madrid">>) -> 0.2;
city_rain_chance(_Unknown)     -> 0.5.
