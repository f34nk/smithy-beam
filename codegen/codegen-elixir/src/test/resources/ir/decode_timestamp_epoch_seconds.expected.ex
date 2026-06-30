defp decode_timestamp_epoch_seconds(nil), do: :nil
defp decode_timestamp_epoch_seconds(v) when is_number(v), do: DateTime.from_unix!(Kernel.trunc(v))