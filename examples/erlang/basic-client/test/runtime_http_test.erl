-module(runtime_http_test).

-include_lib("eunit/include/eunit.hrl").
-include("runtime_types.hrl").

dispatch_builds_url_without_query_test() ->
    Config = #{base_url => <<"https://api.example">>},
    Req = #http_request{
        method = <<"GET">>,
        path = <<"/items">>,
        query = #{},
        headers = [{<<"Content-Type">>, <<"application/json">>}],
        body = <<>>
    },
    {ok, Resp} = runtime_http:dispatch(runtime_http_mock, Config, Req),
    ?assertEqual(200, Resp#http_response.status),
    ?assertEqual([{<<"etag">>, <<"\"v1\"">>}], Resp#http_response.headers),
    ?assertEqual(<<"{\"ok\":true}">>, Resp#http_response.body).

dispatch_appends_query_string_test() ->
    Config = #{base_url => <<"https://api.example">>},
    Req = #http_request{
        method = <<"GET">>,
        path = <<"/items">>,
        query = #{<<"verbose">> => <<"true">>},
        headers = [],
        body = <<>>
    },
    {ok, Resp} = runtime_http:dispatch(runtime_http_mock, Config, Req),
    ?assertEqual(200, Resp#http_response.status),
    ?assertEqual(<<>>, Resp#http_response.body).

dispatch_propagates_client_error_test() ->
    Config = #{base_url => <<"https://api.example">>},
    Req = #http_request{
        method = <<"GET">>,
        path = <<"/fail">>,
        query = #{},
        headers = [],
        body = <<>>
    },
    ?assertEqual(
        {error, timeout},
        runtime_http:dispatch(runtime_http_mock, Config, Req)
    ).

dispatch_exports_test() ->
    Exports = runtime_http:module_info(exports),
    ?assert(lists:member({dispatch, 2}, Exports)),
    ?assert(lists:member({dispatch, 3}, Exports)).
