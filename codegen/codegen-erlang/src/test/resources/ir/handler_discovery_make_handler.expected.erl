make_handler(Impl, Fun) ->
    fun(Ctx, Input, Meta) -> Impl:Fun(Ctx, Input, Meta) end.
