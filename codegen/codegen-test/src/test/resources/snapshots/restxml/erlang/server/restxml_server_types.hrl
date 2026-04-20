-module(restxml_server_types).

-record(list_buckets_input, {
    prefix :: binary()
}).
-type list_buckets_input() :: #list_buckets_input{}.


-record(list_buckets_output, {
    buckets :: [binary()]
}).
-type list_buckets_output() :: #list_buckets_output{}.
