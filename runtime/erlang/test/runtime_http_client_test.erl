-module(runtime_http_client_test).

-include_lib("eunit/include/eunit.hrl").
-include("runtime_types.hrl").
-include("runtime_http_client.hrl").

build_url_without_query_test() ->
    Config = #{base_url => <<"https://api.example">>},
    Req = #http_request{
        method = <<"GET">>,
        path = <<"/items">>,
        query = #{},
        headers = [],
        body = <<>>
    },
    ?assertEqual(
        <<"https://api.example/items">>,
        runtime_http_client:build_url(Config, Req)
    ).

build_url_with_query_test() ->
    Config = #{base_url => <<"https://api.example">>},
    Req = #http_request{
        method = <<"GET">>,
        path = <<"/items">>,
        query = #{<<"verbose">> => <<"true">>},
        headers = [],
        body = <<>>
    },
    Url = runtime_http_client:build_url(Config, Req),
    ?assertNotEqual(nomatch, binary:match(Url, <<"?verbose=true">>)).

build_request_test() ->
    Config = #{base_url => <<"https://api.example">>},
    Req = #http_request{
        method = <<"POST">>,
        path = <<"/items">>,
        query = #{},
        headers = [{<<"Content-Type">>, <<"application/json">>}],
        body = <<"{\"name\":\"item\"}">>
    },
    ClientReq = runtime_http_client:build_request(Config, Req),
    ?assertEqual(post, ClientReq#http_client_request.method),
    ?assertEqual(<<"https://api.example/items">>, ClientReq#http_client_request.url),
    ?assertEqual(Req#http_request.headers, ClientReq#http_client_request.headers),
    ?assertEqual(Req#http_request.body, ClientReq#http_client_request.body).

content_type_defaults_test() ->
    ?assertEqual(
        <<"application/octet-stream">>,
        runtime_http_client:content_type([])
    ).

content_type_from_header_test() ->
    Headers = [{<<"Content-Type">>, <<"application/json">>}],
    ?assertEqual(<<"application/json">>, runtime_http_client:content_type(Headers)).
