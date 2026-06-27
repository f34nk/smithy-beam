lists:filtermap(
    fun
        (V) when V =/= undefined -> {true, {<<"verbose">>, encode_query_value(V)}};
        (_) -> false
    end,
    [Verbose]
)
