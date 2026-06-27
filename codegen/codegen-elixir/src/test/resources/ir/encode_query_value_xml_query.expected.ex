defp encode_query_value(v) when is_integer(v), do: Integer.to_string(v)
defp encode_query_value(v) when is_float(v), do: :erlang.float_to_binary(v, [:short])
defp encode_query_value(v) when is_boolean(v), do: Atom.to_string(v)
defp encode_query_value(v), do: to_binary(v)