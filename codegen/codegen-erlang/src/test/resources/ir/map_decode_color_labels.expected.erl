decode_color_labels(undefined) -> undefined;
decode_color_labels(null) -> undefined;
decode_color_labels(Map) when is_map(Map) ->
    maps:from_list([{decode_color(K), V} || {K, V} <- maps:to_list(Map)]).
