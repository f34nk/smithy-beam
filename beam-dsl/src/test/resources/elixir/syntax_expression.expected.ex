[value]
|> Enum.filter_map(fn
  v when v != nil -> {"key", Codec.encode_value(v)}
  _ -> :pop
end)
