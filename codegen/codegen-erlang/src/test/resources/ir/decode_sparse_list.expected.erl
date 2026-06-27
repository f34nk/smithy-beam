decode_sparse_list(undefined) ->
    undefined;
decode_sparse_list(null) ->
    undefined;
decode_sparse_list(List) when is_list(List) ->
    [
        case V of
            null -> undefined;
            _ -> V
        end
     || V <- List
    ].
