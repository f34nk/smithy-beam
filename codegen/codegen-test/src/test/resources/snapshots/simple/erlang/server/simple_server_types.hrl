-module(simple_server_types).

-record(get_item_input, {
    id :: binary()
}).
-type get_item_input() :: #get_item_input{}.


-record(get_item_output, {
    name :: binary(),
    value :: binary()
}).
-type get_item_output() :: #get_item_output{}.
