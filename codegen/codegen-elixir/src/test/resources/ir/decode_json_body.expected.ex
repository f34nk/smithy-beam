defp decode_json_body(""), do: %{}
defp decode_json_body(body) do
  case Jason.decode(body) do
    {:ok, map} when is_map(map) -> map
    _ -> %{}
  end
end