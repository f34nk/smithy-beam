defp prefix_headers_from_list(
  headers,
  prefix
) do
  headers
  |> Enum.filter(fn {name, _} -> String.starts_with?(name, prefix) end)
  |> Map.new(fn {name, val} -> {String.slice(name, byte_size(prefix)..-1//1), val} end)
  |> case do
    map when map == %{} -> nil
    map -> map
  end
end