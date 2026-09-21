-module(head_bucket_http_mock).
-behaviour(runtime_http_client).

-include("runtime_types.hrl").
-include("runtime_http_client.hrl").

-export([request/1, reset/1, call_count/0]).

-define(RESPONSES_KEY, {head_bucket_http_mock, responses}).
-define(COUNT_KEY, {head_bucket_http_mock, count}).

reset(Responses) when is_list(Responses) ->
    persistent_term:put(?RESPONSES_KEY, Responses),
    persistent_term:put(?COUNT_KEY, 0),
    ok.

call_count() ->
    persistent_term:get(?COUNT_KEY, 0).

request(#http_client_request{method = head, url = Url}) ->
    case binary:match(Url, <<"/my-bucket">>) of
        nomatch ->
            {error, {unexpected_request, Url}};
        _ ->
            N = call_count(),
            persistent_term:put(?COUNT_KEY, N + 1),
            Responses = persistent_term:get(?RESPONSES_KEY, []),
            case lists:nth(N + 1, Responses ++ [no_response]) of
                {status, Status} ->
                    {ok, #http_response{status = Status, headers = [], body = <<>>}};
                no_response ->
                    {error, {no_mock_response, N}}
            end
    end;
request(#http_client_request{} = Req) ->
    {error, {unexpected_request, Req}}.
