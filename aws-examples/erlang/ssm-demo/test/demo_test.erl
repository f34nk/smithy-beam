-module(demo_test).
-include_lib("eunit/include/eunit.hrl").

demo_run_exported_test() ->
    ?assert(erlang:function_exported(demo_app, run, 0)).
