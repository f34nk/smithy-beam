defp decode_basic_item(nil), do: :nil
defp decode_basic_item(map) when is_map(map) do
  %Types.BasicItem{
    name: Map.get(map, "name"),
    count: Map.get(map, "count")
  }
end

defp encode_basic_item(nil), do: :nil
defp encode_basic_item(record = %Types.BasicItem{}) do
  _map =
    %{
      "name" => record.name,
      "count" => record.count
    }
    |> Enum.reject(fn {_k, v} -> is_nil(v) end)
    |> Map.new()
end