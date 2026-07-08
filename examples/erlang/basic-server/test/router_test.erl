-module(router_test).

-include_lib("eunit/include/eunit.hrl").
-include("basic_service_types.hrl").
-include("http_types.hrl").

%% Request flow: http_request -> basic_service_router:dispatch/2 -> codec decode -> Handler:handle_<op>/3.
%% Pass basic_service_server as Handler to invoke the generated server stub.

dispatch_routes_list_basic_items_before_type_prefix_test() ->
    Req = #http_request{
        method = <<"GET">>,
        path = <<"/basic-items">>,
        query = #{},
        headers = [],
        body = <<>>
    },
    ?assertEqual({error, not_implemented}, basic_service_router:dispatch(basic_service_server, Req)).

dispatch_not_found_for_extra_type_segments_test() ->
    Req = #http_request{
        method = <<"GET">>,
        path = <<"/types/a/b">>,
        query = #{},
        headers = [],
        body = <<>>
    },
    ?assertEqual(
        {error, {not_found, <<"GET">>, <<"/types/a/b">>}},
        basic_service_router:dispatch(basic_service_server, Req)
    ).

dispatch_routes_to_basic_server_handler_test() ->
    Req = #http_request{
        method = <<"GET">>,
        path = <<"/types/widget">>,
        query = #{<<"verbose">> => <<"true">>},
        headers = [{<<"X-Request-Tag">>, <<"trace-1">>}],
        body = <<>>
    },
    ?assertEqual(
        {error, not_implemented},
        basic_service_router:dispatch(basic_service_server, Req)
    ).

dispatch_decodes_wire_request_before_handler_test() ->
    Req = #http_request{
        method = <<"GET">>,
        path = <<"/types/widget">>,
        query = #{<<"verbose">> => <<"true">>},
        headers = [{<<"X-Request-Tag">>, <<"trace-1">>}],
        body = <<>>
    },
    {ok, Out} = basic_service_router:dispatch(server_probe, Req),
    ?assertEqual(<<"widget">>, Out#get_type_closure_output.basic_string),
    ?assertEqual(true, Out#get_type_closure_output.basic_boolean).

dispatch_not_found_for_unknown_route_test() ->
    Req = #http_request{
        method = <<"POST">>,
        path = <<"/types/widget">>,
        query = #{},
        headers = [],
        body = <<>>
    },
    ?assertEqual(
        {error, {not_found, <<"POST">>, <<"/types/widget">>}},
        basic_service_router:dispatch(basic_service_server, Req)
    ).
