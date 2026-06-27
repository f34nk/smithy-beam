encode_sparse_map(undefined) -> null;
encode_sparse_map(Map) when is_map(Map) -> maps:map(fun
    (_K, undefined) ->
        null;
    (_K, V) ->
        V
end, Map).