decode_timestamp_epoch_seconds(null) -> undefined;
decode_timestamp_epoch_seconds(undefined) -> undefined;
decode_timestamp_epoch_seconds(V) when is_number(V) ->
    Mega = V div 1000000,
    Secs = V rem 1000000,
    {Mega, Secs, 0}.