-module(smithy_server).

%% HTTP abstraction layer for Smithy server-side dispatch.
%% Framework-agnostic API; the Cowboy implementation is provided here.
%% The return type for response helpers is {StatusCode, Headers, Body} —
%% a plain tuple. The application's Cowboy handler converts it to a
%% framework-specific response.

-export([
    extract/1,
    response/2,
    error_response/1,
    validation_error/1,
    not_found/0
]).

%%%===================================================================
%%% API Functions
%%%===================================================================

%% @doc Extract method, path, headers, and body from a Cowboy request.
%% Returns {Method, Path, Headers, Body} where all values are binaries.
-spec extract(Req :: cowboy_req:req()) ->
    {Method :: binary(), Path :: binary(), Headers :: cowboy:http_headers(), Body :: binary()}.
extract(Req) ->
    Method  = cowboy_req:method(Req),
    Path    = cowboy_req:path(Req),
    Headers = cowboy_req:headers(Req),
    {ok, Body, _Req2} = cowboy_req:read_body(Req),
    {Method, Path, Headers, Body}.

%% @doc Build a successful response tuple with JSON content-type.
%% Returns {StatusCode, Headers, Body}.
-spec response(Code :: pos_integer(), Body :: iodata()) ->
    {pos_integer(), [{binary(), binary()}], iodata()}.
response(Code, Body) ->
    {Code, [{<<"content-type">>, <<"application/json">>}], Body}.

%% @doc Build an error response from a modeled error term.
%% Delegates HTTP status and message lookup to smithy_error_map.
-spec error_response(Err :: term()) ->
    {pos_integer(), [{binary(), binary()}], binary()}.
error_response(Err) ->
    {Code, Msg} = smithy_error_map:to_http(Err),
    Body = jsx:encode(#{<<"message">> => Msg}),
    {Code, [{<<"content-type">>, <<"application/json">>}], Body}.

%% @doc Build a 400 validation-error response.
%% Reason is a term accepted by smithy_validator:format/1.
-spec validation_error(Reason :: term()) ->
    {400, [{binary(), binary()}], binary()}.
validation_error(Reason) ->
    Body = jsx:encode(#{<<"message">> => smithy_validator:format(Reason)}),
    {400, [{<<"content-type">>, <<"application/json">>}], Body}.

%% @doc Build a 404 Not Found response.
-spec not_found() -> {404, [], binary()}.
not_found() ->
    {404, [], <<"Not Found">>}.
