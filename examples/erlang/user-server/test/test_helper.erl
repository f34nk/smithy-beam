-module(test_helper).

-export([init/0]).

init() ->
    ok = user_service_server:init_handlers(),
    ok.
