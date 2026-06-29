defp parse_query_params(body) do
  body
  |> URI.decode_query()
  |> Map.new()
end

defp form_value(params, key), do: Map.get(params, key)

defp form_list_values_aws(params, key) do
  prefix = <<key, ".member.">>
  indexed_form_values(params, prefix)
end

defp indexed_form_values(params, prefix) do
  values =
    params
    |> Enum.filter(fn {k, _} -> String.starts_with?(k, prefix) end)
    |> Enum.sort_by(fn {k, _} -> String.to_integer(String.replace_prefix(k, prefix, "")) end)
    |> Enum.map(fn {_, v} -> v end)
  case values do
    [] -> nil
    values -> values
  end
end
