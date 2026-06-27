%% @doc Invokes {@code Fun} with exponential backoff when a modeled retryable error is returned.
-spec with_retry(fun(() -> term()), map()) -> term().
with_retry(Fun, Opts) ->
    Max = maps:get(max_attempts, Opts, 3),
    Base = maps:get(base_delay_ms, Opts, 100),
    with_retry(Fun, Max, Base, 1).

with_retry(Fun, 0, _, _) ->
    Fun();
with_retry(Fun, Attempts, Base, N) ->
    case Fun() of
        {ok, _} = Ok ->
            Ok;
        {error, _} = Err ->
            case should_retry(Err) of
                true when Attempts > 1 ->
                    timer:sleep(trunc(Base * math:pow(2, N - 1))),
                    with_retry(Fun, Attempts - 1, Base, N + 1);
                _ ->
                    Err
            end
    end.
