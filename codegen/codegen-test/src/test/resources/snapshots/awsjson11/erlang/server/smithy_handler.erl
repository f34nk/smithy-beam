-module(smithy_handler).

%% Behaviour definition for Smithy-generated Erlang server handlers.
%%
%% Every generated server operation becomes a callback that the user
%% must implement.  The code generator produces a skeleton module with
%% `-behaviour(smithy_handler).` and a stub for each operation.
%%
%% ## Callback contract
%%
%%   handle_request(OperationName, Input, Context) ->
%%       {ok, Output} | {error, Reason}
%%
%%   OperationName :: atom()   — snake_case operation identifier
%%   Input         :: map()    — decoded, validated request
%%   Context       :: map()    — request metadata (path bindings, headers, …)
%%   Output        :: map()    — response body (to be JSON/XML encoded)
%%   Reason        :: term()   — forwarded to smithy_server:error_response/1
%%
%% ## Optional callbacks
%%
%% Implementations may also export `init/1` and `terminate/2` which
%% are called by the dispatcher before and after each request respectively.
%% Both default to no-ops when not exported.

%% Callback declarations
-callback handle_request(
    OperationName :: atom(),
    Input         :: map(),
    Context       :: map()
) -> {ok, map()} | {error, term()}.

%% Optional lifecycle callbacks — implementations may omit these.
-callback init(Context :: map()) -> {ok, map()} | {error, term()}.
-callback terminate(Reason :: term(), Context :: map()) -> ok.

-optional_callbacks([init/1, terminate/2]).

%% Public helpers for handler implementors
-export([
    ok/1,
    error/1,
    not_found/0,
    context_path_binding/2,
    context_header/2
]).

%%====================================================================
%% Response builders
%%====================================================================

%% @doc Wrap a successful output map in the `{ok, Output}` tuple expected
%% by the dispatcher.
-spec ok(map()) -> {ok, map()}.
ok(Output) when is_map(Output) ->
    {ok, Output}.

%% @doc Wrap an error reason in the `{error, Reason}` tuple expected by
%% the dispatcher, which delegates it to `smithy_server:error_response/1`.
-spec error(term()) -> {error, term()}.
error(Reason) ->
    {error, Reason}.

%% @doc Signal that the operation is not found / not implemented.
-spec not_found() -> {error, not_found}.
not_found() ->
    {error, not_found}.

%%====================================================================
%% Context helpers
%%====================================================================

%% @doc Retrieve a path binding from the request context.
%%
%% Returns `{ok, Value}` when the binding exists or `{error, not_found}`.
-spec context_path_binding(Context :: map(), Name :: binary()) ->
    {ok, binary()} | {error, not_found}.
context_path_binding(Context, Name) ->
    Bindings = maps:get(path_bindings, Context, #{}),
    case maps:find(Name, Bindings) of
        {ok, _} = Ok -> Ok;
        error         -> {error, not_found}
    end.

%% @doc Retrieve a request header value from the request context.
%%
%% Header names are lowercased in the context map.
%% Returns `{ok, Value}` or `{error, not_found}`.
-spec context_header(Context :: map(), Name :: binary()) ->
    {ok, binary()} | {error, not_found}.
context_header(Context, Name) ->
    Headers = maps:get(headers, Context, #{}),
    LowerName = string:lowercase(Name),
    case maps:find(LowerName, Headers) of
        {ok, _} = Ok -> Ok;
        error         -> {error, not_found}
    end.
