defp encode_timestamp_epoch_seconds(dt = %DateTime{}), do: DateTime.to_unix(dt)
defp encode_timestamp_epoch_seconds(nil), do: :nil