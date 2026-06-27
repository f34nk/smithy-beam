decode_basic_item_list(undefined) -> undefined;
decode_basic_item_list(null) -> undefined;
decode_basic_item_list(List) when is_list(List) -> [decode_basic_item(V) || V <- List, V =/= null].

encode_basic_item_list(undefined) -> undefined;
encode_basic_item_list(List) when is_list(List) -> [encode_basic_item(V) || V <- List, V =/= undefined].
