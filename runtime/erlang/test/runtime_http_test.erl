-module(runtime_http_test).

-include_lib("eunit/include/eunit.hrl").
-include("runtime_types.hrl").

dispatch_builds_url_without_query_test() ->
    Config = #{base_url => <<"https://api.example">>},
    Req = #http_request{
        method = <<"GET">>,
        path = <<"/items">>,
        query = #{},
        headers = [],
        body = <<>>
    },
    {ok, Resp} = runtime_http:dispatch(http_mock, Config, Req),
    ?assertEqual(200, Resp#http_response.status),
    ?assertEqual(<<"{\"ok\":true}">>, Resp#http_response.body).

dispatch_appends_query_string_test() ->
    Config = #{base_url => <<"https://api.example">>},
    Req = #http_request{
        method = <<"GET">>,
        path = <<"/items">>,
        query = #{<<"verbose">> => <<"true">>},
        headers = [],
        body = <<>>
    },
    {ok, Resp} = runtime_http:dispatch(http_mock, Config, Req),
    ?assertEqual(200, Resp#http_response.status),
    ?assertEqual(<<>>, Resp#http_response.body).

dispatch_sends_request_body_test() ->
    Config = #{base_url => <<"https://api.example">>},
    Req = #http_request{
        method = <<"POST">>,
        path = <<"/items">>,
        query = #{},
        headers = [{<<"Content-Type">>, <<"application/json">>}],
        body = <<"{\"name\":\"item\"}">>
    },
    {ok, Resp} = runtime_http:dispatch(http_mock, Config, Req),
    ?assertEqual(201, Resp#http_response.status),
    ?assertEqual(<<"{\"id\":1}">>, Resp#http_response.body).

dispatch_propagates_client_error_test() ->
    Config = #{base_url => <<"https://api.example">>},
    Req = #http_request{
        method = <<"GET">>,
        path = <<"/fail">>,
        query = #{},
        headers = [],
        body = <<>>
    },
    ?assertEqual({error, timeout}, runtime_http:dispatch(http_mock, Config, Req)).

with_retry_returns_ok_without_retry_test() ->
    Ref = counters:new(1, []),
    Fun = fun() ->
        counters:add(Ref, 1, 1),
        {ok, done}
    end,
    ?assertEqual({ok, done}, runtime_http:with_retry(Fun, #{max_attempts => 3, base_delay_ms => 0})),
    ?assertEqual(1, counters:get(Ref, 1)).

with_retry_retries_retryable_error_test() ->
    Ref = counters:new(1, []),
    Fun = fun() ->
        counters:add(Ref, 1, 1),
        case counters:get(Ref, 1) of
            1 -> {error, retryable};
            _ -> {ok, done}
        end
    end,
    ShouldRetry = fun({error, retryable}) -> true; (_) -> false end,
    ?assertEqual(
        {ok, done},
        runtime_http:with_retry(Fun, #{
            max_attempts => 3,
            base_delay_ms => 0,
            should_retry => ShouldRetry
        })
    ),
    ?assertEqual(2, counters:get(Ref, 1)).

with_retry_stops_on_non_retryable_error_test() ->
    Ref = counters:new(1, []),
    Fun = fun() ->
        counters:add(Ref, 1, 1),
        {error, fatal}
    end,
    ?assertEqual(
        {error, fatal},
        runtime_http:with_retry(Fun, #{max_attempts => 3, base_delay_ms => 0})
    ),
    ?assertEqual(1, counters:get(Ref, 1)).
