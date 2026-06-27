defp decode_basic_item_list(nil), do: :nil
defp decode_basic_item_list(list) when is_list(list), do: Enum.map(list, fn v -> decode_basic_item(v) end)

defp encode_basic_item_list(nil), do: :nil
defp encode_basic_item_list(list) when is_list(list), do: Enum.map(list, fn v -> encode_basic_item(v) end)