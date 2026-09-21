-module(http_mock).
-behaviour(runtime_http_client).

-include("runtime_types.hrl").
-include("runtime_http_client.hrl").

-export([request/1]).

request(#http_client_request{method = get, url = <<"https://api.example/items">>, body = <<>>}) ->
    ok_response(200, [{<<"etag">>, <<"\"v1\"">>}], <<"{\"ok\":true}">>);
request(#http_client_request{method = get, url = <<"https://api.example/fail">>, body = <<>>}) ->
    {error, timeout};
request(#http_client_request{method = get, url = Url, body = <<>>}) ->
    case binary:match(Url, <<"https://api.example/items?">>) of
        nomatch ->
            {error, {unexpected_request, Url}};
        _ ->
            ok_response(200, [], <<>>)
    end;
request(#http_client_request{
    method = post,
    url = <<"https://api.example/items">>,
    body = <<"{\"name\":\"item\"}">>
}) ->
    ok_response(201, [], <<"{\"id\":1}">>);
request(#http_client_request{} = Req) ->
    {error, {unexpected_request, Req}}.

ok_response(Status, Headers, Body) ->
    {ok, #http_response{status = Status, headers = Headers, body = Body}}.
