%% Shared smithy-beam Erlang HTTP request/response helper module.
%% Uses httpc from OTP. Replace via adapter for testing.
-module(reqres).
-include("http_types.hrl").
-export([
    dispatch/2,
    dispatch/3,
    with_retry/2
]).

dispatch(Config, Request) ->
    HttpClient = maps:get(http_client, Config, httpc),
    dispatch(HttpClient, Config, Request).

dispatch(HttpClient, Config, Request) -> dispatch_signed(HttpClient, Config, Request).

dispatch_signed(HttpClient, Config, #http_request{
    method = Method, path = Path, query = Query, headers = Headers, body = Body, host = Host
}) ->
    BaseUrl =
        case maps:get(base_url, Config, undefined) of
            undefined ->
                case maps:get(endpoint_prefix, Config, undefined) of
                    undefined ->
                        <<>>;
                    _ ->
                        % TODO:
                        % The intended design is:
                        % resolve/2: run embedded @endpointRuleSet rules when present
                        % resolve_base_url/1: simple static fallback when rules are absent or fail
                        case aws_endpoint:resolve(Config, #{}) of
                            {ok, #{url := ResolvedUrl}} -> ResolvedUrl;
                            _ -> aws_endpoint:resolve_base_url(Config)
                        end
                end;
            GivenUrl ->
                GivenUrl
        end,
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

mime(Headers) ->
    case proplists:get_value(<<"Content-Type">>, Headers) of
        undefined -> "application/octet-stream";
        CT -> binary_to_list(CT)
    end.

%% @doc Invokes {@code Fun} with exponential backoff when a retryable error is returned.
-spec with_retry(fun(() -> term()), map()) -> term().
with_retry(Fun, Opts) ->
    Max = maps:get(max_attempts, Opts, 3),
    Base = maps:get(base_delay_ms, Opts, 100),
    ShouldRetry = maps:get(should_retry, Opts, fun(_) -> false end),
    with_retry(Fun, Max, Base, 1, ShouldRetry).

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
