defp to_binary(v) when is_binary(v), do: v
defp to_binary(v) when is_list(v), do: IO.iodata_to_binary(v)
defp to_binary(v) when is_atom(v), do: Atom.to_string(v)
defp to_binary(v) when is_integer(v), do: Integer.to_string(v)
defp to_binary(v) when is_float(v), do: :erlang.float_to_binary(v, [:short])
defp to_binary(v) when is_boolean(v), do: Atom.to_string(v)