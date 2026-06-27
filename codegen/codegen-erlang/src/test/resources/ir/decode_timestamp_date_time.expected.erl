decode_timestamp_date_time(null) -> undefined;
decode_timestamp_date_time(undefined) -> undefined;
decode_timestamp_date_time(V) when is_number(V) ->
    EpochSecs = trunc(V),
    Mega = EpochSecs div 1000000,
    {Mega, EpochSecs rem 1000000, 0};
decode_timestamp_date_time(V) when is_binary(V) ->
    try
        <<Y:4/binary, "-", Mo:2/binary, "-", D:2/binary, "T", H:2/binary, ":", Mi:2/binary, ":", S:2/binary, _/binary>> = V,
        Dt = {{binary_to_integer(Y), binary_to_integer(Mo), binary_to_integer(D)}, {binary_to_integer(H), binary_to_integer(Mi), binary_to_integer(S)}},
        GregorianSecs = calendar:datetime_to_gregorian_seconds(Dt),
        EpochSecs = GregorianSecs - 62167219200,
        Mega = EpochSecs div 1000000,
        {Mega, EpochSecs rem 1000000, 0}
    catch
        _:_ -> undefined
    end.