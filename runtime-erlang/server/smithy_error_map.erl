-module(smithy_error_map).

%% Maps Smithy error atoms/tuples to HTTP status codes and message binaries.
%%
%% This is the generic fallback module used when a service has no modeled errors.
%% For services that do define error shapes with @httpError, the code generator
%% produces a per-service override of this module that prepends specific clauses
%% for each modeled error before falling through to these defaults.

-export([to_http/1]).

%%%===================================================================
%%% API Functions
%%%===================================================================

%% @doc Map a Smithy error term to {HttpStatusCode, MessageBinary}.
%%
%% Supported error terms:
%%   {not_found, Msg}    -> 404
%%   {conflict, Msg}     -> 409
%%   {validation, Msg}   -> 400
%%   {internal, Msg}     -> 500
%%   {unauthorized, Msg} -> 401
%%   {forbidden, Msg}    -> 403
%%   not_implemented     -> 501
%%   _                   -> 500 (catch-all)
-spec to_http(Err :: term()) -> {pos_integer(), binary()}.
to_http({not_found, Msg})    -> {404, Msg};
to_http({conflict, Msg})     -> {409, Msg};
to_http({validation, Msg})   -> {400, Msg};
to_http({internal, Msg})     -> {500, Msg};
to_http({unauthorized, Msg}) -> {401, Msg};
to_http({forbidden, Msg})    -> {403, Msg};
to_http(not_implemented)     -> {501, <<"Not implemented">>};
to_http(_)                   -> {500, <<"Internal server error">>}.
