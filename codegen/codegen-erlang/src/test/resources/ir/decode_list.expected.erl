decode_list(undefined) -> undefined;
decode_list(null) -> undefined;
decode_list(List) when is_list(List) -> [V || V <- List, V =/= null].
