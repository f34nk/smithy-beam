dispatch_handler(Fun, Ctx, Input, Meta) ->
    Handlers = persistent_term:get(?HANDLERS_KEY, #{}),
    case maps:get(Fun, Handlers, undefined) of
        Handler when is_function(Handler, 3) ->
            Handler(Ctx, Input, Meta);
        _ ->
            {error, not_implemented}
    end.
