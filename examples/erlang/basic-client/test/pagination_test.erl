-module(pagination_test).

-include_lib("eunit/include/eunit.hrl").
-include("basic_service_types.hrl").

client_list_basic_items_collects_all_pages_test() ->
    Config = #{base_url => <<"https://api.example">>,
               http_client => http_mock},
    Input = #list_basic_items_input{},
    ?assertEqual(
        {ok, [
            #basic_item{name = <<"alpha">>, count = 1},
            #basic_item{name = <<"beta">>, count = 2}
        ]},
        basic_service_client:list_basic_items(Config, Input)
    ).
