%% Shared smithy-beam Erlang HTTP request/response helper module.
-module(runtime_http).
-include("runtime_types.hrl").
-export([
    dispatch/2,
    dispatch/3,
    with_retry/2
]).

%% @doc Dispatch an HTTP request using the client config default HTTP adapter.
-spec dispatch(#{binary() => term()}, http_request()) ->
    {ok, http_response()} | {error, term()}.
dispatch(Config, Request) ->
    HttpClient = maps:get(http_client, Config, runtime_http_client_httpc),
    dispatch(HttpClient, Config, Request).

%% @doc Dispatch an HTTP request through a specific HTTP client module.
-spec dispatch(module(), #{binary() => term()}, http_request()) ->
    {ok, http_response()} | {error, term()}.
dispatch(HttpClient, Config, Request) ->
    ClientReq = runtime_http_client:build_request(Config, Request),
    HttpClient:request(ClientReq).

%% @doc Invoke Fun with exponential backoff when a retryable error is returned.
-spec with_retry(fun(() -> term()), map()) -> term().
with_retry(Fun, Opts) ->
    Max = maps:get(max_attempts, Opts, 3),
    Base = maps:get(base_delay_ms, Opts, 100),
    ShouldRetry = maps:get(should_retry, Opts, fun(_) -> false end),
    with_retry(Fun, Max, Base, 1, ShouldRetry).

-spec with_retry(fun(() -> term()), non_neg_integer(), non_neg_integer(), pos_integer(), fun(
    (term()) -> boolean()
)) ->
    term().
with_retry(Fun, 0, _, _, _) ->
    Fun();
with_retry(Fun, Attempts, Base, N, ShouldRetry) ->
    case Fun() of
        {ok, _} = Ok ->
            Ok;
        {error, _} = Err ->
            case ShouldRetry(Err) of
                true when Attempts > 1 ->
                    timer:sleep(trunc(Base * math:pow(2, N - 1))),
                    with_retry(Fun, Attempts - 1, Base, N + 1, ShouldRetry);
                _ ->
                    Err
            end
    end.
