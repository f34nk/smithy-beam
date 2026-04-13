-module(weather_service_app).
-behaviour(application).

-export([start/2, stop/1, start/1]).

%% OTP application entry point — reads port from application env (default 8080).
start(_Type, _Args) ->
    Port = application:get_env(weather_service, port, 8080),
    start(Port).

%% Test-friendly entry point: start Cowboy on an explicit port.
%% Returns {ok, Pid} on success.
-spec start(Port :: inet:port_number()) -> {ok, pid()} | {error, term()}.
start(Port) ->
    Dispatch = cowboy_router:compile([
        {'_', [{'_', weather_service_server, []}]}
    ]),
    cowboy:start_clear(weather_http_listener,
        [{port, Port}],
        #{env => #{dispatch => Dispatch}}).

stop(_State) ->
    cowboy:stop_listener(weather_http_listener).
