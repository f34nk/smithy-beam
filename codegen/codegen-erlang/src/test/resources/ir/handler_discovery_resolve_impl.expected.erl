resolve_impl(Impl) ->
    case code:ensure_loaded(Impl) of
        {module, Impl} ->
            Callbacks = basic_service_behaviour:behaviour_info(callbacks),
            Handlers = maps:from_list([{Fun, make_handler(Impl, Fun)} || {Fun, 3} <- Callbacks, erlang:function_exported(Impl, Fun, 3)]),
            {ok, Handlers};
        {error, _} ->
            {error, {impl_not_loaded, Impl}}
    end.
