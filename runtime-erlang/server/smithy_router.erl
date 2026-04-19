-module(smithy_router).

%% Request routing for Smithy-generated Erlang servers.
%%
%% A generated server module calls `smithy_router:dispatch/4` with the
%% parsed request components and a routing table built from the service's
%% Smithy model.  Each route entry maps `{Method, PathPattern}` to a
%% handler callback `{Module, Function}`.
%%
%% Path patterns follow the Smithy URI template syntax:
%%   "/items/{id}"        → matches "/items/42",  binds Id = <<"42">>
%%   "/prefix/{+proxy}"  → greedy label, matches the rest of the path
%%
%% ## Route table format
%%
%%   Routes :: [{Method, PathPattern, Module, Function}]
%%
%%   Method      :: binary()  (uppercase, e.g. <<"GET">>)
%%   PathPattern :: binary()  (e.g. <<"/items/{id}">>)
%%   Module      :: module()
%%   Function    :: atom()
%%
%% ## Dispatch protocol
%%
%% The handler `Module:Function/3` is called as:
%%   Module:Function(PathBindings, Headers, Body)
%% where `PathBindings` is a map of `binary() => binary()` label values.
%%
%% The handler must return one of:
%%   {ok, Result}        → 200 JSON response (Result is JSON-encodable)
%%   {ok, Status, Body}  → custom status with pre-encoded body
%%   {error, Reason}     → delegated to smithy_server:error_response/1
%%   not_found           → 404

-export([
    dispatch/4,
    match/2
]).

%% Internal (exported for testing)
-export([
    match_path/2,
    extract_bindings/2,
    split_path/1
]).

-type method()       :: binary().
-type path_pattern() :: binary().
-type route()        :: {method(), path_pattern(), module(), atom()}.
-type routes()       :: [route()].
-type bindings()     :: #{binary() => binary()}.
-type headers()      :: #{binary() => binary()} | [{binary(), binary()}].
-type body()         :: binary().

%%====================================================================
%% Public API
%%====================================================================

%% @doc Dispatch an incoming request to the matching handler.
%%
%% Routes are looked up from the application environment key
%% `{smithy_beam, routes}`.  Register routes before calling this function
%% (typically in your application's `start/2` callback):
%%
%%   application:set_env(smithy_beam, routes, MyServer:routes())
%%
%% @param Method   Uppercase HTTP method binary (e.g. `<<"POST">>`).
%% @param Path     URL path binary (e.g. `<<"/items/42">>`).
%% @param Headers  Request headers map or list.
%% @param Body     Raw request body binary.
%% @returns Handler return value, `not_found`, or `{error, routes_not_configured}`.
-spec dispatch(method(), binary(), headers(), body()) -> term().
dispatch(Method, Path, Headers, Body) ->
    case application:get_env(smithy_beam, routes) of
        {ok, Routes} -> dispatch(Routes, Method, Path, Headers, Body);
        undefined    -> {error, routes_not_configured}
    end.

%% @doc Dispatch using an explicit route table.
%%
%% Iterates the route table in order, stopping at the first match.
%% Calls the matched handler with `(Bindings, Headers, Body)`.
%%
%% @param Routes   Route table (list of 4-tuples).
%% @param Method   Uppercase HTTP method binary.
%% @param Path     URL path binary.
%% @param Headers  Request headers.
%% @param Body     Raw request body binary.
%% @returns Handler return value or `not_found`.
-spec dispatch(routes(), method(), binary(), headers(), body()) -> term().
dispatch([], _Method, _Path, _Headers, _Body) ->
    not_found;
dispatch([{RouteMethod, Pattern, Mod, Fun} | Rest], Method, Path, Headers, Body) ->
    case RouteMethod =:= Method orelse RouteMethod =:= <<"*">> of
        false ->
            dispatch(Rest, Method, Path, Headers, Body);
        true ->
            case match_path(Pattern, Path) of
                {ok, Bindings} ->
                    Mod:Fun(Bindings, Headers, Body);
                nomatch ->
                    dispatch(Rest, Method, Path, Headers, Body)
            end
    end.

%% @doc Return the first matching route entry for the given method and path.
%%
%% @returns `{ok, Bindings, Module, Function}` or `nomatch`.
-spec match(routes(), {method(), binary()}) ->
    {ok, bindings(), module(), atom()} | nomatch.
match([], _) ->
    nomatch;
match([{RouteMethod, Pattern, Mod, Fun} | Rest], {Method, Path}) ->
    case RouteMethod =:= Method orelse RouteMethod =:= <<"*">> of
        false ->
            match(Rest, {Method, Path});
        true ->
            case match_path(Pattern, Path) of
                {ok, Bindings} -> {ok, Bindings, Mod, Fun};
                nomatch        -> match(Rest, {Method, Path})
            end
    end.

%%====================================================================
%% Path matching
%%====================================================================

%% @doc Test whether `Path` matches `Pattern`, returning label bindings.
%%
%% Pattern segments enclosed in `{...}` are label captures.
%% A label prefixed with `+` (i.e. `{+proxy}`) greedily captures the
%% remainder of the path including `/` separators.
%%
%% @returns `{ok, Bindings}` or `nomatch`.
-spec match_path(path_pattern(), binary()) -> {ok, bindings()} | nomatch.
match_path(Pattern, Path) ->
    PatternSegs = split_path(Pattern),
    PathSegs    = split_path(Path),
    match_segments(PatternSegs, PathSegs, #{}).

match_segments([], [], Bindings) ->
    {ok, Bindings};
match_segments([<<"{+", Rest/binary>> | _PatRest], PathSegs, Bindings) ->
    %% Greedy label: consumes all remaining path segments
    LabelName = binary:part(Rest, 0, byte_size(Rest) - 1),  %% strip trailing }
    Value = iolist_to_binary(lists:join(<<"/">>, PathSegs)),
    {ok, Bindings#{LabelName => Value}};
match_segments([<<"{"  , Rest/binary>> | PatRest], [PathSeg | PathRest], Bindings) ->
    LabelName = binary:part(Rest, 0, byte_size(Rest) - 1),
    match_segments(PatRest, PathRest, Bindings#{LabelName => PathSeg});
match_segments([Literal | PatRest], [Literal | PathRest], Bindings) ->
    match_segments(PatRest, PathRest, Bindings);
match_segments(_, _, _) ->
    nomatch.

%% @doc Split a URL path by `/`, discarding empty segments from leading slash.
-spec split_path(binary()) -> [binary()].
split_path(<<"/", Rest/binary>>) ->
    split_path(Rest);
split_path(Path) ->
    binary:split(Path, <<"/">>, [global, trim_all]).

%%====================================================================
%% Helpers
%%====================================================================

%% @doc Extract named bindings from a matched path.
%%
%% Given `Pattern = <<"/items/{id}/tags/{tag}">>` and
%% `Path = <<"/items/42/tags/colour">>`, returns
%% `#{<<"id">> => <<"42">>, <<"tag">> => <<"colour">>}`.
-spec extract_bindings(path_pattern(), binary()) -> bindings().
extract_bindings(Pattern, Path) ->
    case match_path(Pattern, Path) of
        {ok, Bindings} -> Bindings;
        nomatch        -> #{}
    end.
