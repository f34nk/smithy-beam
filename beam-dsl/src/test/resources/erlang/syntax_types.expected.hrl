-ifndef(SYNTAX_TYPES_INCLUDED).
-define(SYNTAX_TYPES_INCLUDED, true).

-define(DEFAULT_HANDLER, default).
-define(HANDLERS_KEY, handlers).

-record(item, {
    name :: binary(),
    count :: integer() | undefined
}).
-type item() :: #item{}.

-record(request, {
    method = <<"GET">> :: binary(),
    path = <<"/">> :: binary(),
    query = #{} :: #{binary() => binary()},
    headers = [] :: [{binary(), binary()}],
    body = <<>> :: iodata(),
    host = undefined :: binary() | undefined
}).
-type request() :: #request{}.

-record(response, {
    status = 200 :: non_neg_integer(),
    headers = [] :: [{binary(), binary()}],
    body = <<>> :: iodata()
}).
-type response() :: #response{}.

-record(tagged_error, {
    code :: atom(),
    message :: binary() | undefined
}).
-type tagged_error() :: #tagged_error{}.

-type client_config() :: #{binary() => term()}.
-type credentials() ::
    #{access_key := binary(), secret_key := binary(), token => binary()
    | undefined}.

-endif.
