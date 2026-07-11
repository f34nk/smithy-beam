-module(http_mock).
-export([request/4]).

request(get, Req, [], [{body_format, binary}]) ->
    {Url, Mime} = request_url_and_mime(Req),
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

request_url_and_mime({Url, Headers}) ->
    {Url, header_mime(Headers)};
request_url_and_mime({Url, Headers, Mime, _Body}) ->
    {Url, case Mime of
        undefined -> header_mime(Headers);
        _ -> Mime
    end}.

header_mime(Headers) ->
    case proplists:get_value("Content-Type", Headers) of
        undefined -> "application/octet-stream";
        CT -> CT
    end.

ok_response(Status, Headers, Body) ->
    {ok, {{http, Status, <<"OK">>}, Headers, Body}}.
