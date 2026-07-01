defp decode_basic_string("FOO"), do: :foo
defp decode_basic_string("BAR"), do: :bar
defp decode_basic_string(v) when is_binary(v) do
  normalized = String.replace(v, "_", ".")
  if normalized == v do
    {:unknown, v}
  else
    decode_basic_string(normalized)
  end
end
defp decode_basic_string(nil), do: :nil

defp encode_basic_string(:foo), do: "FOO"
defp encode_basic_string(:bar), do: "BAR"
defp encode_basic_string({:unknown, v}) when is_binary(v), do: v
defp encode_basic_string(nil), do: :nil
