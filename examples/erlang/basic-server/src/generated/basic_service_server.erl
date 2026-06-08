%% Generated Erlang server dispatcher for smithy.beam.demo.basic#BasicService.
%% Discovers impl callbacks at startup via init_handlers/0.
-module(basic_service_server).
-behaviour(basic_service_behaviour).
-export([init_handlers/0, handle_get_type_closure/3, handle_list_basic_items/3]).
-include("basic_service_types.hrl").

%% Handler discovery and dispatch helpers.

handle_get_type_closure(Ctx, Input, Meta) ->
    dispatch_handler(handle_get_type_closure, Ctx, Input, Meta).

handle_list_basic_items(Ctx, Input, Meta) ->
    dispatch_handler(handle_list_basic_items, Ctx, Input, Meta).

-define(DEFAULT_IMPL, basic_service_impl).
-define(HANDLERS_KEY, {basic_service_server, handlers}).

resolve_impl(Impl) ->
    case code:ensure_loaded(Impl) of
        {module, Impl} ->
            Callbacks = basic_service_behaviour:behaviour_info(callbacks),
            Handlers = maps:from_list([
                {Fun, make_handler(Impl, Fun)}
                || {Fun, 3} <- Callbacks,
                   erlang:function_exported(Impl, Fun, 3)
            ]),
            {ok, Handlers};
        {error, _} ->
            {error, {impl_not_loaded, Impl}}
        end.

make_handler(Impl, Fun) ->
    fun(Ctx, Input, Meta) -> Impl:Fun(Ctx, Input, Meta) end.

-spec init_handlers() -> ok | {error, term()}.
init_handlers() ->
    case resolve_impl(?DEFAULT_IMPL) of
        {ok, Handlers} ->
            persistent_term:put(?HANDLERS_KEY, Handlers),
            ok;
        {error, Reason} ->
            persistent_term:put(?HANDLERS_KEY, #{}),
            {error, Reason}
        end.

dispatch_handler(Fun, Ctx, Input, Meta) ->
    Handlers = persistent_term:get(?HANDLERS_KEY, #{}),
    case maps:get(Fun, Handlers, undefined) of
        Handler when is_function(Handler, 3) ->
            Handler(Ctx, Input, Meta);
        _ ->
            {error, not_implemented}
        end.

%% Call basic_service_server:init_handlers/0 during application start before dispatch.
%% Default impl module: basic_service_impl.
