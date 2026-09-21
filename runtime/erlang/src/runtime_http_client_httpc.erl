%% Default HTTP client behaviour implementation using OTP httpc.
-module(runtime_http_client_httpc).
-behaviour(runtime_http_client).

-include("runtime_types.hrl").
-include("runtime_http_client.hrl").

-export([request/1, to_httpc_request/1, from_httpc_response/1]).

-spec request(http_client_request()) -> {ok, http_response()} | {error, term()}.
request(ClientReq) ->
    HttpcReq = to_httpc_request(ClientReq),
    case httpc:request(ClientReq#http_client_request.method, HttpcReq, [], [{body_format, binary}]) of
        {ok, Resp} ->
            from_httpc_response(Resp);
        {error, Reason} ->
            {error, Reason}
    end.

-spec to_httpc_request(http_client_request()) -> term().
to_httpc_request(#http_client_request{url = Url, headers = Headers, body = Body}) ->
    HttpcHeaders = to_httpc_headers(Headers),
    case Body of
        <<>> ->
            {binary_to_list(Url), HttpcHeaders};
        _ ->
            Mime = binary_to_list(runtime_http_client:content_type(Headers)),
            {binary_to_list(Url), HttpcHeaders, Mime, Body}
    end.

-spec to_httpc_headers([{binary(), binary()}]) -> [{string(), string()}].
to_httpc_headers(Headers) ->
    [{binary_to_list(K), binary_to_list(V)} || {K, V} <- Headers].

-spec from_httpc_response(term()) -> {ok, http_response()}.
from_httpc_response({{_, Status, _}, RespHeaders, RespBody}) ->
    BinHeaders = [{list_to_binary(K), list_to_binary(V)} || {K, V} <- RespHeaders],
    {ok, #http_response{
        status = Status,
        headers = BinHeaders,
        body = RespBody
    }}.
