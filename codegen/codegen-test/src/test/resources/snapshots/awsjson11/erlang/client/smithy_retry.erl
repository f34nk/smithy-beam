-module(smithy_retry).

%% Retry logic with exponential backoff for AWS API calls
%% Handles transient errors (5xx, 429) with configurable retry strategy

-export([
    with_retry/1,
    with_retry/2,
    is_retryable_error/1,
    wait/3
]).

%%%===================================================================
%%% API Functions
%%%===================================================================

%% @doc Retry a function with default options
%% Default: 3 retries, 100ms initial backoff, 20s max backoff
-spec with_retry(Fun :: fun(() -> {ok, term()} | {error, term()})) ->
    {ok, term()} | {error, term()}.
with_retry(Fun) ->
    with_retry(Fun, #{}).

%% @doc Retry a function with custom options
%% Options:
%%   - max_retries: Maximum number of retry attempts (default: 3)
%%   - initial_backoff: Initial backoff in milliseconds (default: 100)
%%   - max_backoff: Maximum backoff in milliseconds (default: 20000)
%%   - backoff_multiplier: Multiplier for exponential backoff (default: 2)
%%   - jitter: Add jitter to backoff (default: true)
%%   - retryable_errors: Custom list of retryable error patterns (default: standard AWS errors)
-spec with_retry(
    Fun :: fun(() -> {ok, term()} | {error, term()}),
    Options :: map()
) ->
    {ok, term()} | {error, term()}.
with_retry(Fun, Options) when is_function(Fun, 0), is_map(Options) ->
    MaxRetries = maps:get(max_retries, Options, 3),
    InitialBackoff = maps:get(initial_backoff, Options, 100),
    MaxBackoff = maps:get(max_backoff, Options, 20000),
    Multiplier = maps:get(backoff_multiplier, Options, 2),
    UseJitter = maps:get(jitter, Options, true),

    retry_loop(Fun, 0, MaxRetries, InitialBackoff, MaxBackoff, Multiplier, UseJitter, Options).

%% @doc Check if an error is retryable
%% Retryable errors:
%%   - HTTP 5xx (server errors)
%%   - HTTP 429 (too many requests)
%%   - Connection errors: timeout, econnrefused, ehostunreach, etc.
-spec is_retryable_error(Error :: term()) -> boolean().
is_retryable_error({StatusCode, _}) when is_integer(StatusCode) ->
    StatusCode >= 500 orelse StatusCode =:= 429;
is_retryable_error({error, {StatusCode, _}}) when is_integer(StatusCode) ->
    StatusCode >= 500 orelse StatusCode =:= 429;
is_retryable_error({error, timeout}) ->
    true;
is_retryable_error({error, etimedout}) ->
    true;
is_retryable_error({error, econnrefused}) ->
    true;
is_retryable_error({error, ehostunreach}) ->
    true;
is_retryable_error({error, enetunreach}) ->
    true;
is_retryable_error({error, econnreset}) ->
    true;
is_retryable_error({error, {failed_connect, _}}) ->
    true;
is_retryable_error(_) ->
    false.

%% @doc Polls `Poll' repeatedly with exponential backoff until it reports
%% terminal success or failure, or the maximum number of attempts is reached.
%%
%% This is the polling primitive used by generated Smithy waiters
%% (`@waitable' trait). The poller is the generated acceptor evaluator
%% which calls the underlying operation and matches the result against the
%% waiter's acceptors.
%%
%% `Poll' must return one of:
%%   `{success, Value}' — terminal success; `wait/3' returns `{ok, Value}'.
%%   `{failure, Reason}' — terminal failure; `wait/3' returns `{error, Reason}'.
%%   `{retry,   Value}' — sleep with exponential backoff and call `Poll' again.
%%
%% `MinDelay' and `MaxDelay' are seconds (matching the `@waitable' trait).
%% After 100 retry attempts (hard cap, no setting today) `wait/3' gives up
%% with `{error, max_attempts_exceeded}'.
-spec wait(
    Poll :: fun(() -> {success | failure | retry, term()}),
    MinDelay :: non_neg_integer(),
    MaxDelay :: non_neg_integer()
) ->
    {ok, term()} | {error, term()}.
wait(Poll, MinDelay, MaxDelay) when is_function(Poll, 0) ->
    wait_loop(Poll, 0, 100, MinDelay * 1000, MaxDelay * 1000).

%%%===================================================================
%%% Internal Functions
%%%===================================================================

%% @private
%% Waiter polling loop with exponential backoff.
wait_loop(_Poll, Attempt, MaxAttempts, _MinMs, _MaxMs) when Attempt >= MaxAttempts ->
    {error, max_attempts_exceeded};
wait_loop(Poll, Attempt, MaxAttempts, MinMs, MaxMs) ->
    case Poll() of
        {success, Value} ->
            {ok, Value};
        {failure, Reason} ->
            {error, Reason};
        {retry, _Value} ->
            BaseMs = MinMs * round(math:pow(2, Attempt)),
            DelayMs = min(BaseMs, MaxMs),
            timer:sleep(DelayMs),
            wait_loop(Poll, Attempt + 1, MaxAttempts, MinMs, MaxMs)
    end.

%% @private
%% Main retry loop with exponential backoff
retry_loop(
    Fun, Attempt, MaxRetries, _InitialBackoff, _MaxBackoff, _Multiplier, _UseJitter, _Options
) when
    Attempt >= MaxRetries
->
    %% Final attempt - no more retries after this
    case Fun() of
        {ok, Result} ->
            {ok, Result};
        {error, Reason} ->
            %% Return error with retry information
            {error,
                {max_retries_exceeded, #{
                    attempts => Attempt + 1,
                    last_error => Reason
                }}}
    end;
retry_loop(Fun, Attempt, MaxRetries, InitialBackoff, MaxBackoff, Multiplier, UseJitter, Options) ->
    case Fun() of
        {ok, Result} ->
            {ok, Result};
        {error, Reason} = Error ->
            %% Check if error is retryable
            Retryable =
                case maps:get(retryable_errors, Options, undefined) of
                    undefined ->
                        is_retryable_error(Error);
                    CustomCheck when is_function(CustomCheck, 1) ->
                        CustomCheck(Error);
                    _ ->
                        is_retryable_error(Error)
                end,

            case Retryable of
                true ->
                    %% Calculate backoff time with exponential increase
                    BaseBackoff = InitialBackoff * round(math:pow(Multiplier, Attempt)),
                    LimitedBackoff = min(BaseBackoff, MaxBackoff),

                    %% Add jitter if enabled (random 0-25% of backoff time)
                    FinalBackoff =
                        case UseJitter of
                            true ->
                                JitterAmount = rand:uniform(LimitedBackoff div 4),
                                LimitedBackoff + JitterAmount;
                            false ->
                                LimitedBackoff
                        end,

                    %% Log retry attempt if logger provided
                    case maps:get(logger, Options, undefined) of
                        undefined ->
                            ok;
                        Logger when is_function(Logger, 1) ->
                            Logger(#{
                                event => retry_attempt,
                                attempt => Attempt + 1,
                                max_retries => MaxRetries,
                                backoff_ms => FinalBackoff,
                                error => Reason
                            })
                    end,

                    %% Sleep before retry
                    timer:sleep(FinalBackoff),

                    %% Retry
                    retry_loop(
                        Fun,
                        Attempt + 1,
                        MaxRetries,
                        InitialBackoff,
                        MaxBackoff,
                        Multiplier,
                        UseJitter,
                        Options
                    );
                false ->
                    %% Non-retryable error - return immediately
                    {error, Reason}
            end
    end.
