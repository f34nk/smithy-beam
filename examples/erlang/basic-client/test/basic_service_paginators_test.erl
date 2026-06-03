-module(basic_service_paginators_test).

-include_lib("eunit/include/eunit.hrl").
-include("basic_service_types.hrl").

paginate_list_basic_items_collects_all_pages_test() ->
    Config = #{base_url => <<"https://api.example">>, http_client => runtime_http_mock},
    Input = #list_basic_items_input{},
    ?assertEqual(
        {ok, [
            #{<<"name">> => <<"alpha">>, <<"count">> => 1},
            #{<<"name">> => <<"beta">>, <<"count">> => 2}
        ]},
        basic_service_paginators:paginate_list_basic_items(Config, Input)
    ).

paginate_list_basic_items_exports_test() ->
    Exports = basic_service_paginators:module_info(exports),
    ?assert(lists:member({paginate_list_basic_items, 2}, Exports)),
    ?assert(lists:member({paginate_list_basic_items, 3}, Exports)).
