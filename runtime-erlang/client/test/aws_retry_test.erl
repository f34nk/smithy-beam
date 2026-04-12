-module(aws_retry_test).
-include_lib("eunit/include/eunit.hrl").

%%%===================================================================
%%% Test Suite for AWS Retry Logic with Exponential Backoff
%%%===================================================================

%%--------------------------------------------------------------------
%% Test: Successful operation on first attempt
%%--------------------------------------------------------------------
success_on_first_attempt_test() ->
    Fun = fun() -> {ok, success} end,
    Result = aws_retry:with_retry(Fun),
    ?assertEqual({ok, success}, Result).

success_with_options_test() ->
    Fun = fun() -> {ok, <<"data">>} end,
    Result = aws_retry:with_retry(Fun, #{max_retries => 3}),
    ?assertEqual({ok, <<"data">>}, Result).

%%--------------------------------------------------------------------
%% Test: Retry on retryable errors
%%--------------------------------------------------------------------
retry_500_error_test() ->
    Counter = counters:new(1, []),

    Fun = fun() ->
        Count = counters:get(Counter, 1),
        counters:add(Counter, 1, 1),
        case Count of
            0 -> {error, {500, #{<<"message">> => <<"Server Error">>}}};
            1 -> {error, {503, #{<<"message">> => <<"Service Unavailable">>}}};
            _ -> {ok, <<"success">>}
        end
    end,

    Result = aws_retry:with_retry(Fun, #{
        max_retries => 3,
        initial_backoff => 10,
        jitter => false
    }),

    ?assertEqual({ok, <<"success">>}, Result),
    ?assertEqual(3, counters:get(Counter, 1)).

retry_429_too_many_requests_test() ->
    Counter = counters:new(1, []),

    Fun = fun() ->
        Count = counters:get(Counter, 1),
        counters:add(Counter, 1, 1),
        case Count of
            0 -> {error, {429, #{<<"message">> => <<"Too Many Requests">>}}};
            _ -> {ok, <<"success">>}
        end
    end,

    Result = aws_retry:with_retry(Fun, #{
        max_retries => 2,
        initial_backoff => 10,
        jitter => false
    }),

    ?assertEqual({ok, <<"success">>}, Result),
    ?assertEqual(2, counters:get(Counter, 1)).

retry_timeout_error_test() ->
    Counter = counters:new(1, []),

    Fun = fun() ->
        Count = counters:get(Counter, 1),
        counters:add(Counter, 1, 1),
        case Count of
            0 -> {error, timeout};
            _ -> {ok, <<"success">>}
        end
    end,

    Result = aws_retry:with_retry(Fun, #{
        max_retries => 2,
        initial_backoff => 10
    }),

    ?assertEqual({ok, <<"success">>}, Result).

retry_connection_errors_test() ->
    Counter = counters:new(1, []),

    Fun = fun() ->
        Count = counters:get(Counter, 1),
        counters:add(Counter, 1, 1),
        case Count of
            0 -> {error, econnrefused};
            1 -> {error, etimedout};
            2 -> {error, ehostunreach};
            _ -> {ok, <<"success">>}
        end
    end,

    Result = aws_retry:with_retry(Fun, #{
        max_retries => 5,
        initial_backoff => 10,
        jitter => false
    }),

    ?assertEqual({ok, <<"success">>}, Result),
    ?assertEqual(4, counters:get(Counter, 1)).

%%--------------------------------------------------------------------
%% Test: No retry on non-retryable errors
%%--------------------------------------------------------------------
no_retry_on_400_error_test() ->
    Counter = counters:new(1, []),

    Fun = fun() ->
        counters:add(Counter, 1, 1),
        {error, {400, #{<<"message">> => <<"Bad Request">>}}}
    end,

    Result = aws_retry:with_retry(Fun, #{max_retries => 3}),

    ?assertEqual({error, {400, #{<<"message">> => <<"Bad Request">>}}}, Result),
    % Only called once
    ?assertEqual(1, counters:get(Counter, 1)).

no_retry_on_404_error_test() ->
    Counter = counters:new(1, []),

    Fun = fun() ->
        counters:add(Counter, 1, 1),
        {error, {404, #{<<"message">> => <<"Not Found">>}}}
    end,

    Result = aws_retry:with_retry(Fun, #{max_retries => 3}),

    ?assertMatch({error, {404, _}}, Result),
    ?assertEqual(1, counters:get(Counter, 1)).

no_retry_on_unknown_error_test() ->
    Counter = counters:new(1, []),

    Fun = fun() ->
        counters:add(Counter, 1, 1),
        {error, custom_error}
    end,

    Result = aws_retry:with_retry(Fun, #{max_retries => 3}),

    ?assertEqual({error, custom_error}, Result),
    ?assertEqual(1, counters:get(Counter, 1)).

%%--------------------------------------------------------------------
%% Test: Max retries exceeded
%%--------------------------------------------------------------------
max_retries_exceeded_test() ->
    Counter = counters:new(1, []),

    Fun = fun() ->
        counters:add(Counter, 1, 1),
        {error, {500, #{<<"message">> => <<"Server Error">>}}}
    end,

    Result = aws_retry:with_retry(Fun, #{
        max_retries => 2,
        initial_backoff => 10,
        jitter => false
    }),

    ?assertMatch({error, {max_retries_exceeded, #{attempts := 3, last_error := _}}}, Result),
    % Initial + 2 retries
    ?assertEqual(3, counters:get(Counter, 1)).

max_retries_with_zero_test() ->
    Counter = counters:new(1, []),

    Fun = fun() ->
        counters:add(Counter, 1, 1),
        {error, {503, #{}}}
    end,

    Result = aws_retry:with_retry(Fun, #{
        max_retries => 0,
        initial_backoff => 10
    }),

    ?assertMatch({error, {max_retries_exceeded, _}}, Result),
    ?assertEqual(1, counters:get(Counter, 1)).

%%--------------------------------------------------------------------
%% Test: Exponential backoff
%%--------------------------------------------------------------------
exponential_backoff_timing_test() ->
    StartTime = erlang:monotonic_time(millisecond),
    Counter = counters:new(1, []),

    Fun = fun() ->
        Count = counters:get(Counter, 1),
        counters:add(Counter, 1, 1),
        case Count of
            N when N < 3 -> {error, {500, #{}}};
            _ -> {ok, success}
        end
    end,

    Result = aws_retry:with_retry(Fun, #{
        max_retries => 3,
        initial_backoff => 50,
        backoff_multiplier => 2,
        jitter => false
    }),

    EndTime = erlang:monotonic_time(millisecond),
    Duration = EndTime - StartTime,

    ?assertEqual({ok, success}, Result),
    %% Should wait: 50ms + 100ms + 200ms = 350ms minimum
    ?assert(Duration >= 350).

backoff_with_max_limit_test() ->
    Fun = fun() -> {error, {500, #{}}} end,

    Result = aws_retry:with_retry(Fun, #{
        max_retries => 5,
        initial_backoff => 100,
        max_backoff => 200,
        backoff_multiplier => 2,
        jitter => false
    }),

    ?assertMatch({error, {max_retries_exceeded, _}}, Result).

%%--------------------------------------------------------------------
%% Test: Jitter
%%--------------------------------------------------------------------
jitter_adds_randomness_test() ->
    %% Run multiple times and check that delays vary
    Delays = [measure_retry_delay() || _ <- lists:seq(1, 5)],

    %% All delays should be different due to jitter
    UniqueDelays = lists:usort(Delays),
    ?assert(length(UniqueDelays) > 1).

measure_retry_delay() ->
    StartTime = erlang:monotonic_time(millisecond),

    Fun = fun() -> {error, {500, #{}}} end,

    _ = aws_retry:with_retry(Fun, #{
        max_retries => 1,
        initial_backoff => 50,
        jitter => true
    }),

    EndTime = erlang:monotonic_time(millisecond),
    EndTime - StartTime.

no_jitter_consistency_test() ->
    %% Without jitter, delays should be consistent
    Delays = [measure_retry_delay_no_jitter() || _ <- lists:seq(1, 5)],

    %% All delays should be similar (increased tolerance for CI/slow systems)
    [First | Rest] = Delays,
    ?assert(lists:all(fun(D) -> abs(D - First) < 50 end, Rest)).

measure_retry_delay_no_jitter() ->
    StartTime = erlang:monotonic_time(millisecond),

    Fun = fun() -> {error, {500, #{}}} end,

    _ = aws_retry:with_retry(Fun, #{
        max_retries => 1,
        initial_backoff => 50,
        jitter => false
    }),

    EndTime = erlang:monotonic_time(millisecond),
    EndTime - StartTime.

%%--------------------------------------------------------------------
%% Test: Custom retryable errors
%%--------------------------------------------------------------------
custom_retryable_check_test() ->
    Counter = counters:new(1, []),

    Fun = fun() ->
        Count = counters:get(Counter, 1),
        counters:add(Counter, 1, 1),
        case Count of
            0 -> {error, my_custom_error};
            _ -> {ok, success}
        end
    end,

    CustomCheck = fun
        ({error, my_custom_error}) -> true;
        (_) -> false
    end,

    Result = aws_retry:with_retry(Fun, #{
        max_retries => 2,
        initial_backoff => 10,
        retryable_errors => CustomCheck
    }),

    ?assertEqual({ok, success}, Result),
    ?assertEqual(2, counters:get(Counter, 1)).

%%--------------------------------------------------------------------
%% Test: Logger callback
%%--------------------------------------------------------------------
logger_callback_test() ->
    Pid = self(),
    Counter = counters:new(1, []),

    Fun = fun() ->
        Count = counters:get(Counter, 1),
        counters:add(Counter, 1, 1),
        case Count of
            N when N < 2 -> {error, {500, #{}}};
            _ -> {ok, success}
        end
    end,

    Logger = fun(LogData) ->
        Pid ! {log, LogData}
    end,

    Result = aws_retry:with_retry(Fun, #{
        max_retries => 3,
        initial_backoff => 10,
        jitter => false,
        logger => Logger
    }),

    ?assertEqual({ok, success}, Result),

    %% Should receive 2 log messages (for 2 retries)
    receive
        {log, Log1} -> ?assertEqual(retry_attempt, maps:get(event, Log1))
    after 100 -> ?assert(false)
    end,
    receive
        {log, Log2} -> ?assertEqual(retry_attempt, maps:get(event, Log2))
    after 100 -> ?assert(false)
    end.

%%--------------------------------------------------------------------
%% Test: is_retryable_error/1
%%--------------------------------------------------------------------
is_retryable_error_500_test() ->
    ?assert(aws_retry:is_retryable_error({500, #{}})).

is_retryable_error_503_test() ->
    ?assert(aws_retry:is_retryable_error({503, <<"Service Unavailable">>})).

is_retryable_error_429_test() ->
    ?assert(aws_retry:is_retryable_error({429, #{}})).

is_retryable_error_timeout_test() ->
    ?assert(aws_retry:is_retryable_error({error, timeout})).

is_retryable_error_etimedout_test() ->
    ?assert(aws_retry:is_retryable_error({error, etimedout})).

is_retryable_error_econnrefused_test() ->
    ?assert(aws_retry:is_retryable_error({error, econnrefused})).

is_retryable_error_connection_test() ->
    ?assert(aws_retry:is_retryable_error({error, {failed_connect, []}})).

is_not_retryable_error_400_test() ->
    ?assertNot(aws_retry:is_retryable_error({400, #{}})).

is_not_retryable_error_404_test() ->
    ?assertNot(aws_retry:is_retryable_error({404, #{}})).

is_not_retryable_error_custom_test() ->
    ?assertNot(aws_retry:is_retryable_error({error, custom_error})).

%%--------------------------------------------------------------------
%% Test: Integration scenarios
%%--------------------------------------------------------------------
api_call_with_retry_test() ->
    %% Simulate an API call that fails twice then succeeds
    Counter = counters:new(1, []),

    ApiCall = fun() ->
        Count = counters:get(Counter, 1),
        counters:add(Counter, 1, 1),
        case Count of
            0 -> {error, {503, #{<<"message">> => <<"Service temporarily unavailable">>}}};
            1 -> {error, {500, #{<<"message">> => <<"Internal server error">>}}};
            _ -> {ok, #{<<"data">> => <<"response">>, <<"status">> => <<"success">>}}
        end
    end,

    Result = aws_retry:with_retry(ApiCall, #{
        max_retries => 3,
        initial_backoff => 50,
        max_backoff => 1000
    }),

    ?assertMatch({ok, #{<<"data">> := <<"response">>}}, Result).

mixed_error_types_test() ->
    Counter = counters:new(1, []),

    Fun = fun() ->
        Count = counters:get(Counter, 1),
        counters:add(Counter, 1, 1),
        case Count of
            % Retryable
            0 -> {error, {500, #{}}};
            % Retryable
            1 -> {error, timeout};
            % Retryable
            2 -> {error, {503, #{}}};
            _ -> {ok, <<"finally_succeeded">>}
        end
    end,

    Result = aws_retry:with_retry(Fun, #{
        max_retries => 5,
        initial_backoff => 10,
        jitter => false
    }),

    ?assertEqual({ok, <<"finally_succeeded">>}, Result),
    ?assertEqual(4, counters:get(Counter, 1)).

%%--------------------------------------------------------------------
%% Test: Performance
%%--------------------------------------------------------------------
performance_no_retry_test() ->
    %% Successful calls should have minimal overhead
    Fun = fun() -> {ok, success} end,

    StartTime = erlang:monotonic_time(microsecond),
    _ = [aws_retry:with_retry(Fun) || _ <- lists:seq(1, 100)],
    EndTime = erlang:monotonic_time(microsecond),

    Duration = EndTime - StartTime,
    AvgPerCall = Duration div 100,

    %% Should be very fast (< 100 microseconds per call)
    ?assert(AvgPerCall < 100).
