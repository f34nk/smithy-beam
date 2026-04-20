-module(restjson1_server_types).

-record(echo_message_input, {
    message :: binary()
}).
-type echo_message_input() :: #echo_message_input{}.


-record(echo_message_output, {
    message :: binary()
}).
-type echo_message_output() :: #echo_message_output{}.
