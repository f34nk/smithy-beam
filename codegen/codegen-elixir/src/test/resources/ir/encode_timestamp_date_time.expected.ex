defp encode_timestamp_date_time(dt = %DateTime{}), do: DateTime.to_iso8601(dt)
defp encode_timestamp_date_time(nil), do: :nil