-module(smithy_pagination).

%% Lazy pagination helper for generated Smithy clients.
%%
%% The generated client supplies setter/getter closures via the Opts map so the
%% runtime stays independent of the user's record shape and never touches
%% record_info/2 at compile time.
%%
%% Required Opts entries:
%%   set_input_token :: fun((Input, Token) -> Input)
%%       Installs the next page token on the input record/map.
%%   output_token    :: fun((Output) -> Token | undefined)
%%       Extracts the next page token from a successful output. Returning
%%       `undefined' (or the empty binary) terminates the stream.
%%
%% Optional Opts entries (stream/3 only):
%%   items :: fun((Output) -> [Item] | Item | undefined)
%%       Unwraps the per-page items field. Defaults to "no items".

-export([
    stream/3,
    pages/3
]).

-export_type([
    request_fun/0,
    cont/1
]).

-type request_fun() :: fun((term()) -> {ok, term()} | {error, term()}).
-type cont(T) :: undefined | fun(() -> T).

%% @doc Returns a continuation function that, when invoked, yields the next
%% batch of items together with a continuation for the page after that.
%%
%% Each invocation returns one of:
%%   {[Item], Cont}   — items from the current page; Cont is `undefined' when
%%                      the previous page had no `output_token', otherwise it
%%                      is a 0-arity fun yielding the next batch.
%%   {error, Reason}  — propagated from the underlying request fun.
-spec stream(request_fun(), term(), map()) -> cont({list(), cont(term())}).
stream(RequestFun, Input, Opts)
        when is_function(RequestFun, 1), is_map(Opts) ->
    fun() -> step_stream(RequestFun, Input, Opts) end.

%% @doc Returns a continuation function that, when invoked, yields one full
%% page (the entire output struct) together with a continuation for the next
%% page.
%%
%% Each invocation returns one of:
%%   {ok, Output, Cont} — Cont is `undefined' on the last page.
%%   {error, Reason}    — propagated from the underlying request fun.
-spec pages(request_fun(), term(), map()) -> cont({ok, term(), cont(term())} | {error, term()}).
pages(RequestFun, Input, Opts)
        when is_function(RequestFun, 1), is_map(Opts) ->
    fun() -> step_pages(RequestFun, Input, Opts) end.

%%%===================================================================
%%% Internal
%%%===================================================================

step_stream(RequestFun, Input, Opts) ->
    case RequestFun(Input) of
        {ok, Output} ->
            Items = items_or_empty(maps:get(items, Opts, undefined), Output),
            case (maps:get(output_token, Opts))(Output) of
                undefined ->
                    {Items, undefined};
                <<>> ->
                    {Items, undefined};
                NextToken ->
                    NextInput = (maps:get(set_input_token, Opts))(Input, NextToken),
                    {Items, fun() -> step_stream(RequestFun, NextInput, Opts) end}
            end;
        {error, _} = Err ->
            Err
    end.

step_pages(RequestFun, Input, Opts) ->
    case RequestFun(Input) of
        {ok, Output} ->
            case (maps:get(output_token, Opts))(Output) of
                undefined ->
                    {ok, Output, undefined};
                <<>> ->
                    {ok, Output, undefined};
                NextToken ->
                    NextInput = (maps:get(set_input_token, Opts))(Input, NextToken),
                    {ok, Output, fun() -> step_pages(RequestFun, NextInput, Opts) end}
            end;
        {error, _} = Err ->
            Err
    end.

items_or_empty(undefined, _Output) ->
    [];
items_or_empty(GetItemsFun, Output) ->
    case GetItemsFun(Output) of
        undefined -> [];
        L when is_list(L) -> L;
        Other -> [Other]
    end.
