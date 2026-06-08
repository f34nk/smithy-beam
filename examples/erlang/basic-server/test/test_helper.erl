-module(test_helper).

-export([init/0]).

init() ->
    ok = basic_service_server:init_handlers(),
    ok.
