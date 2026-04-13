-module(weather_roundtrip_test).
-include_lib("eunit/include/eunit.hrl").

-define(PORT, 8484).
-define(BASE, "http://localhost:" ++ integer_to_list(?PORT)).

%% EUnit fixture: start the Cowboy server once, run all three cases, then stop.
roundtrip_test_() ->
    {setup,
        fun setup/0,
        fun teardown/1,
        [
            {"GET /weather/Berlin returns 200 with temperature and unit",
             fun get_weather_berlin_200/0},
            {"GET /nonexistent returns 404",
             fun get_nonexistent_404/0},
            {"POST /unknown returns 404",
             fun post_unknown_404/0}
        ]}.

setup() ->
    application:ensure_all_started(inets),
    application:ensure_all_started(ssl),
    application:ensure_all_started(cowboy),
    application:ensure_all_started(jsx),
    {ok, _} = weather_service_app:start(?PORT),
    ok.

teardown(ok) ->
    cowboy:stop_listener(weather_http_listener).

%% ── Test cases ───────────────────────────────────────────────────────────────

get_weather_berlin_200() ->
    {ok, {{_, Code, _}, _Hdrs, Body}} =
        httpc:request(get, {?BASE ++ "/weather/Berlin", []}, [], []),
    ?assertEqual(200, Code),
    Decoded = jsx:decode(list_to_binary(Body), [return_maps]),
    ?assertEqual(22.5,         maps:get(<<"temperature">>, Decoded)),
    ?assertEqual(<<"Celsius">>, maps:get(<<"unit">>,        Decoded)).

%% The router's catch-all clause fires: route(_, _) -> {error, not_found}.
get_nonexistent_404() ->
    {ok, {{_, Code, _}, _Hdrs, _Body}} =
        httpc:request(get, {?BASE ++ "/nonexistent", []}, [], []),
    ?assertEqual(404, Code).

%% POST /unknown — no matching route clause, dispatcher returns not_found().
post_unknown_404() ->
    {ok, {{_, Code, _}, _Hdrs, _Body}} =
        httpc:request(post,
            {?BASE ++ "/unknown", [], "application/json", "{}"},
            [], []),
    ?assertEqual(404, Code).
