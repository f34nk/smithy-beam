-module(retry_mock).
-behaviour(runtime_http_client).

-include("runtime_types.hrl").
-include("runtime_http_client.hrl").

-export([request/1, call_count/0, reset/0]).

request(#http_client_request{method = get}) ->
    N =
        case get(call_count) of
            undefined -> 1;
            C -> C + 1
        end,
    put(call_count, N),
    case N of
        1 ->
            Body = <<"{\"__type\":\"BasicNotFound\",\"message\":\"missing\"}">>,
            ok_response(
                404,
                [{<<"content-type">>, <<"application/json">>}],
                Body
            );
        _ ->
            ok_response(200, [], <<>>)
    end;
request(#http_client_request{} = Req) ->
    {error, {unexpected_request, Req}}.

ok_response(Status, Headers, Body) ->
    {ok, #http_response{status = Status, headers = Headers, body = Body}}.

call_count() ->
    case get(call_count) of
        undefined -> 0;
        N -> N
    end.

reset() ->
    erase(call_count),
    ok.
