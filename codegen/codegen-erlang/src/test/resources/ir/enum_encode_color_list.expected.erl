encode_color_list(undefined) -> undefined;
encode_color_list(List) when is_list(List) -> [encode_color(V) || V <- List, V =/= undefined].
