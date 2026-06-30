defp decode_timestamp_date_time(nil), do: :nil
defp decode_timestamp_date_time(v) when is_number(v), do: DateTime.from_unix!(Kernel.trunc(v))
defp decode_timestamp_date_time(v) when is_binary(v) do
  case DateTime.from_iso8601(v) do
    {:ok, dt, _} -> dt
    _ -> :nil
  end
end