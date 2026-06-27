encode_sparse_list(undefined) ->
    null;
encode_sparse_list(List) when is_list(List) ->
    [
        case V of
            undefined -> null;
            _ -> V
        end
     || V <- List
    ].
