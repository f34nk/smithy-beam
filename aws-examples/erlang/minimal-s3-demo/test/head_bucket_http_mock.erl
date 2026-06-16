-module(head_bucket_http_mock).
-export([request/4, reset/1, call_count/0]).

-define(RESPONSES_KEY, {head_bucket_http_mock, responses}).
-define(COUNT_KEY, {head_bucket_http_mock, count}).

reset(Responses) when is_list(Responses) ->
    persistent_term:put(?RESPONSES_KEY, Responses),
    persistent_term:put(?COUNT_KEY, 0),
    ok.

call_count() ->
    persistent_term:get(?COUNT_KEY, 0).

request(head, {Url, _Headers}, [], [{body_format, binary}]) ->
    case lists:suffix("/my-bucket", Url) of
        false ->
            {error, {unexpected_request, Url}};
        true ->
            N = call_count(),
            persistent_term:put(?COUNT_KEY, N + 1),
            Responses = persistent_term:get(?RESPONSES_KEY, []),
            case lists:nth(N + 1, Responses ++ [no_response]) of
                {status, Status} ->
                    {ok, {{http, Status, <<"OK">>}, [], <<>>}};
                no_response ->
                    {error, {no_mock_response, N}}
            end
    end;
request(_Method, Req, _HttpOpts, _Opts) ->
    {error, {unexpected_request, Req}}.
