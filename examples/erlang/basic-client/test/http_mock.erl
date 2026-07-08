-module(http_mock).
-export([request/4]).

request(get, Req, [], [{body_format, binary}]) ->
    {Url, _Mime} = request_url_and_mime(Req),
    case Url of
        "https://api.example/basic-items" ->
            page1_response();
        "https://api.example/basic-items?nextToken=page2" ->
            page2_response();
        _ ->
            {error, {unexpected_request, Url}}
    end;
request(_Method, _Req, _HttpOpts, _Opts) ->
    {error, unexpected_method}.

request_url_and_mime({Url, _Headers}) ->
    {Url, undefined};
request_url_and_mime({Url, _Headers, Mime, _Body}) ->
    {Url, Mime}.

page1_response() ->
    Body =
        <<"{\"items\":[{\"name\":\"alpha\",\"count\":1}],\"nextToken\":\"page2\"}">>,
    ok_response(200, [], Body).

page2_response() ->
    Body = <<"{\"items\":[{\"name\":\"beta\",\"count\":2}]}">>,
    ok_response(200, [], Body).

ok_response(Status, Headers, Body) ->
    {ok, {{http, Status, <<"OK">>}, Headers, Body}}.
