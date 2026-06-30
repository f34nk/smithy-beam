defp encode_sparse_list(nil), do: :nil
defp encode_sparse_list(list) when is_list(list) do
  Enum.map(
    list,
    fn
      nil ->
        :nil;
      v ->
        v
    end
  )
end
