decode_sparse_map(undefined) ->
    undefined;
decode_sparse_map(Map) when is_map(Map) ->
    maps:map(
        fun
            (_K, null) ->
                undefined;
            (_K, V) ->
                V
        end,
        Map
    ).
