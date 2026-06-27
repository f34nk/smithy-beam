defp encode_query_value(v) when is_boolean(v), do: Atom.to_string(v)
defp encode_query_value(v) when is_integer(v), do: Integer.to_string(v)
defp encode_query_value(v) when is_float(v), do: Float.to_string(v)
defp encode_query_value(v) when is_binary(v), do: v
defp encode_query_value(v) when is_atom(v), do: Atom.to_string(v)