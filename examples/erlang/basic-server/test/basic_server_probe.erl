-module(basic_server_probe).

-include("basic_types.hrl").

-export([handle_get_type_closure/3]).

%% Test double: echoes decoded input fields into a minimal output record.
handle_get_type_closure(_Ctx, #get_type_closure_input{name = Name, verbose = Verbose}, _Meta) ->
    {ok, #get_type_closure_output{
        basic_string = Name,
        basic_boolean = Verbose
    }}.
