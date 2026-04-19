-module(smithy_http_client).

%% Generic HTTP wrapper for Smithy-generated Erlang clients.
%% Delegates to httpc (OTP built-in) by default; swap the backend by
%% setting the application environment key `smithy_http_backend` to
%% `httpc` (default) or `hackney`.
%%
%% All public functions return `{ok, StatusCode, Headers, Body}` or
%% `{error, Reason}` so callers are insulated from the underlying library.

-export([
    request/4,
    request/5,
    get/2,
    get/3,
    post/3,
    post/4,
    put/3,
    put/4,
    delete/2,
    delete/3
]).

%% Internal (exported for testing)
-export([backend/0]).

-type method()  :: get | post | put | delete | patch | head | options.
-type headers() :: [{binary() | string(), binary() | string()}].
-type body()    :: binary() | iolist() | <<>>.
-type opts()    :: map().
-type response() ::
    {ok, StatusCode :: non_neg_integer(), headers(), Body :: binary()}
    | {error, term()}.

%%====================================================================
%% Convenience wrappers
%%====================================================================

-spec get(binary() | string(), headers()) -> response().
get(Url, Headers) -> request(get, Url, Headers, <<>>).

-spec get(binary() | string(), headers(), opts()) -> response().
get(Url, Headers, Opts) -> request(get, Url, Headers, <<>>, Opts).

-spec post(binary() | string(), headers(), body()) -> response().
post(Url, Headers, Body) -> request(post, Url, Headers, Body).

-spec post(binary() | string(), headers(), body(), opts()) -> response().
post(Url, Headers, Body, Opts) -> request(post, Url, Headers, Body, Opts).

-spec put(binary() | string(), headers(), body()) -> response().
put(Url, Headers, Body) -> request(put, Url, Headers, Body).

-spec put(binary() | string(), headers(), body(), opts()) -> response().
put(Url, Headers, Body, Opts) -> request(put, Url, Headers, Body, Opts).

-spec delete(binary() | string(), headers()) -> response().
delete(Url, Headers) -> request(delete, Url, Headers, <<>>).

-spec delete(binary() | string(), headers(), opts()) -> response().
delete(Url, Headers, Opts) -> request(delete, Url, Headers, <<>>, Opts).

%%====================================================================
%% Core request/4,5
%%====================================================================

%% @doc Execute an HTTP request.
%%
%% @param Method   HTTP method atom: `get`, `post`, `put`, `delete`, …
%% @param Url      Target URL as binary or string.
%% @param Headers  List of `{Name, Value}` tuples (binaries or strings).
%% @param Body     Request body; use `<<>>` for methods without a body.
%% @returns `{ok, Status, ResponseHeaders, ResponseBody}` or `{error, Reason}`.
-spec request(method(), binary() | string(), headers(), body()) -> response().
request(Method, Url, Headers, Body) ->
    request(Method, Url, Headers, Body, #{}).

-spec request(method(), binary() | string(), headers(), body(), opts()) -> response().
request(Method, Url, Headers, Body, Opts) ->
    case backend() of
        httpc   -> request_httpc(Method, Url, Headers, Body, Opts);
        hackney -> request_hackney(Method, Url, Headers, Body, Opts);
        Mod when is_atom(Mod) -> Mod:request(Method, Url, Headers, Body, Opts)
    end.

%%====================================================================
%% Backend: httpc  (OTP built-in, no external dependency)
%%====================================================================

request_httpc(Method, Url, Headers, Body, _Opts) ->
    UrlStr     = to_string(Url),
    HeaderList = normalise_headers_string(Headers),
    ContentType = content_type(Headers),

    HttpRequest =
        case Method of
            M when M =:= get; M =:= delete; M =:= head ->
                {UrlStr, HeaderList};
            _ ->
                {UrlStr, HeaderList, ContentType, iolist_to_binary(Body)}
        end,

    MethodStr = method_to_string(Method),

    case httpc:request(MethodStr, HttpRequest, [{ssl, [{verify, verify_peer}]}], [{body_format, binary}]) of
        {ok, {{_Proto, Status, _Reason}, RespHeaders, RespBody}} ->
            NormHeaders = [{list_to_binary(K), list_to_binary(V)} || {K, V} <- RespHeaders],
            {ok, Status, NormHeaders, RespBody};
        {error, Reason} ->
            {error, Reason}
    end.

%%====================================================================
%% Backend: hackney  (optional external dependency)
%%====================================================================

request_hackney(Method, Url, Headers, Body, Opts) ->
    Timeout = maps:get(timeout, Opts, 30000),
    HackneyOpts = [
        with_body,
        {recv_timeout, Timeout},
        {connect_timeout, maps:get(connect_timeout, Opts, 5000)}
    ],
    case hackney:request(Method, Url, Headers, Body, HackneyOpts) of
        {ok, Status, RespHeaders, RespBody} ->
            NormHeaders = [{to_binary(K), to_binary(V)} || {K, V} <- RespHeaders],
            {ok, Status, NormHeaders, RespBody};
        {error, Reason} ->
            {error, Reason}
    end.

%%====================================================================
%% Helpers
%%====================================================================

-spec backend() -> httpc | hackney | atom().
backend() ->
    application:get_env(smithy_beam, smithy_http_backend, httpc).

method_to_string(get)     -> get;
method_to_string(post)    -> post;
method_to_string(put)     -> put;
method_to_string(delete)  -> delete;
method_to_string(patch)   -> patch;
method_to_string(head)    -> head;
method_to_string(options) -> options;
method_to_string(M) when is_atom(M) -> M.

normalise_headers_string(Headers) ->
    [{to_string(K), to_string(V)} || {K, V} <- Headers].

content_type(Headers) ->
    Lower = [{string:lowercase(to_string(K)), to_string(V)} || {K, V} <- Headers],
    case lists:keyfind("content-type", 1, Lower) of
        {_, CT} -> CT;
        false   -> "application/octet-stream"
    end.

to_string(B) when is_binary(B) -> binary_to_list(B);
to_string(L) when is_list(L)   -> L;
to_string(A) when is_atom(A)   -> atom_to_list(A).

to_binary(B) when is_binary(B) -> B;
to_binary(L) when is_list(L)   -> list_to_binary(L);
to_binary(A) when is_atom(A)   -> atom_to_binary(A, utf8).
