-module(weather_app).

%% Entry point for the Weather example application.
%%
%% Starts the smithy_router dispatch loop bound to weather_handler as the
%% smithy_handler implementation.  In a real deployment replace the stub
%% HTTP wiring below with your preferred framework (e.g. Cowboy, Elli).

-behaviour(application).

-export([start/2, stop/1]).

%%====================================================================
%% Application callbacks
%%====================================================================

start(_Type, _Args) ->
    %% Locate the runtime module that owns smithy_router so we can confirm
    %% it is loaded before accepting requests.
    ok = ensure_runtime(),
    weather_sup:start_link().

stop(_State) ->
    ok.

%%====================================================================
%% Internal helpers
%%====================================================================

ensure_runtime() ->
    %% Verify that the smithy runtime is available on the code path.
    %% Run `make build` from the repository root first to compile the runtime.
    case code:ensure_loaded(smithy_handler) of
        {module, smithy_handler} -> ok;
        {error, Reason} ->
            error({smithy_handler_not_found, Reason})
    end.
