defp decode_large_status(nil), do: :nil
defp decode_large_status(v) when is_binary(v), do: Types.LargeStatus.from_string(v)

defp encode_large_status(nil), do: :nil
defp encode_large_status(v), do: Types.LargeStatus.to_string(v)
