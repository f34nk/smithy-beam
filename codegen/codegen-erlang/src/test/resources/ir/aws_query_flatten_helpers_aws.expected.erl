flatten_member(_Key, undefined) ->
    [];
flatten_member(Key, Value) when is_list(Value) ->
    lists:append([
        flatten_member(<<Key/binary, ".member.", (integer_to_binary(I))/binary>>, V)
     || {I, V} <- lists:enumerate(Value),
        V =/= undefined
    ]);
flatten_member(Key, Value) when is_map(Value) ->
    lists:append([
        (flatten_member(<<Key/binary, ".entry.", (integer_to_binary(I))/binary, ".key">>, K) ++ flatten_member(
            <<Key/binary, ".entry.", (integer_to_binary(I))/binary, ".value">>,
            V
        ))
     || {I, {K, V}} <- lists:enumerate(maps:to_list(Value)),
        K =/= undefined,
        V =/= undefined
    ]);
flatten_member(Key, Value) when is_tuple(Value) ->
    flatten_structure(Key, Value);
flatten_member(Key, Value) ->
    [{Key, Value}].


enc(V) when is_boolean(V) -> atom_to_binary(V, utf8);
enc(V) when is_integer(V) -> integer_to_binary(V);
enc(V) when is_float(V) -> float_to_binary(V);
enc(V) when is_binary(V) -> V;
enc(V) when is_atom(V) -> atom_to_binary(V, utf8).
