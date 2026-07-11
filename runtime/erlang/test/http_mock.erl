-module(http_mock).
-export([request/4]).

request(get, {Url, _Headers}, [], [{body_format, binary}]) ->
    case Url of
        "https://api.example/items" ->
            ok_response(200, [{"etag", "\"v1\""}], <<"{\"ok\":true}">>);
        "https://api.example/fail" ->
            {error, timeout};
        Url ->
            case string:prefix(Url, "https://api.example/items?") of
                nomatch -> {error, {unexpected_request, Url}};
                _ -> ok_response(200, [], <<>>)
            end
    end;
request(post, {Url, _Headers, _Mime, Body}, [], [{body_format, binary}]) ->
    case {Url, Body} of
        {"https://api.example/items", <<"{\"name\":\"item\"}">>} ->
            ok_response(201, [], <<"{\"id\":1}">>);
        _ ->
            {error, {unexpected_request, Url}}
    end;
request(_Method, Req, _HttpOpts, _Opts) ->
    {error, {unexpected_request, Req}}.

ok_response(Status, Headers, Body) ->
    {ok, {{http, Status, <<"OK">>}, Headers, Body}}.
