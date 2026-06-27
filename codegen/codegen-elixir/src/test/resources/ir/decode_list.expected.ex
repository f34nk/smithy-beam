defp decode_list(nil), do: :nil
defp decode_list(list) when is_list(list), do: Enum.reject(list, & is_nil/1)