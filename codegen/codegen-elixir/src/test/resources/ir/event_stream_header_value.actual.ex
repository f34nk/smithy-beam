defp header_value(
  headers,
  name
) do
  Enum.find_value(headers, fn {key, value} -> if key == name, do: value end)
end