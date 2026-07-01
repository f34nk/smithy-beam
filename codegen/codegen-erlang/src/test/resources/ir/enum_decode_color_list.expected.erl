decode_color_list(undefined) -> undefined;
decode_color_list(null) -> undefined;
decode_color_list(List) when is_list(List) -> [decode_color(V) || V <- List, V =/= null].
