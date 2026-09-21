-module(http_mock).
-behaviour(runtime_http_client).

-include("runtime_types.hrl").
-include("runtime_http_client.hrl").

-export([request/1]).

request(#http_client_request{method = get, url = <<"https://api.example/basic-items">>, body = <<>>}) ->
    page1_response();
request(#http_client_request{
    method = get,
    url = <<"https://api.example/basic-items?nextToken=page2">>,
    body = <<>>
}) ->
    page2_response();
request(#http_client_request{} = Req) ->
    {error, {unexpected_request, Req}}.

page1_response() ->
    Body =
        <<"{\"items\":[{\"name\":\"alpha\",\"count\":1}],\"nextToken\":\"page2\"}">>,
    ok_response(200, [], Body).

page2_response() ->
    Body = <<"{\"items\":[{\"name\":\"beta\",\"count\":2}]}">>,
    ok_response(200, [], Body).

ok_response(Status, Headers, Body) ->
    {ok, #http_response{status = Status, headers = Headers, body = Body}}.
