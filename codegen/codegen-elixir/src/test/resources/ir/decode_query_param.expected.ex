defp decode_query_param(nil), do: :nil
defp decode_query_param(true), do: true
defp decode_query_param(false), do: false
defp decode_query_param("true"), do: true
defp decode_query_param("false"), do: false
defp decode_query_param(value), do: value