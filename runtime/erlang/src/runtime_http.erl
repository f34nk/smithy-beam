%% Shared smithy-beam Erlang HTTP request/response helper module.
%% Uses httpc from OTP. Replace via adapter for testing.
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
    HttpClient = maps:get(http_client, Config, httpc),
    dispatch(HttpClient, Config, Request).

%% @doc Dispatch an HTTP request through a specific HTTP client module.
-spec dispatch(module(), #{binary() => term()}, http_request()) ->
    {ok, http_response()} | {error, term()}.
dispatch(HttpClient, Config, #http_request{
    method = Method, path = Path, query = Query, headers = Headers, body = Body, host = Host
}) ->
    BaseUrl = maps:get(base_url, Config, undefined),
    QueryStr =
        case maps:to_list(Query) of
            [] ->
                <<>>;
            Pairs ->
                Encoded = uri_string:compose_query([{K, V} || {K, V} <- Pairs]),
                <<"?", Encoded/binary>>
        end,
    {Scheme, DefaultAuthority} = utils:split_base_url(BaseUrl),
    Authority =
        case Host of
            undefined -> DefaultAuthority;
            _ -> Host
        end,
    ReqUrl = <<Scheme/binary, Authority/binary, Path/binary, QueryStr/binary>>,
    HttpcHeaders = [{binary_to_list(K), binary_to_list(V)} || {K, V} <- Headers],
    Req =
        case Body of
            <<>> -> {binary_to_list(ReqUrl), HttpcHeaders};
            _ -> {binary_to_list(ReqUrl), HttpcHeaders, mime(Headers), Body}
        end,
    case
        HttpClient:request(
            binary_to_atom(string:lowercase(Method), utf8),
            Req,
            [],
            [{body_format, binary}]
        )
    of
        {ok, {{_, Status, _}, RespHeaders, RespBody}} ->
            BinHeaders = [{list_to_binary(K), list_to_binary(V)} || {K, V} <- RespHeaders],
            {ok, #http_response{
                status = Status,
                headers = BinHeaders,
                body = RespBody
            }};
        {error, Reason} ->
            {error, Reason}
    end.

-spec mime([{binary(), binary()}]) -> string().
mime(Headers) ->
    case proplists:get_value(<<"Content-Type">>, Headers) of
        undefined -> "application/octet-stream";
        CT -> binary_to_list(CT)
    end.

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
