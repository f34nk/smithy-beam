-module(user_http_mock).
-export([request/4]).

request(get, {Url, _Headers, Mime, _Body}, [], [{body_format, binary}]) ->
    case {Url, Mime} of
        {"https://api.example/items", "application/json"} ->
            ok_response(200, [{"etag", "\"v1\""}], <<"{\"ok\":true}">>);
        {"https://api.example/fail", _} ->
            {error, timeout};
        {Url, "application/octet-stream"} ->
            case string:prefix(Url, "https://api.example/items?") of
                nomatch -> {error, {unexpected_request, Url, Mime}};
                _ -> ok_response(200, [], <<>>)
            end;
        _ ->
            {error, {unexpected_request, Url, Mime}}
    end;
request(_Method, _Req, _HttpOpts, _Opts) ->
    {error, unexpected_method}.

ok_response(Status, Headers, Body) ->
    {ok, {{http, Status, <<"OK">>}, Headers, Body}}.
