-ifndef(BEAM_RUNTIME_TYPES_INCLUDED).
-define(BEAM_RUNTIME_TYPES_INCLUDED, true).

%% HTTP carrier types for generated clients. Adjust only via codegen.
-record(http_request, {
    method = <<"GET">> :: binary(),
    path = <<"/">> :: binary(),
    query = #{} :: #{binary() => binary()},
    headers = [] :: [{binary(), binary()}],
    body = <<>> :: iodata(),
    host = undefined :: binary() | undefined,
    stream = undefined :: term() | undefined
}).
-type http_request() :: #http_request{}.

-record(http_response, {
    status = 200 :: non_neg_integer(),
    headers = [] :: [{binary(), binary()}],
    body = <<>> :: iodata(),
    stream = undefined :: term() | undefined
}).
-type http_response() :: #http_response{}.

-endif.
