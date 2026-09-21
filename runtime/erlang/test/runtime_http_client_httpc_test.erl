-module(runtime_http_client_httpc_test).

-include_lib("eunit/include/eunit.hrl").
-include("runtime_types.hrl").
-include("runtime_http_client.hrl").

to_httpc_request_without_body_test() ->
    Req = #http_client_request{
        method = get,
        url = <<"https://api.example/items">>,
        headers = [{<<"accept">>, <<"application/json">>}],
        body = <<>>
    },
    ?assertEqual(
        {"https://api.example/items", [{"accept", "application/json"}]},
        runtime_http_client_httpc:to_httpc_request(Req)
    ).

to_httpc_request_with_body_test() ->
    Req = #http_client_request{
        method = post,
        url = <<"https://api.example/items">>,
        headers = [{<<"Content-Type">>, <<"application/json">>}],
        body = <<"{\"name\":\"item\"}">>
    },
    ?assertEqual(
        {
            "https://api.example/items",
            [{"Content-Type", "application/json"}],
            "application/json",
            <<"{\"name\":\"item\"}">>
        },
        runtime_http_client_httpc:to_httpc_request(Req)
    ).

from_httpc_response_test() ->
    HttpcResp = {{http, 200, <<"OK">>}, [{"etag", "\"v1\""}], <<"{\"ok\":true}">>},
    {ok, Resp} = runtime_http_client_httpc:from_httpc_response(HttpcResp),
    ?assertEqual(200, Resp#http_response.status),
    ?assertEqual([{<<"etag">>, <<"\"v1\"">>}], Resp#http_response.headers),
    ?assertEqual(<<"{\"ok\":true}">>, Resp#http_response.body).
