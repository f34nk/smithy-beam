defp decode_sparse_map(nil), do: :nil
defp decode_sparse_map(map) when is_map(map) do
  Map.new(
    map,
    fn
      {k, nil} ->
        {k, :nil};
      {k, v} ->
        {k, v}
    end
  )
end
