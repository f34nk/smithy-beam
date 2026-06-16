-module(s3_waiters_test).

-include_lib("eunit/include/eunit.hrl").
-include("amazon_s3_types.hrl").

-define(BUCKET, <<"my-bucket">>).
-define(WAIT_OPTS, #{max_attempts => 3, min_delay_ms => 0, max_delay_ms => 0}).

wait_bucket_exists_ok_test() ->
    with_cleanup(fun() ->
        head_bucket_http_mock:reset([{status, 200}]),
        Input = #head_bucket_input{bucket = ?BUCKET},
        ?assertMatch(
            {ok, {ok, #head_bucket_output{}}},
            amazon_s3_waiters:wait_bucket_exists(client_config(), Input, ?WAIT_OPTS)
        ),
        ?assertEqual(1, head_bucket_http_mock:call_count())
    end).

wait_bucket_exists_retries_test() ->
    with_cleanup(fun() ->
        head_bucket_http_mock:reset([{status, 404}, {status, 200}]),
        Input = #head_bucket_input{bucket = ?BUCKET},
        ?assertMatch(
            {ok, {ok, #head_bucket_output{}}},
            amazon_s3_waiters:wait_bucket_exists(client_config(), Input, ?WAIT_OPTS)
        ),
        ?assertEqual(2, head_bucket_http_mock:call_count())
    end).

wait_bucket_exists_max_attempts_test() ->
    with_cleanup(fun() ->
        head_bucket_http_mock:reset([{status, 404}, {status, 404}, {status, 404}]),
        Input = #head_bucket_input{bucket = ?BUCKET},
        Opts = maps:put(max_attempts, 2, ?WAIT_OPTS),
        ?assertEqual(
            {error, max_attempts_exceeded},
            amazon_s3_waiters:wait_bucket_exists(client_config(), Input, Opts)
        ),
        ?assertEqual(2, head_bucket_http_mock:call_count())
    end).

wait_bucket_not_exists_max_attempts_test() ->
    with_cleanup(fun() ->
        head_bucket_http_mock:reset([{status, 200}, {status, 200}]),
        Input = #head_bucket_input{bucket = ?BUCKET},
        Opts = maps:put(max_attempts, 2, ?WAIT_OPTS),
        ?assertEqual(
            {error, max_attempts_exceeded},
            amazon_s3_waiters:wait_bucket_not_exists(client_config(), Input, Opts)
        ),
        ?assertEqual(2, head_bucket_http_mock:call_count())
    end).

client_config() ->
    #{
        base_url => <<"http://localhost:4566">>,
        region => <<"us-east-1">>,
        endpoint_prefix => <<"s3">>,
        signing_name => <<"s3">>,
        s3_addressing_style => path_style,
        http_client => head_bucket_http_mock,
        credentials => undefined,
        retry => #{max_attempts => 1, base_delay_ms => 0}
    }.

with_cleanup(Fun) ->
    try
        Fun()
    after
        persistent_term:erase({head_bucket_http_mock, responses}),
        persistent_term:erase({head_bucket_http_mock, count})
    end.
