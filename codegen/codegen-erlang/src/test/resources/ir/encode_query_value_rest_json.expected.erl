encode_query_value(V) when is_boolean(V) -> atom_to_binary(V, utf8);
encode_query_value(V) when is_integer(V) -> integer_to_binary(V);
encode_query_value(V) when is_float(V) -> float_to_binary(V);
encode_query_value(V) when is_binary(V) -> V;
encode_query_value(V) when is_atom(V) -> atom_to_binary(V, utf8).
