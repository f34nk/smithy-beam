encode_timestamp_epoch_seconds({Mega, Secs, _Micro}) -> Mega * 1000000 + Secs;
encode_timestamp_epoch_seconds(undefined) -> undefined.
