-module(user_service_router_test).

-include_lib("eunit/include/eunit.hrl").
-include("user_service_types.hrl").
-include("http_types.hrl").

%% Request flow: http_request -> user_service_router:dispatch/2 -> codec decode -> Handler:handle_<op>/3.
%% Pass user_service_server as Handler to invoke the generated server stub.

dispatch_routes_list_users_before_user_id_path_test() ->
    Req = #http_request{
        method = <<"GET">>,
        path = <<"/users">>,
        query = #{},
        headers = [],
        body = <<>>
    },
    ?assertEqual({error, not_implemented}, user_service_router:dispatch(user_service_server, Req)).

dispatch_not_found_for_extra_user_path_segments_test() ->
    Req = #http_request{
        method = <<"GET">>,
        path = <<"/users/u-1/extra">>,
        query = #{},
        headers = [],
        body = <<>>
    },
    ?assertEqual(
        {error, {not_found, <<"GET">>, <<"/users/u-1/extra">>}},
        user_service_router:dispatch(user_service_server, Req)
    ).

dispatch_routes_to_user_server_handler_test() ->
    Req = #http_request{
        method = <<"GET">>,
        path = <<"/users/u-1">>,
        query = #{},
        headers = [],
        body = <<>>
    },
    ?assertEqual(
        {error, not_implemented},
        user_service_router:dispatch(user_service_server, Req)
    ).

dispatch_decodes_wire_request_before_handler_test() ->
    Req = #http_request{
        method = <<"GET">>,
        path = <<"/users/u-1">>,
        query = #{},
        headers = [],
        body = <<>>
    },
    {ok, Out} = user_service_router:dispatch(user_service_server_probe, Req),
    ?assertEqual(#{<<"userId">> => <<"u-1">>}, Out#get_user_output.user).

dispatch_not_found_for_unknown_route_test() ->
    Req = #http_request{
        method = <<"POST">>,
        path = <<"/users/u-1">>,
        query = #{},
        headers = [],
        body = <<>>
    },
    ?assertEqual(
        {error, {not_found, <<"POST">>, <<"/users/u-1">>}},
        user_service_router:dispatch(user_service_server, Req)
    ).

dispatch_routes_create_user_test() ->
    Req = #http_request{
        method = <<"POST">>,
        path = <<"/users">>,
        query = #{},
        headers = [{<<"Content-Type">>, <<"application/json">>}],
        body = jsone:encode(#{<<"email">> => <<"a@example.com">>})
    },
    ?assertEqual({error, not_implemented}, user_service_router:dispatch(user_service_server, Req)).
