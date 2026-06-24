encode_timestamp_date_time({Mega, Secs, _Micro}) ->
    EpochSecs = Mega * 1000000 + Secs,
    {{Y, Mo, D}, {H, Mi, S}} = calendar:gregorian_seconds_to_datetime(EpochSecs + 62167219200),
    iolist_to_binary(io_lib:format("~4..0B-~2..0B-~2..0BT~2..0B:~2..0B:~2..0BZ", [Y, Mo, D, H, Mi, S]));
encode_timestamp_date_time(undefined) -> undefined.