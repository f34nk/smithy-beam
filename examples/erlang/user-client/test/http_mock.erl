-module(http_mock).
-behaviour(runtime_http_client).

-include("runtime_types.hrl").
-include("runtime_http_client.hrl").

-export([request/1]).

request(#http_client_request{
    method = get,
    url = <<"https://api.example/items">>,
    headers = Headers,
    body = <<>>
}) ->
    case runtime_http_client:content_type(Headers) of
        <<"application/json">> ->
            ok_response(200, [{<<"etag">>, <<"\"v1\"">>}], <<"{\"ok\":true}">>);
        <<"application/octet-stream">> ->
            ok_response(200, [], <<>>);
        CT ->
            {error, {unexpected_request, <<"https://api.example/items">>, CT}}
    end;
request(#http_client_request{
    method = get,
    url = <<"https://api.example/fail">>,
    body = <<>>
}) ->
    {error, timeout};
request(#http_client_request{method = get, url = Url, body = <<>>}) ->
    case binary:match(Url, <<"https://api.example/items?">>) of
        nomatch ->
            CT = runtime_http_client:content_type([]),
            {error, {unexpected_request, Url, CT}};
        _ ->
            ok_response(200, [], <<>>)
    end;
request(#http_client_request{} = Req) ->
    {error, {unexpected_request, Req}}.

ok_response(Status, Headers, Body) ->
    {ok, #http_response{status = Status, headers = Headers, body = Body}}.
