-module(retry_test).

-include_lib("eunit/include/eunit.hrl").
-include("basic_service_types.hrl").

get_type_closure_retries_on_basic_not_found_test() ->
    retry_mock:reset(),
    Config = #{
        base_url => <<"https://api.example">>,
        http_client => retry_mock,
        retry => #{max_attempts => 2, base_delay_ms => 0}
    },
    Input = #get_type_closure_input{name = <<"widget">>},
    ?assertMatch({ok, _}, basic_service_client:get_type_closure(Config, Input)),
    ?assertEqual(2, retry_mock:call_count()).
