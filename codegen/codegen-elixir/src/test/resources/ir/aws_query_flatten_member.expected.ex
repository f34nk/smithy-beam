defp flatten_member(_key, nil), do: []
defp flatten_member(key, value) when is_list(value) do
  List.flatten(for {v, i} <- Enum.with_index(value, 1), v != :nil do flatten_member(key <> ".member." <> Integer.to_string(i), v) end)
end
defp flatten_member(key, value) when is_struct(value) do
  flatten_structure(key, value)
end
defp flatten_member(key, value) when is_map(value) do
  List.flatten(
    for {{k, v}, i} <-
      Enum.with_index(Map.to_list(value), 1),
      k != :nil,
      v != :nil do
      flatten_member(key <> ".entry." <> Integer.to_string(i) <> ".key", k) ++ flatten_member(key <> ".entry." <> Integer.to_string(i) <> ".value", v)
    end
  )
end
defp flatten_member(key, value), do: [{key, value}]

defp enc(value) when is_boolean(value), do: Atom.to_string(value)
defp enc(value) when is_integer(value), do: Integer.to_string(value)
defp enc(value) when is_float(value), do: Float.to_string(value)
defp enc(value) when is_binary(value), do: value
defp enc(value) when is_atom(value), do: Atom.to_string(value)
