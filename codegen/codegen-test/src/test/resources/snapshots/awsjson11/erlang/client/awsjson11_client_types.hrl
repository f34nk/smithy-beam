-module(awsjson11_client_types).

-record(describe_item_input, {
    item_id :: binary()
}).
-type describe_item_input() :: #describe_item_input{}.


-record(describe_item_output, {
    name :: binary(),
    description :: binary()
}).
-type describe_item_output() :: #describe_item_output{}.
