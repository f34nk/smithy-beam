defp prefix_headers_to_list(_prefix, nil), do: []
defp prefix_headers_to_list(prefix, map) when is_map(map) do
  Enum.map(map, fn {k, v} ->
  {prefix <> k, Kernel.to_string(v)}
end)
end