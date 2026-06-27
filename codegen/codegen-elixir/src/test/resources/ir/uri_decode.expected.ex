defp uri_decode(nil), do: :nil
defp uri_decode(value), do: URI.decode(value)