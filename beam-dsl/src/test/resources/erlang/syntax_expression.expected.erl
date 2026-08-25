lists:filtermap(
    fun
        (V) when V =/= undefined -> {true, {<<"key">>, codec:encode_value(V)}};
        (_) -> false
    end,
    [Value]
)
