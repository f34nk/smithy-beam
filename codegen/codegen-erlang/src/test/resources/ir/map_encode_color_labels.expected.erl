encode_color_labels(undefined) -> undefined;
encode_color_labels(Map) when is_map(Map) ->
    maps:from_list([{encode_color(K), V} || {K, V} <- maps:to_list(Map)]).
