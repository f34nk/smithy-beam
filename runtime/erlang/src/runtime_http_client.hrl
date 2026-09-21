%% Types for the runtime HTTP client behaviour.
-ifndef(BEAM_RUNTIME_HTTP_CLIENT_INCLUDED).
-define(BEAM_RUNTIME_HTTP_CLIENT_INCLUDED, true).

-record(http_client_request, {
    method :: atom(),
    url :: binary(),
    headers = [] :: [{binary(), binary()}],
    body = <<>> :: iodata()
}).
-type http_client_request() :: #http_client_request{}.

-endif.
