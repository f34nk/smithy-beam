-module(basic_service_retry_wiring_mock).
-export([request/4, call_count/0, reset/0]).

request(get, _Req, [], [{body_format, binary}]) ->
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
                [{"content-type", "application/json"}],
                Body
            );
        _ ->
            ok_response(200, [], <<>>)
    end;
request(_Method, _Req, _HttpOpts, _Opts) ->
    {error, unexpected_method}.

ok_response(Status, Headers, Body) ->
    {ok, {{http, Status, <<"OK">>}, Headers, Body}}.

call_count() ->
    case get(call_count) of
        undefined -> 0;
        N -> N
    end.

reset() ->
    erase(call_count),
    ok.
